package org.opentaint.ir.api.python

/**
 * PIR Type hierarchy — sealed for exhaustive `when` matching.
 * Analogous to JIRType but tailored for Python's type system.
 *
 * Key differences from JIR:
 * - No primitive types (Python has no primitives)
 * - Union types are first-class (not annotations)
 * - Optional is a sugar for Union[X, None]
 * - Tuple types with fixed vs variable length
 * - Literal types (Literal[42])
 * - TypeVar for generics
 */
sealed interface PIRType

/** A class/instance type. The most common type. */
data class PIRClassType(
    val qualifiedName: String,
    val typeArgs: List<PIRType> = emptyList(),
    val isOptional: Boolean = false,
) : PIRType {
    override fun toString(): String {
        val base = if (typeArgs.isEmpty()) qualifiedName
            else "$qualifiedName[${typeArgs.joinToString(", ")}]"
        return if (isOptional) "$base?" else base
    }
}

/** A callable type: (paramTypes) -> returnType */
data class PIRFunctionType(
    val paramTypes: List<PIRType>,
    val returnType: PIRType,
) : PIRType {
    override fun toString(): String =
        "(${paramTypes.joinToString(", ")}) -> $returnType"
}

/** Union[A, B, C] */
data class PIRUnionType(
    val members: List<PIRType>,
) : PIRType {
    override fun toString(): String =
        members.joinToString(" | ")
}

/** tuple[int, str] or tuple[int, ...] */
data class PIRTupleType(
    val elementTypes: List<PIRType>,
    val isVarLength: Boolean = false,
) : PIRType {
    override fun toString(): String = if (isVarLength && elementTypes.size == 1)
        "tuple[${elementTypes[0]}, ...]"
    else "tuple[${elementTypes.joinToString(", ")}]"
}

/** Literal[42], Literal["hello"] */
data class PIRLiteralType(
    val value: String,
    val baseType: PIRType,
) : PIRType {
    override fun toString(): String = "Literal[$value]"
}

/** The Any type (dynamic/unknown). */
data object PIRAnyType : PIRType {
    override fun toString(): String = "Any"
}

/** The Never/NoReturn type. */
data object PIRNeverType : PIRType {
    override fun toString(): String = "Never"
}

/** The None type (NoneType). */
data object PIRNoneType : PIRType {
    override fun toString(): String = "None"
}

/** A type variable (generic parameter). */
data class PIRTypeVarType(
    val name: String,
    val bounds: List<PIRType> = emptyList(),
) : PIRType {
    override fun toString(): String = name
}
