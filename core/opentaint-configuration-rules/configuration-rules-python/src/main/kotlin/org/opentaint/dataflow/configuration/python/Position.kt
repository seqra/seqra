package org.opentaint.dataflow.configuration.python

sealed interface Position

data class Argument(val index: Int) : Position

data object AnyArgument : Position

data class KwArgument(val name: String) : Position

data object This : Position

data object Result : Position

data class ClassRef(val fqn: String) : Position

data class PositionWithAccess(
    val base: Position,
    val access: PositionAccessor,
) : Position

sealed interface PositionAccessor {
    data object ElementAccessor : PositionAccessor

    data class FieldAccessor(val name: String) : PositionAccessor
}
