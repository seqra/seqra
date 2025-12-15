package org.opentaint.dataflow.ap.ifds.access.common

import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.ap.ifds.ExclusionSet
import org.opentaint.dataflow.ap.ifds.access.InitialFactAp

interface InitialApAccess<IAP> {
    fun getInitialAccess(factAp: InitialFactAp): IAP
    fun createInitial(base: AccessPathBase, ap: IAP, ex: ExclusionSet): InitialFactAp
}
