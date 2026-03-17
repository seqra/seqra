package org.opentaint.dataflow.ap.ifds.access.common

import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.ap.ifds.ExclusionSet
import org.opentaint.dataflow.ap.ifds.access.FinalFactAp

interface FinalApAccess<FAP> {
    fun getFinalAccess(factAp: FinalFactAp): FAP
    fun createFinal(base: AccessPathBase, ap: FAP, ex: ExclusionSet): FinalFactAp
}
