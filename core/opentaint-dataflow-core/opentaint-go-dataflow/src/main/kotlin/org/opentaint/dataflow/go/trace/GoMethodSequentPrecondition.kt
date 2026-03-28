package org.opentaint.dataflow.go.trace

import org.opentaint.dataflow.ap.ifds.access.InitialFactAp
import org.opentaint.dataflow.ap.ifds.trace.MethodSequentPrecondition
import org.opentaint.dataflow.ap.ifds.trace.MethodSequentPrecondition.SequentPrecondition

class GoMethodSequentPrecondition : MethodSequentPrecondition {
    override fun factPrecondition(fact: InitialFactAp): Set<SequentPrecondition> =
        setOf(SequentPrecondition.Unchanged)
}
