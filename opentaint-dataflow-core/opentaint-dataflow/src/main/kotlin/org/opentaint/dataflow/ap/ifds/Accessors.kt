package org.opentaint.dataflow.ap.ifds

sealed interface AccessPathBase {
    override fun toString(): String

    data object This : AccessPathBase {
        override fun toString(): String = "<this>"
    }

    data class LocalVar(val idx: Int) : AccessPathBase {
        override fun toString(): String = "var($idx)"
    }

    data class Argument(val idx: Int) : AccessPathBase {
        override fun toString(): String = "arg($idx)"
    }

    data class Constant(val typeName: String, val value: String) : AccessPathBase {
        override fun toString(): String = "const<$typeName>($value)"
    }

    data class ClassStatic(val typeName: String) : AccessPathBase {
        override fun toString(): String = "<static>($typeName)"
    }

    data object Return : AccessPathBase {
        override fun toString(): String = "ret"
    }

    data object Exception : AccessPathBase {
        override fun toString(): String = "exception"
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
            ElementAccessor, FinalAccessor, AnyAccessor -> 0 // Definitely equal
            is FieldAccessor -> this.compareToFieldAccessor(other as FieldAccessor)
            is TaintMarkAccessor -> this.compareToTaintMarkAccessor(other as TaintMarkAccessor)
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
    override fun toString(): String = "${className}#${fieldName}:$fieldType"

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

inline fun <T : Any> tryAnyAccessorOrNull(accessor: Accessor, body: () -> T?): T? {
    if (!AnyAccessor.containsAccessor(accessor)) return null
    return body()
}
