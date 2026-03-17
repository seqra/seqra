package org.opentaint.ir.impl.types

import kotlinx.collections.immutable.toPersistentMap
import org.opentaint.ir.api.jvm.*
import org.opentaint.ir.impl.types.signature.MethodResolutionImpl
import org.opentaint.ir.impl.types.signature.MethodSignature
import org.opentaint.ir.impl.types.signature.TypeResolutionImpl
import org.opentaint.ir.impl.types.signature.TypeSignature

val JIRClassOrInterface.typeParameters: List<JvmTypeParameterDeclaration>
    get() {
        return (TypeSignature.of(this) as? TypeResolutionImpl)?.typeVariables ?: emptyList()
    }

val JIRMethod.typeParameters: List<JvmTypeParameterDeclaration>
    get() {
        return (MethodSignature.of(this) as? MethodResolutionImpl)?.typeVariables ?: emptyList()
    }

fun JIRClassOrInterface.directTypeParameters(): List<JvmTypeParameterDeclaration> {
    val declaredSymbols = typeParameters.map { it.symbol }.toHashSet()
    return allVisibleTypeParameters().filterKeys { declaredSymbols.contains(it) }.values.toList()
}

/**
 * returns all visible declaration without JvmTypeParameterDeclaration#declaration
 */
fun JIRClassOrInterface.allVisibleTypeParameters(): Map<String, JvmTypeParameterDeclaration> {
    val direct = typeParameters.associateBy { it.symbol }
    val fromMethod = outerMethod?.allVisibleTypeParameters().orEmpty()
    if (!isStatic) {
        val fromOuter = outerClass?.allVisibleTypeParameters().orEmpty()
        return (direct + fromOuter + fromMethod).toPersistentMap()
    }
    return (direct + fromMethod).toPersistentMap()
}

fun JIRMethod.allVisibleTypeParameters(): Map<String, JvmTypeParameterDeclaration> {
    return typeParameters.associateBy { it.symbol } + enclosingClass.allVisibleTypeParameters().takeIf { !isStatic }
        .orEmpty()
}

fun JvmTypeParameterDeclaration.asJIRDeclaration(owner: JIRAccessible): JIRTypeVariableDeclaration {
    val classpath = when (owner) {
        is JIRClassOrInterface -> owner.classpath
        is JIRMethod -> owner.enclosingClass.classpath
        else -> throw IllegalStateException("Unknown owner type $owner")
    }
    return JIRTypeVariableDeclarationImpl(symbol, classpath, bounds.orEmpty(), owner = owner)
}
