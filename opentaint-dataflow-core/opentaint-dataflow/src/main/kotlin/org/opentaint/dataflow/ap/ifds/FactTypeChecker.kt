package org.opentaint.dataflow.ap.ifds

import org.opentaint.ir.api.common.CommonType
import org.opentaint.dataflow.ap.ifds.access.FinalFactAp


interface FactTypeChecker {
    fun filterFactByLocalType(actualType: CommonType?, factAp: FinalFactAp): FinalFactAp?
    fun accessPathFilter(accessPath: List<Accessor>): FactApFilter

    sealed interface FilterResult {
        data object Accept : FilterResult
        data object Reject : FilterResult
        data class FilterNext(val filter: FactApFilter) : FilterResult
    }

    interface FactApFilter {
        fun check(accessor: Accessor): FilterResult
    }

    object AlwaysAcceptFilter : FactApFilter {
        override fun check(accessor: Accessor): FilterResult = FilterResult.Accept
    }

    object AlwaysRejectFilter : FactApFilter {
        override fun check(accessor: Accessor): FilterResult = FilterResult.Reject
    }
}