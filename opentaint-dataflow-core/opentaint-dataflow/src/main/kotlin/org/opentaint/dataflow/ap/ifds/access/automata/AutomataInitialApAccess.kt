package org.opentaint.dataflow.ap.ifds.access.automata

import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.ap.ifds.ExclusionSet
import org.opentaint.dataflow.ap.ifds.access.InitialFactAp
import org.opentaint.dataflow.ap.ifds.access.common.InitialApAccess

interface AutomataInitialApAccess: InitialApAccess<AccessGraph> {
    override fun getInitialAccess(factAp: InitialFactAp): AccessGraph = (factAp as AccessGraphInitialFactAp).access
    override fun createInitial(base: AccessPathBase, ap: AccessGraph, ex: ExclusionSet): InitialFactAp = AccessGraphInitialFactAp(base, ap, ex)
}
