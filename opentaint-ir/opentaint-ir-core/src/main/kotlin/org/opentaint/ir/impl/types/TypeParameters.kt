package org.opentaint.ir.impl.types

import com.google.common.cache.CacheBuilder
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.toPersistentMap
import org.opentaint.ir.api.JIRAccessible
import org.opentaint.ir.api.JIRClassOrInterface
import org.opentaint.ir.api.JIRMethod
import org.opentaint.ir.api.JIRRefType
import org.opentaint.ir.api.JIRTypeVariableDeclaration
import org.opentaint.ir.api.isStatic
import org.opentaint.ir.impl.types.signature.JvmTypeParameterDeclaration
import org.opentaint.ir.impl.types.signature.MethodResolutionImpl
import org.opentaint.ir.impl.types.signature.MethodSignature
import org.opentaint.ir.impl.types.signature.TypeResolutionImpl
import org.opentaint.ir.impl.types.signature.TypeSignature
import java.time.Duration

val JIRClassOrInterface.typeParameters: List<JvmTypeParameterDeclaration>
    get() {
        return (TypeSignature.of(this) as? TypeResolutionImpl)?.typeVariables ?: emptyList()
    }

val JIRMethod.typeParameters: List<JvmTypeParameterDeclaration>
    get() {
        return (MethodSignature.of(this) as? MethodResolutionImpl)?.typeVariables ?: emptyList()
    }

private val classParamsCache = CacheBuilder.newBuilder()
    .maximumSize(1_000)
    .expireAfterAccess(Duration.ofSeconds(10))
    .build<JIRClassOrInterface, PersistentMap<String, JvmTypeParameterDeclaration>>()

fun JIRClassOrInterface.directTypeParameters(): List<JvmTypeParameterDeclaration> {
    val declaredSymbols = typeParameters.map { it.symbol }.toHashSet()
    return allVisibleTypeParameters().filterKeys { declaredSymbols.contains(it) }.values.toList()
}

/**
 * returns all visible declaration without JvmTypeParameterDeclaration#declaration
 */
fun JIRClassOrInterface.allVisibleTypeParameters(): Map<String, JvmTypeParameterDeclaration> {
    val result = classParamsCache.getIfPresent(this)
    if (result != null) {
        return result
    }
    val direct = typeParameters.associateBy { it.symbol }
    if (!isStatic) {
        val fromOuter = outerClass?.allVisibleTypeParameters()
        val fromMethod = outerMethod?.allVisibleTypeParameters()
        val res = (direct + (fromMethod ?: fromOuter).orEmpty()).toPersistentMap()
        classParamsCache.put(this, res)
        return res
    }
    return direct.also {
        classParamsCache.put(this, it.toPersistentMap())
    }
}

fun JIRMethod.allVisibleTypeParameters(): Map<String, JvmTypeParameterDeclaration> {
    return typeParameters.associateBy { it.symbol } + enclosingClass.allVisibleTypeParameters().takeIf { !isStatic }
        .orEmpty()
}

fun JvmTypeParameterDeclaration.asJcDeclaration(owner: JIRAccessible): JIRTypeVariableDeclaration {
    val classpath = when (owner) {
        is JIRClassOrInterface -> owner.classpath
        is JIRMethod -> owner.enclosingClass.classpath
        else -> throw IllegalStateException("Unknown owner type $owner")
    }
    val bounds = bounds?.map { classpath.typeOf(it) as JIRRefType }
    return JIRTypeVariableDeclarationImpl(symbol, bounds.orEmpty(), owner = owner)
}