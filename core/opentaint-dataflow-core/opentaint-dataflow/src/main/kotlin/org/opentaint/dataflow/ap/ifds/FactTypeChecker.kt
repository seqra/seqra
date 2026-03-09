package org.opentaint.dataflow.ap.ifds

import org.opentaint.ir.api.common.CommonType
import org.opentaint.dataflow.ap.ifds.access.FinalFactAp


interface FactTypeChecker {
    fun filterFactByLocalType(actualType: CommonType?, factAp: FinalFactAp): FinalFactAp?
    fun accessPathFilter(accessPath: List<Accessor>): FactApFilter
    fun accessPathCompatibilityFilter(accessPath: List<Accessor>): FactCompatibilityFilter

    sealed interface FilterResult {
        data object Accept : FilterResult
        data object Reject : FilterResult
        data class FilterNext(val filter: FactApFilter) : FilterResult
    }

    sealed interface CompatibilityFilterResult {
        data object Compatible: CompatibilityFilterResult
        data object NotCompatible: CompatibilityFilterResult
    }

    interface FactCompatibilityFilter {
        fun check(accessor: Accessor): CompatibilityFilterResult
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

    object AlwaysCompatibleFilter : FactCompatibilityFilter {
        override fun check(accessor: Accessor): CompatibilityFilterResult = CompatibilityFilterResult.Compatible
    }

    object Dummy : FactTypeChecker {
        override fun filterFactByLocalType(actualType: CommonType?, factAp: FinalFactAp): FinalFactAp = factAp
        override fun accessPathFilter(accessPath: List<Accessor>): FactApFilter = AlwaysAcceptFilter
        override fun accessPathCompatibilityFilter(accessPath: List<Accessor>): FactCompatibilityFilter = AlwaysCompatibleFilter
    }
}