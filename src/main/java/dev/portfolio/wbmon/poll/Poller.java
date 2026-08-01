package dev.portfolio.wbmon.poll;

import dev.portfolio.wbmon.alert.AlertEngine;
import dev.portfolio.wbmon.alert.AlertNotifier;
import dev.portfolio.wbmon.domain.BotConfig;
import dev.portfolio.wbmon.domain.BotConfigService;
import dev.portfolio.wbmon.domain.Marketplace;
import dev.portfolio.wbmon.domain.PriceSnapshot;
import dev.portfolio.wbmon.domain.PriceSnapshotRepository;
import dev.portfolio.wbmon.domain.Sku;
import dev.portfolio.wbmon.domain.SkuRepository;
import dev.portfolio.wbmon.marketplace.MarketplaceClient;
import dev.portfolio.wbmon.marketplace.MarketplaceFetchException;
import dev.portfolio.wbmon.marketplace.ProductSnapshot;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Тикает раз в минуту; реально опрашивает маркетплейсы, только когда с прошлого опроса
 * прошло bot_config.poll_interval_min минут. Так интервал меняется на лету командой /interval
 * без перепланирования задачи.
 */
@Component
public class Poller {

    private static final Logger log = LoggerFactory.getLogger(Poller.class);
    private static final int FAILURE_ALERT_THRESHOLD = 5;

    private final SkuRepository skuRepository;
    private final PriceSnapshotRepository snapshotRepository;
    private final BotConfigService botConfigService;
    private final AlertEngine alertEngine;
    private final AlertNotifier alertNotifier;
    private final Map<Marketplace, MarketplaceClient> clients;
    private final Clock clock;
    private final long ownerChatId;

    private final Map<Long, Integer> consecutiveFailures = new ConcurrentHashMap<>();
    private volatile Instant lastPollAt = Instant.EPOCH;

    public Poller(SkuRepository skuRepository,
                  PriceSnapshotRepository snapshotRepository,
                  BotConfigService botConfigService,
                  AlertEngine alertEngine,
                  AlertNotifier alertNotifier,
                  List<MarketplaceClient> marketplaceClients,
                  Clock clock,
                  @Value("${wbmon.owner-chat-id}") long ownerChatId) {
        this.skuRepository = skuRepository;
        this.snapshotRepository = snapshotRepository;
        this.botConfigService = botConfigService;
        this.alertEngine = alertEngine;
        this.alertNotifier = alertNotifier;
        this.clients = marketplaceClients.stream()
                .collect(Collectors.toMap(MarketplaceClient::marketplace, Function.identity()));
        this.clock = clock;
        this.ownerChatId = ownerChatId;
    }

    @Scheduled(fixedDelayString = "${wbmon.poll.tick-ms}")
    public void tick() {
        BotConfig config = botConfigService.getOrCreate(ownerChatId);
        Instant now = clock.instant();
        if (Duration.between(lastPollAt, now).toMinutes() < config.getPollIntervalMin()) {
            return;
        }
        lastPollAt = now;

        List<Sku> skus = skuRepository.findByChatId(ownerChatId);
        log.info("Polling {} sku(s)", skus.size());
        for (Sku sku : skus) {
            try {
                pollOne(sku, config, now);
            } catch (RuntimeException e) {
                // страховка: ни одна ошибка по отдельному SKU не должна прерывать цикл
                log.error("Unexpected error while polling {} {}", sku.getMarketplace(), sku.getSkuCode(), e);
            }
        }
    }

    private void pollOne(Sku sku, BotConfig config, Instant now) {
        MarketplaceClient client = clients.get(sku.getMarketplace());
        if (client == null) {
            log.error("No MarketplaceClient registered for {} (sku {})", sku.getMarketplace(), sku.getSkuCode());
            return;
        }

        ProductSnapshot fetched;
        try {
            fetched = client.fetch(sku.getSkuCode());
        } catch (MarketplaceFetchException e) {
            registerFailure(sku, e);
            return;
        }
        consecutiveFailures.remove(sku.getId());

        PriceSnapshot previous = snapshotRepository
                .findTop1BySkuIdOrderByCheckedAtDescIdDesc(sku.getId())
                .orElse(null);
        PriceSnapshot current = snapshotRepository.save(
                new PriceSnapshot(sku.getId(), fetched.price(), fetched.quantity(), now));

        alertEngine.evaluate(sku, previous, current, config)
                .ifPresent(text -> alertNotifier.send(sku.getChatId(), text));
    }

    private void registerFailure(Sku sku, MarketplaceFetchException e) {
        int failures = consecutiveFailures.merge(sku.getId(), 1, Integer::sum);
        log.error("Fetch failed for {} {} (consecutive failures: {})",
                sku.getMarketplace(), sku.getSkuCode(), failures, e);
        if (failures == FAILURE_ALERT_THRESHOLD) {
            // ровно один раз при переходе в состояние "стабильно недоступен"
            alertNotifier.send(sku.getChatId(),
                    "⛔ Не могу получить данные по %s %s уже %d попыток подряд. Последняя ошибка: %s"
                            .formatted(sku.getMarketplace(), sku.getSkuCode(), failures, e.getMessage()));
        }
    }
}
