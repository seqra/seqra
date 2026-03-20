package org.opentaint.dataflow.ap.ifds

import org.opentaint.dataflow.ap.ifds.access.ApManager

sealed interface AccessPathBase {
    override fun toString(): String

    data object This : AccessPathBase {
        override fun toString(): String = "<this>"
    }

    @ConsistentCopyVisibility
    data class LocalVar private constructor(val idx: Int) : AccessPathBase {
        override fun toString(): String = "var($idx)"

        companion object{
            fun create(idx: Int): LocalVar = LocalVar(idx)
        }
    }

    @ConsistentCopyVisibility
    data class Argument private constructor(val idx: Int) : AccessPathBase {
        override fun toString(): String = "arg($idx)"

        companion object {
            fun create(idx: Int): Argument = Argument(idx)
        }
    }

    data class Constant(val typeName: String, val value: String) : AccessPathBase {
        override fun toString(): String = "const<$typeName>($value)"
    }
    data object ClassStatic : AccessPathBase {
        override fun toString(): String = "<static>"
    }

    data object Return : AccessPathBase {
        override fun toString(): String = "ret"
    }

    data object Exception : AccessPathBase {
        override fun toString(): String = "exception"
    }

    companion object {
        private const val PREDEFINED_ARGS = 16
        private const val PREDEFINED_LOCALS = 128

        private val arguments = Array(PREDEFINED_ARGS) { Argument.create(it) }
        private val localVars = Array(PREDEFINED_LOCALS) { LocalVar.create(it) }

        fun LocalVar(idx: Int): LocalVar {
            if (idx < PREDEFINED_LOCALS) return localVars[idx]
            return LocalVar.create(idx)
        }

        fun Argument(idx: Int): Argument {
            if (idx < PREDEFINED_ARGS) return arguments[idx]
            return Argument.create(idx)
        }
    }
}

sealed class Accessor : Comparable<Accessor> {
    abstract fun toSuffix(): String
    protected abstract val accessorClassId: Int

    override fun compareTo(other: Accessor): Int {
        if (accessorClassId != other.accessorClassId) {
            return accessorClassId.compareTo(other.accessorClassId)
        }

        return when (this) {
            ElementAccessor, FinalAccessor, AnyAccessor, ValueAccessor -> 0 // Definitely equal
            is FieldAccessor -> this.compareToFieldAccessor(other as FieldAccessor)
            is TaintMarkAccessor -> this.compareToTaintMarkAccessor(other as TaintMarkAccessor)
            is ClassStaticAccessor -> this.compareToClassStaticAccessor(other as ClassStaticAccessor)
        }
    }
}

data class TaintMarkAccessor(val mark: String): Accessor() {
    override fun toSuffix(): String = "![$mark]"
    override fun toString(): String = "![$mark]"

    override val accessorClassId: Int = 3

    fun compareToTaintMarkAccessor(other: TaintMarkAccessor): Int {
        return mark.compareTo(other.mark)
    }
}

data class FieldAccessor(
    val className: String,
    val fieldName: String,
    val fieldType: String
) : Accessor() {
    override fun toSuffix(): String = ".$fieldName"
    override fun toString(): String = "${className.substringAfterLast('.')}#${fieldName}"

    override val accessorClassId: Int = 2

    fun compareToFieldAccessor(other: FieldAccessor): Int {
        var result = fieldName.length.compareTo(other.fieldName.length)

        if (result == 0) {
            result = fieldName.compareTo(other.fieldName)
        }

        if (result == 0) {
            result = className.compareTo(other.className)
        }

        if (result == 0) {
            result = fieldType.compareTo(other.fieldType)
        }

        return result
    }
}

data object ElementAccessor : Accessor() {
    override fun toSuffix(): String = "[*]"
    override fun toString(): String = "*"

    override val accessorClassId: Int = 0
}

data object FinalAccessor : Accessor() {
    override fun toSuffix(): String = ".\$"
    override fun toString(): String = "\$"

    override val accessorClassId: Int = 1
}

data object AnyAccessor : Accessor() {
    override fun toString(): String = "[any]"
    override fun toSuffix(): String = ".[any]"

    override val accessorClassId: Int  = 4

    fun containsAccessor(accessor: Accessor): Boolean = accessor is FieldAccessor || accessor is ElementAccessor
}

data class ClassStaticAccessor(val typeName: String) : Accessor() {
    override fun toSuffix(): String = "<static>($typeName)"
    override fun toString(): String = "<static>($typeName)"

    override val accessorClassId: Int = 5

    fun compareToClassStaticAccessor(other: ClassStaticAccessor): Int {
        return typeName.compareTo(other.typeName)
    }
}

object ValueAccessor : Accessor() {
    override fun toString(): String = "[value]"
    override fun toSuffix(): String = ".[value]"

    override val accessorClassId: Int = 6
}

inline fun <T : Any> ApManager.tryAnyAccessorOrNull(accessor: Accessor, body: () -> T?): T? {
    if (!AnyAccessor.containsAccessor(accessor)) return null
    if (!anyAccessorUnrollStrategy.unrollAccessor(accessor)) return null
    return body()
}
