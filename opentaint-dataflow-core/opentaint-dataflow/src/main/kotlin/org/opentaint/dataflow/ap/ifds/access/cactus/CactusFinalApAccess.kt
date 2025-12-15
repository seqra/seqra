package org.opentaint.dataflow.ap.ifds.access.cactus

import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.ap.ifds.ExclusionSet
import org.opentaint.dataflow.ap.ifds.access.FinalFactAp
import org.opentaint.dataflow.ap.ifds.access.common.FinalApAccess

interface CactusFinalApAccess: FinalApAccess<AccessCactus.AccessNode> {
    override fun getFinalAccess(factAp: FinalFactAp): AccessCactus.AccessNode =
        (factAp as AccessCactus).access

    override fun createFinal(base: AccessPathBase, ap: AccessCactus.AccessNode, ex: ExclusionSet): FinalFactAp =
        AccessCactus(base, ap, ex)
}
