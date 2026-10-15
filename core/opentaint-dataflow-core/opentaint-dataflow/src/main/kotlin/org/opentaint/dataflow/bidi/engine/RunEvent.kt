package org.opentaint.dataflow.bidi.engine

import org.opentaint.dataflow.bidi.ap.Facts
import org.opentaint.dataflow.bidi.ap.InitialAp
import org.opentaint.dataflow.bidi.ap.Layer
import org.opentaint.dataflow.bidi.ap.MethodKey
import org.opentaint.dataflow.bidi.store.CallerRef
import org.opentaint.dataflow.bidi.store.RequestMarkSet
import java.util.Collections

sealed interface RunEvent {
    data class Start(val root: MethodKey) : RunEvent
    data class LinkIn(val callee: MethodKey, val ref: CallerRef, val linkLayer: Layer, val added: Facts) : RunEvent
    data class ZeroIn(val callee: MethodKey) : RunEvent

    /** Independent mark requests for one exact target origin; the set owns immutable storage. */
    data class MarkRequestsIn(val method: MethodKey, val premise: InitialAp, val marks: RequestMarkSet) : RunEvent

    /** Only a subscribed caller runner handles this event; the publisher reads no caller directory. */
    data class QuestionDelivery(val callee: MethodKey, val delta: QuestionDelta) : RunEvent

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
    is RunEvent.ZeroIn -> callee
    is RunEvent.MarkRequestsIn -> method
    is RunEvent.InitialAnswersIn -> method
    is RunEvent.Delivery, is RunEvent.QuestionDelivery, is RunEvent.Work ->
        error("Delivery and Work require their owning runner")
}
