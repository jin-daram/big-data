package com.batch.inventory.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.math.BigDecimal
import java.time.LocalDateTime

@Entity
@Table(name = "warehouse_inventory")
class Inventory(

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val seq: Long? = null,

    @Column(nullable = false, length = 100)
    val sku: String,

    @Column(length = 255)
    val productName: String? = null,

    @Column(length = 20)
    val speciesCode: String? = null,

    @Column(length = 20)
    val cutCode: String? = null,

    @Column(length = 20)
    val originCode: String? = null,

    val baseDate: LocalDateTime? = null,

    @Column(nullable = false)
    val quantity: Int = 0,

    @Column(precision = 12, scale = 3)
    val weight: BigDecimal? = null,

    @Column(length = 20)
    val unit: String? = null,

    @Column(nullable = false)
    val updatedAt: LocalDateTime = LocalDateTime.now()

)
