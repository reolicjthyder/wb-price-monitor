package dev.portfolio.wbmon.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SkuRepository extends JpaRepository<Sku, Long> {

    List<Sku> findByChatId(Long chatId);

    Optional<Sku> findByChatIdAndMarketplaceAndSkuCode(Long chatId, Marketplace marketplace, String skuCode);
}
