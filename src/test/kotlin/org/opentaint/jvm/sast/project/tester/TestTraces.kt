package org.opentaint.jvm.sast.dataflow

import kotlinx.serialization.Serializable
import org.opentaint.ir.api.jvm.JIRMethod
import org.opentaint.ir.api.jvm.PredefinedPrimitives
import org.opentaint.ir.impl.cfg.util.isArray
import org.opentaint.ir.taint.configuration.AllAnnotatedArguments
import org.opentaint.ir.taint.configuration.AnyArgument
import org.opentaint.ir.taint.configuration.Argument
import org.opentaint.ir.taint.configuration.Position
import org.opentaint.ir.taint.configuration.PositionWithAccess
import org.opentaint.ir.taint.configuration.Result
import org.opentaint.ir.taint.configuration.ResultAnyElement
import org.opentaint.ir.taint.configuration.SerializedPosition
import org.opentaint.ir.taint.configuration.SerializedPositionWithAccess
import org.opentaint.ir.taint.configuration.This

@Serializable
data class TracePair(
    val source: TraceNode,
    val sink: TraceNode,
    val trace: List<TraceLocation>
)

@Serializable
data class TraceNode(
    val location: TraceLocation,
    val position: SerializedPosition,
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
        ResultAnyElement -> method.returnType.isArray
        is PositionWithAccess ->  error("")
    }

fun specializePosition(method: JIRMethod, position: SerializedPosition): List<Position> = when (position) {
    is Position -> listOfNotNull(position.takeIf { inBounds(method, position) })

    AnyArgument -> if (method.parameters.isNotEmpty()) {
        method.parameters.indices.map { Argument(it) }.filter { inBounds(method, it) }
    } else {
        emptyList()
    }

    is SerializedPositionWithAccess -> specializePosition(method, position.base)
        .map { PositionWithAccess(it, position.access) }

    is AllAnnotatedArguments -> {
        method.parameters.indices.map { Argument(it) }
            .filter { inBounds(method, it) }
//            .filter { methodAnnotationMatches(method, it, position.typeMatcher) }
    }
}