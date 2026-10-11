package org.opentaint.dataflow.bidi.engine

import org.opentaint.dataflow.bidi.ap.Facts
import org.opentaint.dataflow.bidi.ap.InitialAp
import org.opentaint.dataflow.bidi.ap.Layer
import org.opentaint.dataflow.bidi.ap.MethodKey
import org.opentaint.dataflow.bidi.store.CallerRef
import org.opentaint.dataflow.bidi.store.RequestKind
import java.util.Collections

sealed interface RunEvent {
    data class Start(val root: MethodKey) : RunEvent
    data class LinkIn(val callee: MethodKey, val ref: CallerRef, val linkLayer: Layer, val added: Facts) : RunEvent
    data class ZeroIn(val callee: MethodKey) : RunEvent
    data class RequestIn(val method: MethodKey, val premise: InitialAp, val request: RequestKind) : RunEvent

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
    is RunEvent.RequestIn -> method
    is RunEvent.Delivery, is RunEvent.Work -> error("Delivery and Work require their owning runner")
}
