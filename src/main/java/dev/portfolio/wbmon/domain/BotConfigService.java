package dev.portfolio.wbmon.domain;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
public class BotConfigService {

    private final BotConfigRepository repository;

    public BotConfigService(BotConfigRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public BotConfig getOrCreate(long chatId) {
        return repository.findById(chatId)
                .orElseGet(() -> repository.save(new BotConfig(chatId)));
    }

    @Transactional
    public BotConfig updateInterval(long chatId, int minutes) {
        BotConfig config = getOrCreate(chatId);
        config.setPollIntervalMin(minutes);
        return repository.save(config);
    }

    @Transactional
    public BotConfig updateThresholds(long chatId, BigDecimal pricePct, int qtyLow) {
        BotConfig config = getOrCreate(chatId);
        config.setPriceThresholdPct(pricePct);
        config.setQtyLowThreshold(qtyLow);
        return repository.save(config);
    }
}
