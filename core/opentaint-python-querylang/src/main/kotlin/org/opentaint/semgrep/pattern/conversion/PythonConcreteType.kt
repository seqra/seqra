package org.opentaint.semgrep.pattern.conversion

import org.opentaint.semgrep.pattern.MetaVarConstraints
import org.opentaint.semgrep.pattern.ResolvedMetaVarInfo

sealed interface PythonConcreteType : LanguageConcreteType {
    data class Named(val name: String) : PythonConcreteType { override fun toString() = name }
}

fun pythonNamed(name: String) = TypeConstraint.Concrete(PythonConcreteType.Named(name))

object PythonTypeOps : LanguageTypeOps {
    override fun unifyConcrete(
        left: LanguageConcreteType,
        right: LanguageConcreteType,
        metaVarInfo: ResolvedMetaVarInfo,
    ): TypeConstraint? = null

    override fun metavarsOf(type: LanguageConcreteType): Set<String> = emptySet()

    override fun concreteMatchesMetaVarConstraint(
        type: LanguageConcreteType,
        constraints: MetaVarConstraints?,
    ): Boolean = constraints == null
}
