package org.opentaint.dataflow.jvm.ap.ifds.access.automata

import org.opentaint.dataflow.jvm.ap.ifds.AccessPathBase
import org.opentaint.dataflow.jvm.ap.ifds.Accessor
import org.opentaint.dataflow.jvm.ap.ifds.ExclusionSet
import org.opentaint.dataflow.jvm.ap.ifds.access.InitialFactAp

data class AccessGraphInitialFactAp(
    override val base: AccessPathBase,
    val access: AccessGraph,
    override val exclusions: ExclusionSet,
) : InitialFactAp {
    override val size: Int get() = access.size

    override fun exclude(accessor: Accessor): InitialFactAp =
        AccessGraphInitialFactAp(base, access, exclusions.add(accessor))

    override fun replaceExclusions(exclusions: ExclusionSet): InitialFactAp =
        AccessGraphInitialFactAp(base, access, exclusions)
}
