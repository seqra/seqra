package org.opentaint.dataflow.ap.ifds

import org.opentaint.dataflow.ap.ifds.access.InitialFactAp

interface SideEffectKind

sealed interface SideEffectSummary {
    val kind: SideEffectKind

    data class ZeroSideEffectSummary(override val kind: SideEffectKind) : SideEffectSummary

    data class FactSideEffectSummary(val initialFactAp: InitialFactAp, override val kind: SideEffectKind) : SideEffectSummary
}
