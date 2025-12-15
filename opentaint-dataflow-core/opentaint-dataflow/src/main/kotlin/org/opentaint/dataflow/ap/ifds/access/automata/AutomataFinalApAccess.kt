package org.opentaint.dataflow.ap.ifds.access.automata

import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.ap.ifds.ExclusionSet
import org.opentaint.dataflow.ap.ifds.access.FinalFactAp
import org.opentaint.dataflow.ap.ifds.access.common.FinalApAccess

interface AutomataFinalApAccess : FinalApAccess<AccessGraph> {
    override fun getFinalAccess(factAp: FinalFactAp): AccessGraph = (factAp as AccessGraphFinalFactAp).access
    override fun createFinal(base: AccessPathBase, ap: AccessGraph, ex: ExclusionSet): FinalFactAp = AccessGraphFinalFactAp(base, ap, ex)
}
