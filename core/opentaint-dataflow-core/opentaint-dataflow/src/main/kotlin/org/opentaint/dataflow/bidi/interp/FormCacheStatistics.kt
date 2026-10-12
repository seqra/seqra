package org.opentaint.dataflow.bidi.interp

import java.util.concurrent.atomic.AtomicLong

/** Optional analysis-wide aggregates. Counters never retain forms, slots or builder callbacks. */
class FormCacheStatistics {
    private val accesses = AtomicLong()
    private val hits = AtomicLong()
    private val buildAttempts = AtomicLong()
    private val buildSuccesses = AtomicLong()
    private val buildFailures = AtomicLong()
    private val reversals = AtomicLong()
    private val reversalFailures = AtomicLong()
    private val uncachedBuilds = AtomicLong()
    private val buildNanos = AtomicLong()
    private val reversalNanos = AtomicLong()
    private val activePins = AtomicLong()
    private val peakPins = AtomicLong()
    private val compiledPredicates = AtomicLong()
    private val compiledNodes = AtomicLong()
    private val compiledLeaves = AtomicLong()
    private val observedProofRecords = AtomicLong()
    private val observedSupportShapes = AtomicLong()
    private val supportGrowthUpdates = AtomicLong()
    private val newRootOutcomes = AtomicLong()
    private val rootGrowthOutcomes = AtomicLong()
    private val rootOutcomes = AtomicLong()

    internal fun access() { accesses.incrementAndGet() }
    internal fun hit() { hits.incrementAndGet() }
    internal fun startBuild(): Long { buildAttempts.incrementAndGet(); return System.nanoTime() }
    internal fun finishBuild(start: Long, success: Boolean, cached: Boolean) {
        buildNanos.addAndGet((System.nanoTime() - start).coerceAtLeast(0))
        if (success) {
            buildSuccesses.incrementAndGet()
            if (!cached) uncachedBuilds.incrementAndGet()
        } else buildFailures.incrementAndGet()
    }
    internal fun startReversal(): Long { reversals.incrementAndGet(); return System.nanoTime() }
    internal fun finishReversal(start: Long, success: Boolean) {
        reversalNanos.addAndGet((System.nanoTime() - start).coerceAtLeast(0))
        if (!success) reversalFailures.incrementAndGet()
    }
    internal fun pin() {
        val current = activePins.incrementAndGet()
        peakPins.accumulateAndGet(current, ::maxOf)
    }
    internal fun unpin() { activePins.decrementAndGet() }

    /** Compilation totals include rebuilds and constants; they are not a live payload census. */
    fun compiledPredicate(nodes: Int, leaves: Int) {
        compiledPredicates.incrementAndGet()
        compiledNodes.addAndGet(nodes.toLong())
        compiledLeaves.addAndGet(leaves.toLong())
    }

    /** Runtime totals across actor stores and runs, separate from compiled potential structure.
     *  A proof record is newly interned; a support shape is first stored at a logical node.
     *  Growth updates an existing stored shape. Root counts follow canonical deduplicated outcomes.
     *  These totals do not fall when stores freeze and are not a live or peak retained-object census. */
    internal fun observedProof() { observedProofRecords.incrementAndGet() }
    internal fun observedSupport() { observedSupportShapes.incrementAndGet() }
    internal fun supportGrowth() { supportGrowthUpdates.incrementAndGet() }
    internal fun rootOutcome(growth: Boolean) {
        if (growth) rootGrowthOutcomes.incrementAndGet() else newRootOutcomes.incrementAndGet()
        rootOutcomes.incrementAndGet()
    }

    data class Snapshot(
        val accesses: Long, val hits: Long,
        val buildAttempts: Long, val buildSuccesses: Long, val buildFailures: Long,
        val reversals: Long, val reversalFailures: Long, val uncachedBuilds: Long,
        val buildNanos: Long, val reversalNanos: Long, val activePins: Long, val peakPins: Long,
        val compiledPredicates: Long, val compiledNodes: Long, val compiledLeaves: Long,
        val observedProofRecords: Long, val observedSupportShapes: Long, val supportGrowthUpdates: Long,
        val newRootOutcomes: Long, val rootGrowthOutcomes: Long, val rootOutcomes: Long,
    )

    fun snapshot(): Snapshot = Snapshot(accesses.get(), hits.get(), buildAttempts.get(), buildSuccesses.get(),
        buildFailures.get(), reversals.get(), reversalFailures.get(), uncachedBuilds.get(), buildNanos.get(),
        reversalNanos.get(), activePins.get(), peakPins.get(), compiledPredicates.get(), compiledNodes.get(),
        compiledLeaves.get(), observedProofRecords.get(), observedSupportShapes.get(), supportGrowthUpdates.get(),
        newRootOutcomes.get(), rootGrowthOutcomes.get(), rootOutcomes.get())

    companion object {
        const val PROPERTY = "org.opentaint.bidi.formStatistics"
        fun ifEnabled(): FormCacheStatistics? = if (java.lang.Boolean.getBoolean(PROPERTY)) FormCacheStatistics() else null
    }
}
