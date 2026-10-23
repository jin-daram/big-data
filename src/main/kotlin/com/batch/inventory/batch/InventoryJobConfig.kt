package com.batch.inventory.batch

import com.batch.inventory.domain.InventoryStatistics
import org.springframework.batch.core.job.Job
import org.springframework.batch.core.job.builder.JobBuilder
import org.springframework.batch.core.job.parameters.RunIdIncrementer
import org.springframework.batch.core.repository.JobRepository
import org.springframework.batch.core.step.Step
import org.springframework.batch.core.step.builder.StepBuilder
import org.springframework.batch.infrastructure.item.database.JpaItemWriter
import org.springframework.batch.infrastructure.item.database.JpaPagingItemReader
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.transaction.PlatformTransactionManager

@Configuration
class InventoryJobConfig(
    private val jobRepository: JobRepository,
    private val transactionManager: PlatformTransactionManager,
) {

    @Bean
    fun inventoryJob(inventoryStep: Step): Job =
        JobBuilder("inventoryJob", jobRepository)
            .incrementer(RunIdIncrementer()) // 실행마다 run.id 증가 → 새 JobInstance로 재실행 가능
            .start(inventoryStep)
            .build()

    @Bean
    fun inventoryStep(
        reader: JpaPagingItemReader<String>,
        processor: InventoryItemProcessor,
        writer: JpaItemWriter<InventoryStatistics>,
        timingListener: StepTimingListener,
    ): Step =
        StepBuilder("inventoryStep", jobRepository)
            .chunk<String, InventoryStatistics>(100)
            .transactionManager(transactionManager)
            .reader(reader)
            .processor(processor)
            .writer(writer)
            .listener(timingListener)
            .build()

}