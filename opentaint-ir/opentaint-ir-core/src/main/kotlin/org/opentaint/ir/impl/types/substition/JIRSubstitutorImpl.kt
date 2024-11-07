package org.opentaint.ir.impl.types.substition

import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toPersistentMap
import org.opentaint.ir.impl.types.signature.JvmType
import org.opentaint.ir.impl.types.signature.JvmTypeParameterDeclaration
import org.opentaint.ir.impl.types.signature.JvmTypeParameterDeclarationImpl
import org.opentaint.ir.impl.types.signature.JvmTypeVariable

class JIRSubstitutorImpl(
    // map declaration -> actual type or type variable
    override val substitutions: PersistentMap<JvmTypeParameterDeclaration, JvmType> = persistentMapOf()
) : JIRSubstitutor {

    private val substitutionTypeVisitor = object : JvmTypeVisitor {

        override fun visitUnprocessedTypeVariable(type: JvmTypeVariable, context: VisitorContext): JvmType {
            val direct = substitutions.firstNotNullOfOrNull { if (it.key.symbol == type.symbol) it.value else null }
            if (direct != null) {
                return direct
            }
            return type.declaration?.let {
                JvmTypeVariable(visitDeclaration(it, context))
            } ?: type
        }
    }

    override fun substitution(typeParameter: JvmTypeParameterDeclaration): JvmType? {
        return substitutions[typeParameter]
    }

    override fun substitute(type: JvmType): JvmType {
        return substitutionTypeVisitor.visitType(type)
    }

    override fun newScope(declarations: List<JvmTypeParameterDeclaration>): JIRSubstitutor {
        if (declarations.isEmpty()) {
            return this
        }
        val incomingSymbols = declarations.map { it.symbol }.toSet() // incoming symbols may override current
        val filtered = substitutions.filterNot { incomingSymbols.contains(it.key.symbol) }
        return JIRSubstitutorImpl(
            (filtered + declarations.associateWith {
                JvmTypeVariable(substitute(it, incomingSymbols))
            }).toPersistentMap()
        )
    }

    override fun fork(explicit: Map<JvmTypeParameterDeclaration, JvmType>): JIRSubstitutor {
        val incomingSymbols = explicit.keys.map { it.symbol }.toSet() // incoming symbols may override current
        val forked = explicit.map {
            substitute(it.key, incomingSymbols) to substitute(it.value)
        }.toMap().toPersistentMap()
        return JIRSubstitutorImpl(forked)
    }

    private fun substitute(
        declaration: JvmTypeParameterDeclaration,
        ignoredSymbols: Set<String>
    ): JvmTypeParameterDeclaration {
        val visitor = object : JvmTypeVisitor {

            override fun visitUnprocessedTypeVariable(type: JvmTypeVariable, context: VisitorContext): JvmType {
                if (ignoredSymbols.contains(type.symbol)) {
                    return type
                }
                return substitutions.firstNotNullOfOrNull { if (it.key.symbol == type.symbol) it.value else null }
                    ?: type
            }
        }
        return JvmTypeParameterDeclarationImpl(
            declaration.symbol,
            declaration.owner,
            declaration.bounds?.map { visitor.visitType(it) })
    }

}
