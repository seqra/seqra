package org.opentaint.ir.impl.types

import org.opentaint.ir.api.JIRAccessible
import org.opentaint.ir.api.JIRClassOrInterface
import org.opentaint.ir.api.JIRMethod
import org.opentaint.ir.api.JIRRefType
import org.opentaint.ir.api.JIRTypeVariableDeclaration
import org.opentaint.ir.impl.types.signature.JvmTypeParameterDeclaration
import org.opentaint.ir.impl.types.signature.TypeResolutionImpl
import org.opentaint.ir.impl.types.signature.TypeSignature

val JIRClassOrInterface.typeParameters: List<JvmTypeParameterDeclaration>
    get() {
        return (TypeSignature.of(this) as? TypeResolutionImpl)?.typeVariables ?: emptyList()
    }

suspend fun JvmTypeParameterDeclaration.asJcDeclaration(owner: JIRAccessible): JIRTypeVariableDeclaration {
    val classpath = when (owner) {
        is JIRClassOrInterface -> owner.classpath
        is JIRMethod -> owner.enclosingClass.classpath
        else -> throw IllegalStateException("Unknown owner type $owner")
    }
    val bounds = bounds?.map { classpath.typeOf(it) as JIRRefType }
    return JIRTypeVariableDeclarationImpl(symbol, bounds.orEmpty(), owner = owner)
}