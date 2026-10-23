package com.batch.inventory.batch

import com.batch.inventory.domain.InventoryStatistics
import org.springframework.batch.core.ExitStatus
import org.springframework.batch.core.listener.ItemProcessListener
import org.springframework.batch.core.listener.ItemReadListener
import org.springframework.batch.core.listener.ItemWriteListener
import org.springframework.batch.core.listener.StepExecutionListener
import org.springframework.batch.core.step.StepExecution
import org.springframework.batch.infrastructure.item.Chunk
import org.springframework.stereotype.Component

// 측정용: Reader / Processor / Writer 구간별 누적 시간 (단일 스레드 Step 전제)
@Component
class StepTimingListener :
    ItemReadListener<String>,
    ItemProcessListener<String, InventoryStatistics>,
    ItemWriteListener<InventoryStatistics>,
    StepExecutionListener {

    private var start = 0L
    private var stepStart = 0L
    private var readNs = 0L
    private var processNs = 0L
    private var writeNs = 0L
    private val processTimes = mutableListOf<Long>() // SKU별 Processor 시간(ms), chunk 안 순서대로

    override fun beforeStep(stepExecution: StepExecution) {
        readNs = 0; processNs = 0; writeNs = 0; processTimes.clear()
        stepStart = System.nanoTime()
    }

    override fun beforeRead() { start = System.nanoTime() }
    override fun afterRead(item: String) { readNs += System.nanoTime() - start }

    override fun beforeProcess(item: String) { start = System.nanoTime() }
    override fun afterProcess(item: String, result: InventoryStatistics?) {
        val ns = System.nanoTime() - start
        processNs += ns
        processTimes += ns / 1_000_000
    }

    override fun beforeWrite(items: Chunk<out InventoryStatistics>) { start = System.nanoTime() }
    override fun afterWrite(items: Chunk<out InventoryStatistics>) { writeNs += System.nanoTime() - start }

    override fun afterStep(stepExecution: StepExecution): ExitStatus? {
        val totalMs = (System.nanoTime() - stepStart) / 1_000_000
        val r = readNs / 1_000_000
        val p = processNs / 1_000_000
        val w = writeNs / 1_000_000
        println(
            "[TIMING] items=${stepExecution.readCount} total=${totalMs}ms " +
                "read=${r}ms process=${p}ms write=${w}ms etc(commit 등)=${totalMs - r - p - w}ms"
        )
        println("[TIMING] process per SKU(ms) 1~10번째=${processTimes.take(10)} / 91~100번째=${processTimes.takeLast(10)}")
        return null
    }
}
