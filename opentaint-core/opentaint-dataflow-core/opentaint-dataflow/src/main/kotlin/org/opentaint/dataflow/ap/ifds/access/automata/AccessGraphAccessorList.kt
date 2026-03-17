package org.opentaint.dataflow.ap.ifds.access.automata

import org.opentaint.dataflow.ap.ifds.Accessor
import org.opentaint.dataflow.ap.ifds.access.AccessorList
import org.opentaint.dataflow.ap.ifds.tryAnyAccessorOrNull

interface AccessGraphAccessorList: AccessorList {
    val access: AccessGraph

    override fun getAllAccessors(): Set<Accessor> = access.getAllOwnAccessors()

    override fun getStartAccessors(): Set<Accessor> = access.getInitialSuccessorsAccessors()

    override fun startsWithAccessor(accessor: Accessor): Boolean = with(access.manager) {
        if (access.startsWith(accessor.idx)) return true
        if (!access.startsWith(anyAccessorIdx)) return false
        return tryAnyAccessorOrNull(accessor) { true } == true
    }
}
