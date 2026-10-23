package com.batch.inventory.batch

import jakarta.persistence.EntityManagerFactory
import org.springframework.batch.infrastructure.item.database.JpaPagingItemReader
import org.springframework.batch.infrastructure.item.database.builder.JpaPagingItemReaderBuilder
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.LocalDate

@Configuration
class InventoryItemReader(private val entityManagerFactory: EntityManagerFactory) {

    private val dayFrom = LocalDate.of(2025, 12, 31)

    @Bean
    fun inventoryReader(): JpaPagingItemReader<String> =
        JpaPagingItemReaderBuilder<String>()
            .name("skuReader")
            .entityManagerFactory(entityManagerFactory)
            // TODO: sku <= SKU00500 조건은 500개 측정용, 확인 후 삭제
            .queryString("""
                select i.sku from Inventory i
                where i.baseDate >= :dayFrom AND i.baseDate < :dayTo
                and i.sku <= 'SKU00500'
                group by i.sku
                order by i.sku
            """.trimIndent()
            ).parameterValues(mapOf(
                "dayFrom" to dayFrom.atStartOfDay(),
                "dayTo" to dayFrom.plusDays(1).atStartOfDay(),
            ))
            .pageSize(1000)
            .build()
}
