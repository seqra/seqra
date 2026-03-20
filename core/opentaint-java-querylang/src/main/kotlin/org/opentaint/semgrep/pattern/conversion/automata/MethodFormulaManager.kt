package org.opentaint.semgrep.pattern.conversion.automata

import org.opentaint.semgrep.pattern.conversion.automata.MethodFormula.And
import org.opentaint.semgrep.pattern.conversion.automata.MethodFormula.False
import org.opentaint.semgrep.pattern.conversion.automata.MethodFormula.Or
import org.opentaint.semgrep.pattern.conversion.automata.MethodFormula.True

class MethodFormulaManager(initialPredicates: List<Predicate> = emptyList()) {
    private val predicateIds = hashMapOf<Predicate, Int>().also {
        initialPredicates.forEachIndexed { index, predicate ->
            it[predicate] = index
        }
    }

    private val predicates = arrayListOf<Predicate>().also { it.addAll(initialPredicates) }

    val allPredicateIds: List<PredicateId>
        get() = (1..predicates.size).toList()

    val allPredicates: List<Predicate>
        get() = predicates.toList()

    fun predicateId(predicate: Predicate): PredicateId = predicateIds.getOrPut(predicate) {
        val id = predicates.size
        predicates.add(predicate)
        id + 1
    }

    fun predicate(predicateId: PredicateId): Predicate {
        return predicates[predicateId - 1]
    }

    fun mkCube(cube: MethodFormulaCubeCompact): MethodFormula {
        if (cube.isEmpty) return True
        return MethodFormula.Cube(cube, negated = false)
    }

    fun mkAnd(all: List<MethodFormula>): MethodFormula = when (all.size) {
        0 -> True
        1 -> all.single()
        else -> And(all.toTypedArray())
    }

    fun mkOr(any: List<MethodFormula>): MethodFormula = when (any.size) {
        0 -> False
        1 -> any.single()
        else -> Or(any.toTypedArray())
    }
}
