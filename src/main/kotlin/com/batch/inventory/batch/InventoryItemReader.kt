package com.batch.inventory.batch

import com.batch.inventory.domain.Inventory
import jakarta.persistence.EntityManagerFactory
import org.springframework.batch.infrastructure.item.database.JpaPagingItemReader
import org.springframework.batch.infrastructure.item.database.builder.JpaPagingItemReaderBuilder
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class InventoryItemReader(private val entityManagerFactory: EntityManagerFactory) {

    // ponytail: 1년치(약 2920건)라 페이징 리더로 충분, 대용량이면 JpaCursorItemReader
    @Bean
    fun inventoryReader(): JpaPagingItemReader<Inventory> =
        JpaPagingItemReaderBuilder<Inventory>()
            .name("inventoryReader")
            .entityManagerFactory(entityManagerFactory)
            .queryString("select i from Inventory i order by i.baseDate, i.seq")
            .pageSize(500)
            .build()
}
