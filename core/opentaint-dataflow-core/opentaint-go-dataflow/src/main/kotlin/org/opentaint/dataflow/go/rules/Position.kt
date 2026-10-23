package org.opentaint.dataflow.go.rules

sealed interface Position {
    sealed interface Simple : Position

    data class Argument(val index: Int) : Simple

    data object This : Simple {
        override fun toString(): String = javaClass.simpleName
    }

    data object Result : Simple {
        override fun toString(): String = javaClass.simpleName
    }

    data class ClassStatic(val className: String) : Simple
}

sealed interface PositionAccessor {
    data object ElementAccessor : PositionAccessor {
        override fun toString(): String = javaClass.simpleName
    }

    data class FieldAccessor(
        val className: String,
        val fieldName: String,
        val fieldType: String
    ) : PositionAccessor
}

data class PositionWithAccess(
    val base: Position,
    val access: PositionAccessor
) : Position
