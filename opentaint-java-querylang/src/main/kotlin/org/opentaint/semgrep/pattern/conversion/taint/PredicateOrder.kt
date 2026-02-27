package org.opentaint.semgrep.pattern.conversion.taint

import org.opentaint.dataflow.util.copy
import org.opentaint.dataflow.util.toSet
import org.opentaint.semgrep.pattern.conversion.automata.MethodFormulaManager
import org.opentaint.semgrep.pattern.conversion.automata.ParamConstraint
import org.opentaint.semgrep.pattern.conversion.automata.Position
import org.opentaint.semgrep.pattern.conversion.automata.Predicate
import org.opentaint.semgrep.pattern.conversion.automata.PredicateId
import org.opentaint.semgrep.pattern.conversion.taint.DecisionVarSelector
import java.util.BitSet

class SemanticPredicateOrderer(
    private val formulaManager: MethodFormulaManager
) {
    private val oldPredicateIdToStablePredicateId: IntArray

    private fun comparePredicates(lhsPredicateId: PredicateId, rhsPredicateId: PredicateId): Int {
        val lhsPredicate = formulaManager.predicate(lhsPredicateId)
        val rhsPredicate = formulaManager.predicate(rhsPredicateId)

        return lhsPredicate.toString().compareTo(rhsPredicate.toString())
    }

    init {
        val allPredicateIds = formulaManager.allPredicateIds

        oldPredicateIdToStablePredicateId = IntArray((allPredicateIds.maxOrNull() ?: 0) + 1)
        val sortedPredicates = allPredicateIds.sortedWith(::comparePredicates)

        sortedPredicates.forEachIndexed { index, oldPredicateId ->
            oldPredicateIdToStablePredicateId[oldPredicateId] = index + 1
        }
    }

    fun stablePredicateId(predicateId: PredicateId): PredicateId {
        return oldPredicateIdToStablePredicateId[predicateId]
    }
}

class FormulaManagerAwareDecisionVarSelector private constructor(
    private val formulaManager: MethodFormulaManager,
    private val orderer: SemanticPredicateOrderer,
    private val used: BitSet
) : DecisionVarSelector {
    constructor(formulaManager: MethodFormulaManager): this(
        formulaManager = formulaManager,
        orderer = SemanticPredicateOrderer(formulaManager),
        used = BitSet()
    )

    private val Predicate.isPredicateOnResult: Boolean
        get() {
            return (constraint as? ParamConstraint)?.position is Position.Result
        }

    override fun nextDecisionVar(options: BitSet): Pair<Int, DecisionVarSelector>? {
        val newOptions = options.copy()
        newOptions.andNot(used)

        val nextVar = newOptions.toSet().minWithOrNull { lhs, rhs ->
            val lhsPred = formulaManager.predicate(lhs)
            val rhsPred = formulaManager.predicate(rhs)

            if (lhsPred.isPredicateOnResult != rhsPred.isPredicateOnResult) {
                return@minWithOrNull -1 * lhsPred.isPredicateOnResult.compareTo(rhsPred.isPredicateOnResult)
            }

            orderer.stablePredicateId(lhs) - orderer.stablePredicateId(rhs)
        } ?: return null

        val newUsed = used.copy()
        newUsed.set(nextVar)

        return nextVar to FormulaManagerAwareDecisionVarSelector(formulaManager, orderer, newUsed)
    }
}
