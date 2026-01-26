package org.opentaint.semgrep.pattern.conversion

import org.opentaint.semgrep.pattern.conversion.SemgrepPatternAction.SignatureModifier

sealed interface TypeNamePattern {
    data class FullyQualified(val name: String) : TypeNamePattern {
        override fun toString(): String = name
    }

    data class ClassName(val name: String) : TypeNamePattern {
        override fun toString(): String = "*.$name"
    }

    data class PrimitiveName(val name: String) : TypeNamePattern{
        override fun toString(): String = name
    }

    data class MetaVar(val metaVar: String) : TypeNamePattern {
        override fun toString(): String = metaVar
    }

    data object AnyType : TypeNamePattern {
        override fun toString(): String = "*"
    }

    data class ArrayType(val element: TypeNamePattern) : TypeNamePattern {
        override fun toString(): String = "${element}[]"
    }
}

sealed interface ParamPosition {
    data class Concrete(val idx: Int) : ParamPosition
    data class Any(val paramClassifier: String) : ParamPosition
}

sealed interface ParamCondition {
    data class And(val conditions: List<ParamCondition>) : ParamCondition

    data object True : ParamCondition

    sealed interface Atom : ParamCondition

    data class TypeIs(val typeName: TypeNamePattern) : Atom

    data object AnyStringLiteral : Atom

    data class StringValueMetaVar(val metaVar: MetavarAtom) : Atom

    data class ParamModifier(val modifier: SignatureModifier): Atom

    data class SpecificStaticFieldValue(val fieldName: String, val fieldClass: TypeNamePattern) : Atom
}

data class SpecificBoolValue(val value: Boolean) : ParamCondition.Atom

data class SpecificStringValue(val value: String) : ParamCondition.Atom

data class IsMetavar(val metavar: MetavarAtom) : ParamCondition.Atom

sealed interface MetavarAtom {
    val basics: Set<Basic>

    data class Basic(val name: String): MetavarAtom {
        override fun toString(): String = name

        override val basics: Set<Basic> get() = setOf(this)

        val isArtificial: Boolean get() = name.startsWith(ArtificialMetaVarName)
    }

    data class Complex(override val basics: Set<Basic>): MetavarAtom {
        override fun toString(): String {
            return basics
                .sortedBy { it.name }
                .joinToString("&")
        }
    }

    companion object {
        fun create(metavar: String): Basic {
            return Basic(metavar)
        }

        private const val ArtificialMetaVarName = "\$<ARTIFICIAL>"

        fun createArtificial(classifier: String): MetavarAtom =
            create("${ArtificialMetaVarName}_$classifier")

        fun create(metavars: Collection<Basic>): MetavarAtom {
            if (metavars.isEmpty()) {
                error("Unexpected empty collection of metavars")
            }

            val distinct = metavars.toSet()
            if (distinct.size == 1) {
                return distinct.single()
            }
            return Complex(distinct)
        }
    }
}

fun ParamCondition.collectMetavarTo(dst: MutableSet<MetavarAtom>) {
    when (this) {
        is ParamCondition.And -> conditions.forEach { it.collectMetavarTo(dst) }
        is IsMetavar -> dst.add(metavar)
        else -> {
            // no metavars
        }
    }
}

fun mkAnd(conditions: Set<ParamCondition>): ParamCondition = when (conditions.size) {
    0 -> ParamCondition.True
    1 -> conditions.first()
    else -> ParamCondition.And(conditions.toList())
}

data class ParamPattern(val position: ParamPosition, val condition: ParamCondition)

sealed interface ParamConstraint {
    val conditions: List<ParamCondition>

    data class Concrete(val params: List<ParamCondition>) : ParamConstraint {
        override val conditions: List<ParamCondition> get() = params
    }

    data class Partial(val params: List<ParamPattern>) : ParamConstraint {
        override val conditions: List<ParamCondition> get() = params.map { it.condition }
    }
}
