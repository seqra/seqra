package org.opentaint.dataflow.bidi.interp

import org.opentaint.dataflow.bidi.ap.PathEdge
import org.opentaint.dataflow.bidi.ap.Pattern

/** A logical operation address survives eviction and rebuilding of a full form. */
data class OperationId(
    val form: FormKey,
    val stageOrdinal: Int = 0,
    val action: ActionId? = null,
    val substep: Int = 0,
) {
    init { require(stageOrdinal >= 0 && substep >= 0) }
}

/** Lowering supplies these descriptors. A static destination alone does not identify a write. */
sealed interface StaticOperation {
    val id: OperationId

    data class Read(override val id: OperationId, val transfer: PathEdge) : StaticOperation
    data class Write(
        override val id: OperationId,
        val target: Pattern,
        val transfer: PathEdge? = null,
        val action: ActionId? = id.action,
    ) : StaticOperation
    data class MarkInput(
        override val id: OperationId,
        val position: Pattern,
        // ANY predicates include their point. A BELOW cleaner excludes it.
        val includeBase: Boolean = true,
    ) : StaticOperation
}

enum class CleanerPhase { INPUT, READ, CLEAN, WRITE }

/** The operation input is different from its output; replies never restart a method entry. */
sealed interface ResumeSite {
    val operation: OperationId
    data class StatementInput(override val operation: OperationId) : ResumeSite
    data class BoundaryInput(override val operation: OperationId, val atStart: Boolean) : ResumeSite
    data class CallStageInput(override val operation: OperationId) : ResumeSite
    data class CleanerInput(
        override val operation: OperationId,
        val step: Int,
        val phase: CleanerPhase,
    ) : ResumeSite
    data class ReadOutput(override val operation: OperationId, val input: ResumeSite) : ResumeSite
}
