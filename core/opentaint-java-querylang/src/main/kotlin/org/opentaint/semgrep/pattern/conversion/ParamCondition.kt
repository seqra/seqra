package org.opentaint.semgrep.pattern.conversion

import kotlinx.serialization.Serializable
import org.opentaint.semgrep.pattern.conversion.SemgrepPatternAction.SignatureModifier

@Serializable
sealed interface TypeNamePattern {
    @Serializable
    data class FullyQualified(val name: String, val typeArgs: List<TypeNamePattern> = emptyList()) : TypeNamePattern {
        override fun toString(): String = if (typeArgs.isEmpty()) name else "$name<${typeArgs.joinToString(", ")}>"
    }

    @Serializable
    data class ClassName(val name: String, val typeArgs: List<TypeNamePattern> = emptyList()) : TypeNamePattern {
        override fun toString(): String = if (typeArgs.isEmpty()) "*.$name" else "*.$name<${typeArgs.joinToString(", ")}>"
    }

    @Serializable
    data class PrimitiveName(val name: String) : TypeNamePattern{
        override fun toString(): String = name
    }

    @Serializable
    data class MetaVar(val metaVar: String) : TypeNamePattern {
        override fun toString(): String = metaVar
    }

    @Serializable
    data object AnyType : TypeNamePattern {
        override fun toString(): String = "*"
    }

    /**
     * Java unbounded wildcard `?` as a type argument. Java's `?` is the
     * supertype of any concrete parameterization, so a `Foo<?>` pattern
     * accepts any `Foo<X>` — semantically equivalent to [AnyType] at a
     * type-argument slot.
     */
    @Serializable
    data object WildcardType : TypeNamePattern {
        override fun toString(): String = "?"
    }

    @Serializable
    data class ArrayType(val element: TypeNamePattern) : TypeNamePattern {
        override fun toString(): String = "${element}[]"
    }
}

sealed interface ParamPosition {
    data class Concrete(val idx: Int) : ParamPosition
    data class Any(val paramClassifier: String) : ParamPosition
}

@Serializable
sealed interface ParamCondition {
    @Serializable
    data class And(val conditions: List<ParamCondition>) : ParamCondition

    @Serializable
    data object True : ParamCondition

    @Serializable
    sealed interface Atom : ParamCondition

    @Serializable
    data class TypeIs(val typeName: TypeNamePattern) : Atom

    @Serializable
    data object AnyStringLiteral : Atom

    @Serializable
    data class StringValueMetaVar(val metaVar: MetavarAtom) : Atom

    @Serializable
    data class ParamModifier(val modifier: SignatureModifier): Atom

    @Serializable
    data class SpecificStaticFieldValue(val fieldName: String, val fieldClass: TypeNamePattern) : Atom
}

@Serializable
sealed interface SpecificConstantValue: ParamCondition.Atom

@Serializable
data class SpecificBoolValue(val value: Boolean) : SpecificConstantValue
@Serializable
data class SpecificIntValue(val value: Int) : SpecificConstantValue
@Serializable
data class SpecificStringValue(val value: String) : SpecificConstantValue
@Serializable
data object SpecificNullValue : SpecificConstantValue

@Serializable
data class IsMetavar(val metavar: MetavarAtom) : ParamCondition.Atom

@Serializable
sealed interface MetavarAtom {
    val basics: Set<Basic>

    @Serializable
    data class Basic(val name: String): MetavarAtom {
        override fun toString(): String = name

        override val basics: Set<Basic> get() = setOf(this)

        val isArtificial: Boolean get() = name.startsWith(ArtificialMetaVarName)
    }

    @Serializable
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
