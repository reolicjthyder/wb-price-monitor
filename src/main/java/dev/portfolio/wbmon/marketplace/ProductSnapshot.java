package dev.portfolio.wbmon.marketplace;

import java.math.BigDecimal;

/** Мгновенное состояние товара на маркетплейсе: цена в рублях и доступный остаток. */
public record ProductSnapshot(String skuCode, BigDecimal price, int quantity) {
}
