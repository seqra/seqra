package org.opentaint.dataflow.bidi.engine

import org.opentaint.dataflow.bidi.ap.Facts
import org.opentaint.dataflow.bidi.ap.InitialAp
import org.opentaint.dataflow.bidi.ap.Layer
import org.opentaint.dataflow.bidi.ap.MethodKey
import org.opentaint.dataflow.bidi.store.CallerRef
import java.util.Collections

sealed interface RunEvent {
    data class Start(val root: MethodKey) : RunEvent
    data class LinkIn(val callee: MethodKey, val ref: CallerRef, val linkLayer: Layer, val added: Facts) : RunEvent
    data class PreparedInitialsIn(val callee: MethodKey, val prepared: Run1PreparedInitials) : RunEvent
    data class ZeroIn(val callee: MethodKey) : RunEvent
    data class StaticEntriesIn(val callee: MethodKey, val entries: org.opentaint.dataflow.bidi.ap.FlowTree) : RunEvent
    data class ConcreteStaticEntriesIn(val callee: MethodKey, val entries: org.opentaint.dataflow.bidi.ap.TaintTree) : RunEvent
    /** Posted only to subscribed caller owners; the wave contains no concrete evidence. */
    data class StaticNeed(val visit: StaticNeedVisit) : RunEvent

    /** A detached immutable batch addressed to the full method owner. */
    data class MarkRequestsIn(val method: MethodKey, val rows: RequestRows) : RunEvent

    /** Only a subscribed caller runner handles this event; the publisher reads no caller directory. */
    data class ChangedRequests(val callee: MethodKey, val rows: RequestRows) : RunEvent

    /** Shared-view callback: immutable interest only, addressed to its exact caller site. */
    data class CallsiteRequests(val key: CallSiteKey, val rows: RequestRows) : RunEvent

    /** One counted idle wake; queued method Work owns P until its method-empty hook. */
    data class RequestDrain(val caller: MethodKey) : RunEvent

    /** Answers own only canonical initial keys, never supplier Facts or caller bindings. */
    class InitialAnswersIn(val method: MethodKey, answers: Collection<InitialAp>) : RunEvent {
        val answers: List<InitialAp> = Collections.unmodifiableList(ArrayList(answers))
    }

    /** The publisher can clear its pending list as soon as this event is sent. */
    class Delivery(val callee: MethodKey, publications: List<Publication>) : RunEvent {
        val publications: List<Publication> = Collections.unmodifiableList(ArrayList(publications))
    }

    class Work(val analyzer: RunActor) : RunEvent
}

fun RunEvent.target(): MethodKey = when (this) {
    is RunEvent.Start -> root
    is RunEvent.LinkIn -> callee
    is RunEvent.PreparedInitialsIn -> callee
    is RunEvent.ZeroIn -> callee
    is RunEvent.StaticEntriesIn -> callee
    is RunEvent.ConcreteStaticEntriesIn -> callee
    is RunEvent.MarkRequestsIn -> method
    is RunEvent.InitialAnswersIn -> method
    is RunEvent.CallsiteRequests -> key.caller
    is RunEvent.RequestDrain -> caller
    is RunEvent.Delivery, is RunEvent.ChangedRequests, is RunEvent.StaticNeed, is RunEvent.Work ->
        error("Delivery and Work require their owning runner")
}
