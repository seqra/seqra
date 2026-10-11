package org.opentaint.dataflow.bidi.engine

import org.opentaint.dataflow.bidi.ap.*
import org.opentaint.dataflow.bidi.interp.SinkRule
import org.opentaint.dataflow.bidi.store.CallerRef
import org.opentaint.dataflow.bidi.store.ConjunctionStore
import org.opentaint.dataflow.bidi.store.RequestKind
import org.opentaint.ir.api.common.cfg.CommonInst
import java.util.Collections

internal typealias ResultSink = (PremiseKey, Facts) -> Unit

/** A run supplies its actual publication destination. A failure must propagate. */
fun interface PublicationSink {
    fun publish(method: MethodKey, batch: List<Publication>)
}

/** Evaluated literal parts remain separate even when no complete sink fired. */
class Triggers(fired: List<Pair<SinkRule, Layer>>, parts: List<Facts>) {
    val fired: List<Pair<SinkRule, Layer>> = Collections.unmodifiableList(ArrayList(fired))
    val parts: List<Facts> = Collections.unmodifiableList(ArrayList(parts))
    operator fun plus(other: Triggers): Triggers =
        Triggers((fired + other.fired).distinct(), parts + other.parts)

    companion object { val NONE = Triggers(emptyList(), emptyList()) }
}

/** Required local operations bind to the one actor's stores and run inputs. */
interface MethodLocalServices {
    val conjunctions: ConjunctionStore?
    fun addRequest(premise: InitialAp, kind: RequestKind)
    fun onAddedLink(ref: CallerRef, linkLayer: Layer, delta: Facts)
    fun conjunction(edge: ConjunctiveEdge, premise: PremiseKey, facts: Facts, node: CommonInst,
                    sink: (PremiseKey, Facts) -> Unit)
    fun checkSinks(premise: PremiseKey, node: CommonInst, facts: Facts, sinks: List<SinkRule>): Triggers
    fun fireSinkSeeds(node: CommonInst, emit: (Facts) -> Unit)
    fun fireTriggerSeeds(node: CommonInst, sink: SinkRule, emit: (Facts) -> Unit)
    fun freeze()
}

fun interface MethodLocalServicesFactory {
    fun create(core: RunMethodAnalyzer): MethodLocalServices
}

/** Call processing and resume operations are supplied by the call engine. */
interface MethodCallServices : SummaryApplier {
    fun process(item: EdgeDelta)
    fun freeze()
}

fun interface MethodCallServicesFactory {
    fun create(core: RunMethodAnalyzer): MethodCallServices
}
