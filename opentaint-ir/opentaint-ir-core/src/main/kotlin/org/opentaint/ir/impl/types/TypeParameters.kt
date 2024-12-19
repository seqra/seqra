package org.opentaint.ir.impl.types

import kotlinx.collections.immutable.toPersistentMap
import org.opentaint.ir.api.JIRAccessible
import org.opentaint.ir.api.JIRClassOrInterface
import org.opentaint.ir.api.JIRMethod
import org.opentaint.ir.api.JIRRefType
import org.opentaint.ir.api.JIRTypeVariableDeclaration
import org.opentaint.ir.api.ext.isStatic
import org.opentaint.ir.impl.types.signature.JvmTypeParameterDeclaration
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
    if (!isStatic) {
        val fromOuter = outerClass?.allVisibleTypeParameters()
        val fromMethod = outerMethod?.allVisibleTypeParameters()
        return (direct + (fromMethod ?: fromOuter).orEmpty()).toPersistentMap()
    }
    return direct
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
    val bounds = bounds?.map { classpath.typeOf(it) as JIRRefType }
    return JIRTypeVariableDeclarationImpl(symbol, bounds.orEmpty(), owner = owner)
}