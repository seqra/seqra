package org.opentaint.org.opentaint.semgrep.pattern.conversion.automata

sealed interface MethodFormula {

    fun complement(): MethodFormula

    data class Or(@JvmField val any: Array<MethodFormula>) : MethodFormula {
        override fun complement(): MethodFormula = And(
            Array(any.size) { any[it].complement() }
        )

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is Or) return false
            return any.contentEquals(other.any)
        }

        override fun hashCode(): Int = any.contentHashCode()
    }

    data class And(@JvmField val all: Array<MethodFormula>) : MethodFormula {
        override fun complement(): MethodFormula = Or(
            Array(all.size) { all[it].complement() }
        )

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is And) return false
            return all.contentEquals(other.all)
        }

        override fun hashCode(): Int = all.contentHashCode()
    }

    data class Literal(@JvmField val predicate: PredicateId, @JvmField val negated: Boolean) : MethodFormula {
        override fun complement() = copy(negated = !negated)
    }

    data class Cube(@JvmField val cube: MethodFormulaCubeCompact, @JvmField val negated: Boolean) : MethodFormula {
        override fun complement(): MethodFormula = copy(negated = !negated)
    }

    data object True : MethodFormula {
        override fun complement(): MethodFormula = False
    }

    data object False : MethodFormula {
        override fun complement(): MethodFormula = True
    }
}
