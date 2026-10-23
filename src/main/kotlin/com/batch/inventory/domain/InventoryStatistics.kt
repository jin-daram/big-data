package com.batch.inventory.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime

@Entity
@Table(name = "year_warehouse_inventory_statistics")
class InventoryStatistics(
    @Column(nullable = false, length = 100)
    val sku: String,

    @Column(nullable = false)
    val baseDate: LocalDate,

    @Column(nullable = false, precision = 12, scale = 2)
    val avgQuantity: BigDecimal,

    @Column(nullable = false)
    val minQuantity: Int,

    @Column(nullable = false)
    val maxQuantity: Int,

    @Column(nullable = false)
    val recordCount: Long,

    @Column(nullable = false)
    val createdAt: LocalDateTime = LocalDateTime.now(),

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val seq: Long? = null,
)
