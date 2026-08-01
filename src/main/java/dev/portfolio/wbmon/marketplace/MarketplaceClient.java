package dev.portfolio.wbmon.marketplace;

import dev.portfolio.wbmon.domain.Marketplace;

public interface MarketplaceClient {

    /** Маркетплейс, который обслуживает эта реализация; по нему Poller маршрутизирует SKU. */
    Marketplace marketplace();

    /**
     * @throws MarketplaceFetchException если данные получить не удалось
     */
    ProductSnapshot fetch(String skuCode);
}
