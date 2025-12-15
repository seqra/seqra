package org.opentaint.dataflow.ap.ifds.access.tree

import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.ap.ifds.ExclusionSet
import org.opentaint.dataflow.ap.ifds.access.FinalFactAp
import org.opentaint.dataflow.ap.ifds.access.common.FinalApAccess

interface TreeFinalApAccess: FinalApAccess<AccessTree.AccessNode> {
    override fun getFinalAccess(factAp: FinalFactAp): AccessTree.AccessNode =
        (factAp as AccessTree).access

    override fun createFinal(base: AccessPathBase, ap: AccessTree.AccessNode, ex: ExclusionSet): FinalFactAp =
        AccessTree(base, ap, ex)
}
