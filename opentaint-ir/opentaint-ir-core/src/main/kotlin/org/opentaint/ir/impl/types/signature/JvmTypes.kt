
package org.opentaint.ir.impl.types.signature

import org.opentaint.ir.api.PredefinedPrimitives

/**
 * @property isNullable denotes the nullability of the type in terms of Kotlin type system.
 * It has three possible values:
 * - true -- means that type is nullable, a.k.a. T?
 * - false -- means that type is non-nullable, a.k.a. T
 * - null -- means that type has unknown nullability, a.k.a. T!
 */
sealed class JvmType(val isNullable: Boolean?) {

    abstract val displayName: String
}

internal sealed class JvmRefType(isNullable: Boolean?) : JvmType(isNullable)

internal class JvmArrayType(val elementType: JvmType, isNullable: Boolean? = null) : JvmRefType(isNullable) {

    override val displayName: String
        get() = elementType.displayName + "[]"

}

internal class JvmParameterizedType(
    val name: String,
    val parameterTypes: List<JvmType>,
    isNullable: Boolean? = null
) : JvmRefType(isNullable) {

    override val displayName: String
        get() = name + "<${parameterTypes.joinToString { it.displayName }}>"

    class JvmNestedType(
        val name: String,
        val parameterTypes: List<JvmType>,
        val ownerType: JvmType,
        isNullable: Boolean? = null
    ) : JvmRefType(isNullable) {

        override val displayName: String
            get() = name + "<${parameterTypes.joinToString { it.displayName }}>"

    }

}

internal class JvmClassRefType(val name: String, isNullable: Boolean? = null) : JvmRefType(isNullable) {

    override val displayName: String
        get() = name

}

/**
 * For type variables, the nullability is defined similarly to all other types:
 *  - kt T? and java @Nullable T -- nullable (true)
 *  - kt T and java @NotNull T -- non-nullable (false)
 *  - java T -- undefined nullability (null)
 *
 *  This is important to properly handle nullability during substitutions. Not that kt T and java @NotNull T still have
 *  differences -- see comment for `JIRSubstitutorImpl.relaxNullabilityAfterSubstitution` for more details
 */
class JvmTypeVariable(val symbol: String, isNullable: Boolean? = null) : JvmType(isNullable) {

    constructor(declaration: JvmTypeParameterDeclaration, isNullable: Boolean? = null) : this(declaration.symbol, isNullable) {
        this.declaration = declaration
    }

    var declaration: JvmTypeParameterDeclaration? = null

    override val displayName: String
        get() = symbol

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as JvmTypeVariable

        if (symbol != other.symbol) return false
        if (declaration != other.declaration) return false
        if (isNullable != other.isNullable) return false

        return true
    }

    override fun hashCode(): Int {
        var result = symbol.hashCode()
        result = 63 * result + 31 * (declaration?.hashCode() ?: 1) + (isNullable?.hashCode() ?: 0)
        return result
    }
}

// Nullability has no sense in wildcards, so we suppose them to be always nullable for definiteness
internal sealed class JvmWildcard: JvmType(isNullable = true)

internal sealed class JvmBoundWildcard(val bound: JvmType) : JvmWildcard() {

    internal class JvmUpperBoundWildcard(boundType: JvmType) : JvmBoundWildcard(boundType) {
        override val displayName: String
            get() = "? extends ${bound.displayName}"

    }

    internal class JvmLowerBoundWildcard(boundType: JvmType) : JvmBoundWildcard(boundType) {
        override val displayName: String
            get() = "? super ${bound.displayName}"

    }
}

internal object JvmUnboundWildcard : JvmWildcard() {

    override val displayName: String
        get() = "*"
}

internal class JvmPrimitiveType(val ref: String) : JvmRefType(isNullable = false) {

    companion object {
        fun of(descriptor: Char): JvmType {
            return when (descriptor) {
                'V' -> JvmPrimitiveType(PredefinedPrimitives.void)
                'Z' -> JvmPrimitiveType(PredefinedPrimitives.boolean)
                'B' -> JvmPrimitiveType(PredefinedPrimitives.byte)
                'S' -> JvmPrimitiveType(PredefinedPrimitives.short)
                'C' -> JvmPrimitiveType(PredefinedPrimitives.char)
                'I' -> JvmPrimitiveType(PredefinedPrimitives.int)
                'J' -> JvmPrimitiveType(PredefinedPrimitives.long)
                'F' -> JvmPrimitiveType(PredefinedPrimitives.float)
                'D' -> JvmPrimitiveType(PredefinedPrimitives.double)
                else -> throw IllegalArgumentException("Not a valid primitive type descriptor: $descriptor")
            }
        }
    }

    override val displayName: String
        get() = ref

}