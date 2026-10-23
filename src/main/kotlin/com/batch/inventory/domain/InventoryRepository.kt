package com.batch.inventory.domain

import com.batch.inventory.batch.InventoryStat
import org.springframework.data.jpa.repository.JpaRepository
import jakarta.persistence.QueryHint
import org.springframework.data.jpa.repository.Query
import org.springframework.data.jpa.repository.QueryHints
import java.time.LocalDateTime

interface InventoryRepository : JpaRepository<Inventory, Long> {

    // 엔티티 2920건을 올리지 않고 DB에서 바로 집계
    @Query(
        """
        select new com.batch.inventory.batch.InventoryStat(
            i.sku, avg(i.quantity), min(i.quantity), max(i.quantity), count(i)
        )
        from Inventory i
        where i.sku = :sku and i.baseDate >= :from and i.baseDate < :to
        group by i.sku
        """
    )
    fun findYearStat(sku: String, from: LocalDateTime, to: LocalDateTime): InventoryStat?

    // 메모리 계산 실험용: 1년치 엔티티 2920건을 그대로 조회
    // 실험: 쿼리 전 auto flush 끔 (영속성 컨텍스트 전체 순회 방지)
    @QueryHints(QueryHint(name = "org.hibernate.flushMode", value = "COMMIT"))
    @Query(
        """
        select i from Inventory i
        where i.sku = :sku and i.baseDate >= :from and i.baseDate < :to
        """
    )
    fun findYearRows(sku: String, from: LocalDateTime, to: LocalDateTime): List<Inventory>
}
