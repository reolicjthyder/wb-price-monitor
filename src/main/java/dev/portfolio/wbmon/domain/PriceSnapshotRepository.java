package dev.portfolio.wbmon.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PriceSnapshotRepository extends JpaRepository<PriceSnapshot, Long> {

    Optional<PriceSnapshot> findTop1BySkuIdOrderByCheckedAtDescIdDesc(Long skuId);
}
