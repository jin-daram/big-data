package com.batch.inventory.batch

import com.batch.inventory.domain.InventoryRepository
import com.batch.inventory.domain.InventoryStatistics
import org.springframework.batch.infrastructure.item.ItemProcessor
import org.springframework.stereotype.Component
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate

data class InventoryStat(
    val sku: String,
    val avgQuantity: Double,
    val minQuantity: Int,
    val maxQuantity: Int,
    val count: Long,
)

@Component
class InventoryItemProcessor(
    private val inventoryRepository: InventoryRepository
) : ItemProcessor<String, InventoryStatistics> {

    // ponytail: 오늘 고정, 운영 시 JobParameter로
    private val today = LocalDate.of(2025, 12, 31)

    // 기존: DB 집계 방식 (SKU당 결과 1행만 받음)
    // override fun process(sku: String): InventoryStatistics? {
    //     val stat = inventoryRepository.findYearStat(
    //         sku,
    //         today.minusYears(1).atStartOfDay(),
    //         today.plusDays(1).atStartOfDay(),
    //     ) ?: return null
    //
    //     return InventoryStatistics(
    //         sku = stat.sku,
    //         baseDate = today,
    //         avgQuantity = BigDecimal.valueOf(stat.avgQuantity).setScale(2, RoundingMode.HALF_UP),
    //         minQuantity = stat.minQuantity,
    //         maxQuantity = stat.maxQuantity,
    //         recordCount = stat.count,
    //     )
    // }

    // 실험: 1년치 엔티티 2920건을 메모리에 올려서 Kotlin으로 계산
    // ponytail: 엔티티가 chunk 커밋까지 영속성 컨텍스트에 남음 (chunk 100 × 2920 = 약 29만 개)
    override fun process(sku: String): InventoryStatistics? {
        val rows = inventoryRepository.findYearRows(
            sku,
            today.minusYears(1).atStartOfDay(),
            today.plusDays(1).atStartOfDay(),
        )
        if (rows.isEmpty()) return null

        val quantities = rows.map { it.quantity }
        return InventoryStatistics(
            sku = sku,
            baseDate = today,
            avgQuantity = BigDecimal.valueOf(quantities.average()).setScale(2, RoundingMode.HALF_UP),
            minQuantity = quantities.min(),
            maxQuantity = quantities.max(),
            recordCount = rows.size.toLong(),
        )
    }
}
