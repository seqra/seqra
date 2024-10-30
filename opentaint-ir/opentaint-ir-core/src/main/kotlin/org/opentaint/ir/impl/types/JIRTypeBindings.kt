package org.opentaint.ir.impl.types

import org.opentaint.ir.api.JIRClasspath
import org.opentaint.ir.api.JIRRefType
import org.opentaint.ir.api.JIRType
import org.opentaint.ir.api.JIRTypeVariableDeclaration
import org.opentaint.ir.api.PredefinedPrimitives
import org.opentaint.ir.api.anyType
import org.opentaint.ir.api.ext.findClass
import org.opentaint.ir.impl.signature.Formal
import org.opentaint.ir.impl.signature.FormalTypeVariable
import org.opentaint.ir.impl.signature.SArrayType
import org.opentaint.ir.impl.signature.SBoundWildcard
import org.opentaint.ir.impl.signature.SClassRefType
import org.opentaint.ir.impl.signature.SParameterizedType
import org.opentaint.ir.impl.signature.SPrimitiveType
import org.opentaint.ir.impl.signature.SType
import org.opentaint.ir.impl.signature.STypeVariable
import org.opentaint.ir.impl.signature.SUnboundWildcard

class JIRTypeBindings(internal val bindings: Map<String, SType> = emptyMap()) {

    fun override(incoming: Set<String>): JIRTypeBindings {
        return JIRTypeBindings(bindings.filterKeys { !incoming.contains(it) })
    }
}

internal suspend fun JIRClasspath.typeOf(stype: SType): JIRType {
    return when (stype) {
        is SPrimitiveType -> {
            PredefinedPrimitives.of(stype.ref, this)
                ?: throw IllegalStateException("primitive type ${stype.ref} not found")
        }

        is SClassRefType -> typeOf(findClass(stype.name))
        is SArrayType -> arrayTypeOf(typeOf(stype.elementType))
        is SParameterizedType -> {
            val clazz = findClass(stype.name)
            JIRClassTypeImpl(
                clazz,
                parametrization = stype.parameterTypes,
                nullable = true
            )
        }

        is STypeVariable -> JIRTypeVariableImpl(stype.symbol, true, anyType())
        is SUnboundWildcard -> JIRUnboundWildcardImpl(anyType())
        is SBoundWildcard.SUpperBoundWildcard -> JIRUpperBoundWildcardImpl(typeOf(stype.boundType) as JIRRefType, true)
        is SBoundWildcard.SLowerBoundWildcard -> JIRLowerBoundWildcardImpl(typeOf(stype.boundType) as JIRRefType, true)
        else -> throw IllegalStateException("unknown type")
    }
}

class JIRTypeVariableDeclarationImpl(
    override val symbol: String,
    override val bounds: List<JIRRefType>
) : JIRTypeVariableDeclaration

internal suspend fun JIRClasspath.typeDeclaration(formal: FormalTypeVariable): JIRTypeVariableDeclaration {
    return when (formal) {
        is Formal -> JIRTypeVariableDeclarationImpl(
            formal.symbol,
            formal.boundTypeTokens?.map { typeOf(it) as JIRRefType }.orEmpty()
        )

        else -> throw IllegalStateException("Unknown type $formal")
    }
}

internal suspend fun JIRClasspath.typeDeclarations(formals: List<FormalTypeVariable>): List<JIRTypeVariableDeclaration> {
    return formals.map { typeDeclaration(it) }
}