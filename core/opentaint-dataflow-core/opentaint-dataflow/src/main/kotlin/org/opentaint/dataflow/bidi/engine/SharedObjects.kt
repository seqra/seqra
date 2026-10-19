package org.opentaint.dataflow.bidi.engine

import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.ExecutorCoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.newFixedThreadPoolContext
import org.opentaint.dataflow.ap.ifds.LanguageManager
import org.opentaint.dataflow.bidi.ap.ApManager
import org.opentaint.dataflow.bidi.ap.ApOps
import org.opentaint.dataflow.bidi.interp.FormCacheStatistics
import org.opentaint.dataflow.bidi.interp.Interpreter
import org.opentaint.dataflow.bidi.interp.UnresolvedCallObserver
import org.opentaint.dataflow.bidi.store.RecordStore
import org.opentaint.dataflow.bidi.store.N1Statistics
import org.opentaint.dataflow.bidi.store.VulnerabilityStore
import org.opentaint.dataflow.ifds.UnitResolver
import org.opentaint.dataflow.util.Cancellation
import org.opentaint.dataflow.util.RefManager
import org.opentaint.ir.api.common.CommonMethod
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/** Analysis owners shared across runs; each run creates its own mutable engine state. */
class SharedObjects(
    val ap: ApManager,
    val ops: ApOps,
    val interpreter: Interpreter,
    val language: LanguageManager,
    val contexts: MethodContextCache,
    val records: RecordStore,
    val vulnerabilities: VulnerabilityStore,
    val unitResolver: UnitResolver<CommonMethod>,
    val refManager: RefManager,
    val cancellation: Cancellation,
    threads: Int = (Runtime.getRuntime().availableProcessors() / 2).coerceAtLeast(1),
    val cancellationTimeout: Duration = 30.seconds,
    val diagnostics: Boolean = false,
    val formStatistics: FormCacheStatistics? = null,
    releaseRun1CatalogCoverage: (() -> Unit)? = null,
    val abstractionStatistics: Run1AbstractionStatistics? =
        if (java.lang.Boolean.getBoolean("org.opentaint.bidi.optimizationStatistics")) Run1AbstractionStatistics() else null,
    val n1Statistics: N1Statistics? = if (java.lang.Boolean.getBoolean("org.opentaint.bidi.optimizationStatistics")) N1Statistics() else null,
    val n2Statistics: N2Statistics? = if (java.lang.Boolean.getBoolean("org.opentaint.bidi.optimizationStatistics")) N2Statistics() else null,
    val requestBatchStatistics: RequestBatchStatistics? =
        if (java.lang.Boolean.getBoolean("org.opentaint.bidi.optimizationStatistics")) RequestBatchStatistics() else null,
) : AutoCloseable {
    @OptIn(DelicateCoroutinesApi::class, ExperimentalCoroutinesApi::class)
    val dispatcher: ExecutorCoroutineDispatcher = newFixedThreadPoolContext(threads, "bidi-worker")

    /** Call only after run-1 workers have joined. No static request catalog exists. */
    private var releaseCoverage: (() -> Unit)? = releaseRun1CatalogCoverage
    fun releaseRun1CatalogCoverage() {
        val release = releaseCoverage
        releaseCoverage = null
        release?.invoke()
    }

    val unresolvedObserver: UnresolvedCallObserver? get() = interpreter as? UnresolvedCallObserver

    override fun close() { releaseRun1CatalogCoverage(); dispatcher.close() }
}
