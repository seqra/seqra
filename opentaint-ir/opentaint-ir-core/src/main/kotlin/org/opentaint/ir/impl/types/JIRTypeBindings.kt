package org.opentaint.ir.impl.types

import org.opentaint.ir.api.JIRClassOrInterface
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
import org.opentaint.ir.impl.signature.TypeResolutionImpl
import org.opentaint.ir.impl.signature.TypeSignature

class JIRTypeBindings(
    internal val parametrization: List<SType>? = null,
    bindings: Map<String, SType>,
    private val declarations: Map<String, FormalTypeVariable>,
    private val parent: JIRTypeBindings? = null
) {
    companion object {

        val empty = JIRTypeBindings(null, emptyMap(), emptyMap(), null)

        fun ofClass(
            jirClass: JIRClassOrInterface,
            parent: JIRTypeBindings?,
            parametrization: List<SType>? = null
        ): JIRTypeBindings {
            val resolution = TypeSignature.of(jirClass.signature)
            if (parametrization != null && resolution is TypeResolutionImpl && resolution.typeVariables.size != parametrization.size) {
                val msg = "Expected ${resolution.typeVariables.joinToString()} but " +
                        "was ${parametrization.joinToString()}"
                throw IllegalStateException(msg)
            }

            val resolutionImpl = resolution as? TypeResolutionImpl

            val bindings = resolutionImpl?.typeVariables?.mapIndexed { index, declaration ->
                declaration.symbol to (parametrization?.get(index) ?: STypeVariable(declaration.symbol))
            }?.toMap() ?: emptyMap()

            val declarations = resolutionImpl?.let {
                it.typeVariables.associateBy { it.symbol }
            } ?: emptyMap()
            return parent?.override(JIRTypeBindings(parametrization, bindings, declarations), true)
                ?: JIRTypeBindings(parametrization, bindings, declarations)
        }
    }

    internal val typeBindings = bindings.filterValues { it !is STypeVariable }

    fun override(overrides: JIRTypeBindings, join: Boolean = false): JIRTypeBindings {
        return JIRTypeBindings(
            overrides.parametrization,
            overrides.typeBindings,
            overrides.declarations,
            takeIf { join })
    }

    fun override(typeVariables: List<FormalTypeVariable>): JIRTypeBindings {
        return JIRTypeBindings(
            null,
            emptyMap(),
            typeVariables.associateBy { it.symbol },
            this
        )
    }

    fun findTypeBinding(symbol: String): SType? {
        return typeBindings[symbol] ?: parent?.findTypeBinding(symbol)
    }

    fun resolve(symbol: String): SResolvedTypeVariable {
        val typeVariable = declarations[symbol]
        if (typeVariable == null && parent != null) {
            return parent.resolve(symbol)
        }
        val bounds = typeVariable?.boundTypeTokens?.map {
            it.applyTypeDeclarations(this, null)
        }
        return SResolvedTypeVariable(symbol, bounds.orEmpty())
    }

    suspend fun toJcRefType(stype: SType, classpath: JIRClasspath): JIRRefType {
        return classpath.typeOf(stype.apply(this, null), this) as JIRRefType
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
                null,
                JIRTypeBindings.ofClass(clazz, bindings, stype.parameterTypes),
                nullable = true
            )
        }

        is SParameterizedType.SNestedType -> {
            val clazz = findClass(stype.name)
            val outerType = typeOf(stype.ownerType, bindings)
            val outerParameters = (stype.ownerType as? SParameterizedType)?.parameterTypes
            JIRClassTypeImpl(
                clazz,
                outerType as JIRClassTypeImpl,
                JIRTypeBindings.ofClass(clazz, bindings, outerParameters),
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
        is SBoundWildcard.SUpperBoundWildcard -> typeOf(stype.bound, bindings)

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
            formal.boundTypeTokens?.map { typeOf(it.applyKnownBindings(bindings), bindings) as JIRRefType }.orEmpty()
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