package com.batch.inventory.batch

import com.batch.inventory.domain.InventoryStatistics
import jakarta.persistence.EntityManagerFactory
import org.springframework.batch.infrastructure.item.database.JpaItemWriter
import org.springframework.batch.infrastructure.item.database.builder.JpaItemWriterBuilder
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class InventoryItemWriter(private val entityManagerFactory: EntityManagerFactory) {

    // 항상 신규 행이라 merge 대신 persist (불필요한 select 없음)
    @Bean
    fun inventoryStatisticsWriter(): JpaItemWriter<InventoryStatistics> =
        JpaItemWriterBuilder<InventoryStatistics>()
            .entityManagerFactory(entityManagerFactory)
            .usePersist(true)
            .build()
}
