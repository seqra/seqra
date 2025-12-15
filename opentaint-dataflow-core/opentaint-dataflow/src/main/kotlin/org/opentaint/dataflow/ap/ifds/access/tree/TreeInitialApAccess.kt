package org.opentaint.dataflow.ap.ifds.access.tree

import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.ap.ifds.ExclusionSet
import org.opentaint.dataflow.ap.ifds.access.InitialFactAp
import org.opentaint.dataflow.ap.ifds.access.common.InitialApAccess

interface TreeInitialApAccess: InitialApAccess<AccessPath.AccessNode?> {
    override fun getInitialAccess(factAp: InitialFactAp): AccessPath.AccessNode? =
        (factAp as AccessPath).access

    override fun createInitial(base: AccessPathBase, ap: AccessPath.AccessNode?, ex: ExclusionSet): InitialFactAp =
        AccessPath(base, ap, ex)
}
