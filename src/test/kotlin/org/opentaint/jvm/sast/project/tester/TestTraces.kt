package org.opentaint.jvm.sast.project.tester

import kotlinx.serialization.Serializable
import org.opentaint.ir.api.jvm.JIRMethod
import org.opentaint.ir.api.jvm.PredefinedPrimitives
import org.opentaint.dataflow.configuration.jvm.Argument
import org.opentaint.dataflow.configuration.jvm.ClassStatic
import org.opentaint.dataflow.configuration.jvm.Position
import org.opentaint.dataflow.configuration.jvm.PositionWithAccess
import org.opentaint.dataflow.configuration.jvm.Result
import org.opentaint.dataflow.configuration.jvm.This

@Serializable
data class TracePair(
    val source: TraceNode,
    val sink: TraceNode,
    val trace: List<TraceLocation>
)

@Serializable
data class TraceNode(
    val location: TraceLocation,
    val position: String,
    val mark: String,
)

@Serializable
data class TraceLocation(
    val isProjectLocation: Boolean,
    val cls: String,
    val methodName: String,
    val methodDesc: String,
    val instIndex: Int,
    val instStr: String
) {
    override fun toString(): String {
        return "$cls#$methodName - $instStr"
    }
}

private fun inBounds(method: JIRMethod, position: Position): Boolean =
    when (position) {
        is Argument -> position.index in method.parameters.indices
        This -> !method.isStatic
        Result -> method.returnType.typeName != PredefinedPrimitives.Void
        is PositionWithAccess ->  error("")
        is ClassStatic -> TODO()
    }

fun specializePosition(method: JIRMethod, position: String): List<Position> = TODO()
