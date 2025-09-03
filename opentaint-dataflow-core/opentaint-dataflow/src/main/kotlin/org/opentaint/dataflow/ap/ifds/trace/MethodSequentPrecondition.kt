package org.opentaint.dataflow.ap.ifds.trace

import org.opentaint.dataflow.ap.ifds.access.InitialFactAp

fun interface MethodSequentPrecondition {
    fun factPrecondition(fact: InitialFactAp): List<InitialFactAp>?
}