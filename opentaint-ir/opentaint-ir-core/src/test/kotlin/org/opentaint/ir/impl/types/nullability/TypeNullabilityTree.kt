package org.opentaint.opentaint-ir.impl.types.nullability

import org.opentaint.opentaint-ir.api.JIRArrayType
import org.opentaint.opentaint-ir.api.JIRBoundedWildcard
import org.opentaint.opentaint-ir.api.JIRClassType
import org.opentaint.opentaint-ir.api.JIRType
import org.opentaint.opentaint-ir.api.JIRTypeVariable
import org.opentaint.opentaint-ir.api.JIRTypeVariableDeclaration
import org.opentaint.opentaint-ir.api.JIRUnboundWildcard

data class TypeNullabilityTree(val isNullable: Boolean?, val innerTypes: List<TypeNullabilityTree>)

class TreeBuilder(private val isNullable: Boolean?) {
    private val innerTypes: MutableList<TypeNullabilityTree> = mutableListOf()

    operator fun TypeNullabilityTree.unaryPlus() {
        this@TreeBuilder.innerTypes.add(this)
    }

    fun build(): TypeNullabilityTree = TypeNullabilityTree(isNullable, innerTypes)
}

fun buildTree(isNullable: Boolean?, actions: TreeBuilder.() -> Unit = {}) =
    TreeBuilder(isNullable).apply(actions).build()

val JIRType.nullabilityTree: TypeNullabilityTree
    get() {
        return when (this) {
            is JIRClassType -> TypeNullabilityTree(nullable, typeArguments.map { it.nullabilityTree })
            is JIRArrayType -> TypeNullabilityTree(nullable, listOf(elementType.nullabilityTree))
            is JIRBoundedWildcard -> (upperBounds + lowerBounds).map { it.nullabilityTree }.single()  // For bounded wildcard we are interested only in nullability of bound, not of the wildcard itself
            is JIRUnboundWildcard -> TypeNullabilityTree(nullable, listOf())
            is JIRTypeVariable -> TypeNullabilityTree(nullable, bounds.map { it.nullabilityTree })
            is JIRTypeVariableDeclaration -> TypeNullabilityTree(nullable, bounds.map { it.nullabilityTree })
            else -> TypeNullabilityTree(nullable, listOf())
        }
    }