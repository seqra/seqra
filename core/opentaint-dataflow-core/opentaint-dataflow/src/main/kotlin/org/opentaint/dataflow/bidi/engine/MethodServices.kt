package org.opentaint.dataflow.bidi.engine

import org.opentaint.dataflow.bidi.ap.*
import org.opentaint.dataflow.bidi.interp.SinkRule
import org.opentaint.dataflow.bidi.interp.SourceRuleOccurrence
import org.opentaint.dataflow.bidi.interp.ResumeSite
import org.opentaint.dataflow.bidi.interp.ActionId
import org.opentaint.dataflow.bidi.store.CallerRef
import org.opentaint.dataflow.bidi.store.ConjunctionStore
import org.opentaint.dataflow.bidi.store.RequestMarkSet
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
    fun addMarkRequest(premise: InitialAp, mark: TaintMark)
    fun addMarkRequests(premise: InitialAp, marks: RequestMarkSet) {
        marks.forEachMark { addMarkRequest(premise, it) }
    }
    val hasPendingRequests: Boolean get() = false
    /** A positive, bounded service opportunity; callbacks can admit another frontier. */
    fun flushRequests(quantum: Int): Boolean = true
    fun conjunction(edge: ConjunctiveEdge, premise: PremiseKey, facts: Facts, node: CommonInst,
                    sink: (PremiseKey, Facts) -> Unit)
    fun conjunctionAt(edge: ConjunctiveEdge, premise: PremiseKey, facts: Facts, node: CommonInst,
                      at: OperationInput, sink: (PremiseKey, Facts) -> Unit) = conjunction(edge, premise, facts, node, sink)
    fun sourcePredicate(source: SourceRuleOccurrence, premise: PremiseKey, facts: Facts, node: CommonInst,
                        statementEdge: Boolean, sink: (PremiseKey, Facts) -> Unit) {
        error("These local services do not implement rule predicates")
    }
    fun sourcePredicateAt(source: SourceRuleOccurrence, premise: PremiseKey, facts: Facts, node: CommonInst,
                          statementEdge: Boolean, at: OperationInput,
                          sink: (PremiseKey, Facts) -> Unit) = sourcePredicate(source, premise, facts, node, statementEdge, sink)
    fun sourcePredicateActionsAt(source: SourceRuleOccurrence, premise: PremiseKey, facts: Facts, node: CommonInst,
                                statementEdge: Boolean, at: OperationInput,
                                sink: (PremiseKey, Facts, ActionId?) -> Unit) =
        sourcePredicateAt(source, premise, facts, node, statementEdge, at) { pr, result -> sink(pr, result, null) }
    fun checkSinks(premise: PremiseKey, node: CommonInst, facts: Facts, sinks: List<SinkRule>): Triggers
    fun checkSinksAt(premise: PremiseKey, node: CommonInst, facts: Facts, sinks: List<SinkRule>, at: OperationInput): Triggers =
        checkSinks(premise, node, facts, sinks)
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
    fun resumePoint(premise: PremiseKey, facts: Facts, site: ResumeSite, state: CallResumeState?) {
        error("These call services cannot resume a static point")
    }
    fun staticSupply(part: Subscription, pub: Publication, member: Int, supply: RegionGroups) {
        error("These call services cannot join static supply")
    }
    fun freeze()
}

fun interface MethodCallServicesFactory {
    fun create(core: RunMethodAnalyzer): MethodCallServices
}
