package org.opentaint.ir.impl.types

import org.opentaint.ir.api.JIRClasspath
import org.opentaint.ir.api.JIRRefType
import org.opentaint.ir.api.JIRType
import org.opentaint.ir.api.JIRTypeVariableDeclaration
import org.opentaint.ir.api.PredefinedPrimitives
import org.opentaint.ir.api.ext.findClass
import org.opentaint.ir.impl.signature.Formal
import org.opentaint.ir.impl.signature.FormalTypeVariable
import org.opentaint.ir.impl.signature.SArrayType
import org.opentaint.ir.impl.signature.SBoundWildcard
import org.opentaint.ir.impl.signature.SClassRefType
import org.opentaint.ir.impl.signature.SParameterizedType
import org.opentaint.ir.impl.signature.SPrimitiveType
import org.opentaint.ir.impl.signature.SResolvedTypeVariable
import org.opentaint.ir.impl.signature.SType
import org.opentaint.ir.impl.signature.STypeVariable
import org.opentaint.ir.impl.signature.SUnboundWildcard

class JIRTypeBindings(
    incoming: Map<String, SType>,
    private val declarations: Map<String, FormalTypeVariable>
) {
    companion object {
        val empty = JIRTypeBindings(emptyMap(), emptyMap())
    }

    internal val bindings = incoming.filterValues { it !is STypeVariable }

    fun override(overrides: List<FormalTypeVariable>): JIRTypeBindings {
        val newDeclarations = declarations + overrides.associateBy { it.symbol }
        val newSymbols = overrides.map { it.symbol }.toSet()
        val newBindings = bindings.filterKeys { !newSymbols.contains(it) }
        return JIRTypeBindings(newBindings, newDeclarations)
    }

    fun findDirectBinding(symbol: String): SType? {
        return bindings[symbol]
    }

    fun resolve(symbol: String): SResolvedTypeVariable {
        val bounds = declarations[symbol]?.boundTypeTokens?.map { it.applyTypeDeclarations(this, null) }
        return SResolvedTypeVariable(symbol, bounds.orEmpty())
    }

    suspend fun toJcRefType(stype: SType, classpath: JIRClasspath): JIRRefType {
        return classpath.typeOf(stype.apply(this, null), this) as JIRRefType
//        val bindings = this
//
//        suspend fun SType.toJcRefType(): JIRRefType {
//            return classpath.typeOf(this, bindings) as JIRRefType
//        }

//        if (stype is STypeVariable) {
//            val symbol = stype.symbol
//            val direct = findDirectBinding(symbol)
//            if (direct != null) {
//                return direct.toJcRefType()
//            }
//            val resolved = resolve(symbol)
//            return JIRTypeVariableImpl(
//                classpath,
//                JIRTypeVariableDeclarationImpl(
//                    symbol,
//                    resolved.boundTypeTokens?.map { it.toJcRefType() }.orEmpty()
//                ), true
//            )
//        }
//        return stype.apply(bindings, null).toJcRefType()

    }
}

internal suspend fun JIRClasspath.typeOf(stype: SType, bindings: JIRTypeBindings): JIRType {
    return when (stype) {
        is SPrimitiveType -> {
            PredefinedPrimitives.of(stype.ref, this)
                ?: throw IllegalStateException("primitive type ${stype.ref} not found")
        }

        is SClassRefType -> typeOf(findClass(stype.name))
        is SArrayType -> arrayTypeOf(typeOf(stype.elementType, bindings))
        is SParameterizedType -> {
            val clazz = findClass(stype.name)
            JIRClassTypeImpl(
                clazz,
                parametrization = stype.parameterTypes,
                nullable = true
            )
        }

        is SResolvedTypeVariable -> {
            val resolved = stype.boundaries.map { typeOf(it, bindings) as JIRRefType }
            JIRTypeVariableImpl(this, JIRTypeVariableDeclarationImpl(stype.symbol, resolved), true)
        }
        is STypeVariable -> {
            JIRTypeVariableImpl(this, JIRTypeVariableDeclarationImpl(stype.symbol, emptyList()), true)
        }

        is SUnboundWildcard -> JIRUnboundWildcardImpl(this)
        is SBoundWildcard.SUpperBoundWildcard -> JIRUpperBoundWildcardImpl(
            typeOf(
                stype.bound,
                bindings
            ) as JIRRefType, true
        )

        is SBoundWildcard.SLowerBoundWildcard -> JIRLowerBoundWildcardImpl(
            typeOf(
                stype.bound,
                bindings
            ) as JIRRefType, true
        )

        else -> throw IllegalStateException("unknown type")
    }
}

class JIRTypeVariableDeclarationImpl(
    override val symbol: String,
    override val bounds: List<JIRRefType>
) : JIRTypeVariableDeclaration

internal suspend fun JIRClasspath.typeDeclaration(
    formal: FormalTypeVariable,
    bindings: JIRTypeBindings
): JIRTypeVariableDeclaration {
    return when (formal) {
        is Formal -> JIRTypeVariableDeclarationImpl(
            formal.symbol,
            formal.boundTypeTokens?.map { typeOf(it, bindings) as JIRRefType }.orEmpty()
        )

        else -> throw IllegalStateException("Unknown type $formal")
    }
}

internal suspend fun JIRClasspath.typeDeclarations(
    formals: List<FormalTypeVariable>,
    bindings: JIRTypeBindings
): List<JIRTypeVariableDeclaration> {
    return formals.map { typeDeclaration(it, bindings) }
}