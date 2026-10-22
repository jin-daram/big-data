package com.batch.inventory

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class BigInventoryApplication

fun main(args: Array<String>) {
    runApplication<BigInventoryApplication>(*args)
}
