package org.opentaint.dataflow.bidi.engine

import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.ExecutorCoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.newFixedThreadPoolContext
import org.opentaint.dataflow.ap.ifds.LanguageManager
import org.opentaint.dataflow.bidi.ap.ApManager
import org.opentaint.dataflow.bidi.ap.ApOps
import org.opentaint.dataflow.bidi.interp.Interpreter
import org.opentaint.dataflow.bidi.interp.UnresolvedCallObserver
import org.opentaint.dataflow.bidi.store.RecordStore
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
) : AutoCloseable {
    @OptIn(DelicateCoroutinesApi::class, ExperimentalCoroutinesApi::class)
    val dispatcher: ExecutorCoroutineDispatcher = newFixedThreadPoolContext(threads, "bidi-worker")

    val unresolvedObserver: UnresolvedCallObserver? get() = interpreter as? UnresolvedCallObserver

    override fun close(): Unit = dispatcher.close()
}
