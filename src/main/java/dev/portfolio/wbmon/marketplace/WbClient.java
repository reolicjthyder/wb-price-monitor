package dev.portfolio.wbmon.marketplace;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.portfolio.wbmon.domain.Marketplace;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;

/**
 * Клиент внутреннего API карточек Wildberries.
 * Формат ответа (проверен на живом API 2026-08-01): products[0].totalQuantity
 * и products[0].sizes[0].price.product (в копейках). Обёртки "data" НЕТ.
 * Версии /cards/v0..v3 и v5 отвечают 404 — актуальна только v4.
 */
@Component
public class WbClient implements MarketplaceClient {

    private static final Logger log = LoggerFactory.getLogger(WbClient.class);
    private static final String DETAIL_PATH = "/cards/v4/detail";
    private static final int MAX_LOGGED_BODY_CHARS = 500;

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final int maxRetries;
    private final long backoffInitialMs;
    private final long backoffMultiplier;

    public WbClient(@Value("${wbmon.wb.base-url}") String baseUrl,
                    @Value("${wbmon.wb.connect-timeout-ms}") int connectTimeoutMs,
                    @Value("${wbmon.wb.read-timeout-ms}") int readTimeoutMs,
                    @Value("${wbmon.wb.max-retries}") int maxRetries,
                    @Value("${wbmon.wb.backoff-initial-ms}") long backoffInitialMs,
                    @Value("${wbmon.wb.backoff-multiplier}") long backoffMultiplier,
                    ObjectMapper objectMapper) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofMillis(connectTimeoutMs));
        factory.setReadTimeout(Duration.ofMillis(readTimeoutMs));
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(factory)
                .build();
        this.objectMapper = objectMapper;
        this.maxRetries = maxRetries;
        this.backoffInitialMs = backoffInitialMs;
        this.backoffMultiplier = backoffMultiplier;
    }

    @Override
    public Marketplace marketplace() {
        return Marketplace.WB;
    }

    @Override
    public ProductSnapshot fetch(String skuCode) {
        int totalAttempts = maxRetries + 1;
        RuntimeException lastFailure = null;
        long delayMs = backoffInitialMs;

        for (int attempt = 1; attempt <= totalAttempts; attempt++) {
            try {
                String body = restClient.get()
                        .uri(uriBuilder -> uriBuilder.path(DETAIL_PATH)
                                .queryParam("appType", 1)
                                .queryParam("curr", "rub")
                                .queryParam("dest", -1257786)
                                .queryParam("nm", skuCode)
                                .build())
                        .retrieve()
                        .body(String.class);
                return parse(skuCode, body);
            } catch (UnexpectedResponseException e) {
                throw e; // повторный запрос формат ответа не починит
            } catch (RuntimeException e) {
                lastFailure = e;
                log.warn("WB fetch attempt {}/{} failed for sku {}: {}",
                        attempt, totalAttempts, skuCode, e.toString());
            }
            if (attempt < totalAttempts) {
                sleep(delayMs);
                delayMs *= backoffMultiplier;
            }
        }
        throw new MarketplaceFetchException(
                "WB fetch failed for sku " + skuCode + " after " + totalAttempts + " attempts", lastFailure);
    }

    private ProductSnapshot parse(String skuCode, String body) {
        JsonNode root;
        try {
            root = objectMapper.readTree(body == null ? "" : body);
        } catch (Exception e) {
            log.error("WB returned a non-JSON body for sku {}: {}", skuCode, truncate(body));
            throw new UnexpectedResponseException("WB returned a non-JSON body for sku " + skuCode);
        }

        JsonNode product = root.path("products").path(0);
        JsonNode priceNode = product.path("sizes").path(0).path("price").path("product");
        JsonNode quantityNode = product.path("totalQuantity");

        if (!priceNode.isNumber() || !quantityNode.isNumber()) {
            log.error("WB returned an unexpected payload for sku {}: {}", skuCode, truncate(body));
            throw new UnexpectedResponseException("WB returned an unexpected payload for sku " + skuCode);
        }

        BigDecimal price = BigDecimal.valueOf(priceNode.asLong())
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        return new ProductSnapshot(skuCode, price, quantityNode.asInt());
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new MarketplaceFetchException("Interrupted while backing off", e);
        }
    }

    private static String truncate(String body) {
        if (body == null) {
            return "<null>";
        }
        return body.length() <= MAX_LOGGED_BODY_CHARS
                ? body
                : body.substring(0, MAX_LOGGED_BODY_CHARS) + "...";
    }
}
