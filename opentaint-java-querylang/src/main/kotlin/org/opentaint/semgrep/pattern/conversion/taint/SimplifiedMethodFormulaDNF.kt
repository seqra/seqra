package org.opentaint.semgrep.pattern.conversion.taint

import org.opentaint.dataflow.util.containsAll
import org.opentaint.dataflow.util.copy
import org.opentaint.dataflow.util.forEach
import org.opentaint.dataflow.util.removeFirst
import org.opentaint.semgrep.pattern.conversion.automata.FalseValue
import org.opentaint.semgrep.pattern.conversion.automata.MethodFormula
import org.opentaint.semgrep.pattern.conversion.automata.MethodFormulaCubeCompact
import org.opentaint.semgrep.pattern.conversion.automata.TrueValue
import org.opentaint.semgrep.pattern.conversion.automata.UnknownValue
import org.opentaint.semgrep.pattern.conversion.automata.VariableValue
import org.opentaint.semgrep.pattern.conversion.automata.isFalse
import org.opentaint.semgrep.pattern.conversion.automata.isTrue
import org.opentaint.semgrep.pattern.conversion.automata.isUnknown
import org.opentaint.semgrep.pattern.conversion.automata.negated
import java.util.BitSet

fun methodFormulaDNF(formula: MethodFormula): List<MethodFormulaCubeCompact> {
    return methodFormulaModels(formula)
}

fun methodFormulaCheckSat(
    formula: MethodFormula,
    verifyModel: (MethodFormulaCubeCompact) -> Boolean
): Boolean {
    methodFormulaModels(formula) { model ->
        if (verifyModel(model)) return true
    }
    return false
}

private class ModelStorage {
    val models = hashMapOf<BitSet, MutableList<MethodFormulaCubeCompact>>()

    val isEmpty: Boolean get() = models.isEmpty()

    inline fun forEachWeakerModelGroup(usedVars: BitSet, body: (MutableList<MethodFormulaCubeCompact>) -> Unit) =
        forEachModelGroup({ it == usedVars || !usedVars.containsAll(it) }, body)

    inline fun forEachStrongerModelGroup(usedVars: BitSet, body: (MutableList<MethodFormulaCubeCompact>) -> Unit) =
        forEachModelGroup({ it == usedVars || !it.containsAll(usedVars) }, body)

    inline fun forEachModel(body: (MethodFormulaCubeCompact) -> Unit) =
        forEachModelGroup(skipGroup = { false }) { group ->
            group.forEach(body)
        }

    inline fun <T> forEachModelGroup(
        skipGroup: (BitSet) -> Boolean,
        body: (MutableList<MethodFormulaCubeCompact>) -> T
    ) {
        val iter = models.iterator()
        while (iter.hasNext()) {
            val (vars, modelGroup) = iter.next()

            if (skipGroup(vars)) continue

            body(modelGroup)

            if (modelGroup.isEmpty()) {
                iter.remove()
            }
        }
    }
}

private class FormulaModels(val formula: MethodFormula) {
    val storage = ModelStorage()

    fun addModels(models: List<MethodFormulaCubeCompact>, startModel: MethodFormulaCubeCompact) {
        models.forEach { addModel(it, startModel) }
    }

    private fun addModel(model: MethodFormulaCubeCompact, startModel: MethodFormulaCubeCompact) {
        val usedVars = model.usedLitVars()
        mergeAddModelToGroupWithWeaknessCheck(model, usedVars, startModel)
    }

    private fun mergeAddModelToGroupWithWeaknessCheck(
        model: MethodFormulaCubeCompact, usedVars: BitSet,
        startModel: MethodFormulaCubeCompact
    ) {
        mergeAddModelToGroup(
            model, usedVars,
            addToCurrentGroup = { m, vars ->
                addModelToGroupWithWeaknessCheck(m, vars, startModel)
            },
            addToWeakerGroup = { m, vars ->
                mergeAddModelToGroupWithWeaknessCheck(m, vars, startModel)
            }
        )
    }

    private fun mergeAddModelToGroupNoWeaknessCheck(model: MethodFormulaCubeCompact, usedVars: BitSet) {
        mergeAddModelToGroup(
            model, usedVars,
            addToCurrentGroup = { m, vars ->
                addModelToGroup(m, vars)
            },
            addToWeakerGroup = { m, vars ->
                mergeAddModelToGroupNoWeaknessCheck(m, vars)
            }
        )
    }

    private inline fun mergeAddModelToGroup(
        model: MethodFormulaCubeCompact,
        usedVars: BitSet,
        addToCurrentGroup: (MethodFormulaCubeCompact, BitSet) -> Unit,
        addToWeakerGroup: (MethodFormulaCubeCompact, BitSet) -> Unit
    ) {
        val group = storage.models[usedVars]
        if (group == null) {
            addToCurrentGroup(model, usedVars)
            return
        }

        val iter = group.iterator()
        while (iter.hasNext()) {
            val m = iter.next()

            if (m == model) return

            val posLit = findSingleDifferentLiteral(m.positiveLiterals, model.positiveLiterals) ?: continue
            val negLit = findSingleDifferentLiteral(m.negativeLiterals, model.negativeLiterals) ?: continue

            if (posLit != negLit) continue

            iter.remove()

            usedVars.clear(posLit)
            model.positiveLiterals.clear(posLit)
            model.negativeLiterals.clear(posLit)

            addToWeakerGroup(model, usedVars)
            return
        }

        addToCurrentGroup(model, usedVars)
    }

    private fun addModelToGroupWithWeaknessCheck(
        model: MethodFormulaCubeCompact, usedVars: BitSet,
        startModel: MethodFormulaCubeCompact,
    ) {
        storage.forEachWeakerModelGroup(usedVars) { group ->
            group.forEach {
                // weaker model stored
                if (model.containsAll(it)) return
            }
        }

        val currentUsedVars = usedVars.copy()
        removeFreeVariables(formula, model, usedVars, startModel)

        if (currentUsedVars == usedVars) {
            addModelToGroup(model, usedVars)
        } else {
            mergeAddModelToGroupNoWeaknessCheck(model, usedVars)
        }
    }

    private fun addModelToGroup(model: MethodFormulaCubeCompact, usedVars: BitSet) {
        storage.models.getOrPut(usedVars, ::mutableListOf).add(model)

        storage.forEachStrongerModelGroup(usedVars) { group ->
            val iter = group.iterator()
            while (iter.hasNext()) {
                val m = iter.next()
                if (m.containsAll(model)) {
                    iter.remove()
                }
            }
        }
    }

    private fun findSingleDifferentLiteral(firstLitVars: BitSet, secondLitVars: BitSet): Int? {
        if (firstLitVars.cardinality() > secondLitVars.cardinality()) {
            return findSingleDifferentLiteral(secondLitVars, firstLitVars)
        }

        val diff = secondLitVars.copy()
        diff.andNot(firstLitVars)

        if (diff.isEmpty) return null

        val diffCandidate = diff.removeFirst()
        if (!diff.isEmpty) return null

        return diffCandidate
    }
}

fun methodFormulaModels(formula: MethodFormula) = methodFormulaModels(formula) {}

private inline fun methodFormulaModels(
    formula: MethodFormula,
    onEachModel: (MethodFormulaCubeCompact) -> Unit,
): List<MethodFormulaCubeCompact> {
    val models = FormulaModels(formula)

    val startModels = startModels(formula)
    for (startModel in startModels) {
        while (true) {
            var iterationFormula = formula

            if (!models.storage.isEmpty) {
                val conjuncts = mutableListOf(iterationFormula)
                models.storage.forEachModel { model ->
                    conjuncts += MethodFormula.Cube(model, negated = true)
                }
                iterationFormula = MethodFormula.And(conjuncts.toTypedArray())
            }

            val iterationModels = mutableListOf<MethodFormulaCubeCompact>()
            val status = deepSearchModel(decisionVar = 0, iterationFormula, startModel, iterationModels)

            if (!status) {
                break
            }

            iterationModels.forEach { onEachModel(it) }

            models.addModels(iterationModels, startModel)
        }
    }

    val result = mutableListOf<MethodFormulaCubeCompact>()
    models.storage.forEachModel { result.add(it) }
    return result
}

private fun startModels(formula: MethodFormula): List<MethodFormulaCubeCompact> {
    var candidateModels: List<MethodFormulaCubeCompact>? = null

    if (formula is MethodFormula.And) {
        for (subFormula in formula.all) {
            if (subFormula is MethodFormula.Or) {
                val args = subFormula.any
                val models = args.mapNotNull { (it as? MethodFormula.Cube)?.takeIf { !it.negated }?.cube }
                if (models.size == args.size) {
                    if (candidateModels == null || candidateModels.size > models.size) {
                        candidateModels = models
                    }
                }
            }
        }
    }

    return candidateModels ?: listOf(MethodFormulaCubeCompact())
}

private val emptyModel = MethodFormulaCubeCompact()

typealias SimplificationResult = Any?

private data class SimplifiedPartial(
    val formula: MethodFormula,
    val requiredModel: MethodFormulaCubeCompact,
    val requiredVars: BitSet
)

private inline fun <T> SimplificationResult.handle(
    failed: () -> T,
    simplifiedTrue: (MethodFormulaCubeCompact) -> T,
    partial: (SimplifiedPartial) -> T
): T {
    if (this == null) return failed()

    if (this is MethodFormulaCubeCompact) {
        return simplifiedTrue(this)
    }

    return partial(this as SimplifiedPartial)
}

@Suppress("NOTHING_TO_INLINE")
private inline fun simplificationFailed(): SimplificationResult = null

@Suppress("NOTHING_TO_INLINE")
private inline fun simplifiedTrue(model: MethodFormulaCubeCompact): SimplificationResult = model

private fun searchModel(
    decisionVar: Int,
    formula: MethodFormula,
    currentModel: MethodFormulaCubeCompact,
    resultModels: MutableList<MethodFormulaCubeCompact>
): Boolean {
    currentModel.positiveLiterals.set(decisionVar)
    val positiveSat = deepSearchModel(decisionVar, formula, currentModel, resultModels)
    currentModel.positiveLiterals.clear(decisionVar)

    currentModel.negativeLiterals.set(decisionVar)
    val negativeSat = deepSearchModel(decisionVar, formula, currentModel, resultModels)
    currentModel.negativeLiterals.clear(decisionVar)

    return positiveSat or negativeSat
}

private fun deepSearchModel(
    decisionVar: Int,
    formula: MethodFormula,
    currentModel: MethodFormulaCubeCompact,
    resultModels: MutableList<MethodFormulaCubeCompact>
): Boolean {
    val simplificationResult = simplifyWrtModel(formula, currentModel)
    return simplificationResult.handle(
        failed = { false },
        simplifiedTrue = { requiredModel ->
            val resultModel = currentModel.add(requiredModel)
            check(!resultModel.hasConflict()) { "Simplification failed" }

            resultModels.add(resultModel)
            true
        },
        partial = {
            val nextModel = currentModel.add(it.requiredModel)
            check(!nextModel.hasConflict()) { "Simplification failed" }

            val nextVar = it.requiredVars.nextSetBit(decisionVar + 1)
            check(nextVar > 0) { "Simplification failed" }

            searchModel(nextVar, it.formula, nextModel, resultModels)
        }
    )
}

private fun simplifyWrtModel(
    formula: MethodFormula,
    model: MethodFormulaCubeCompact
): SimplificationResult = when (formula) {
    MethodFormula.False -> simplificationFailed()
    MethodFormula.True -> simplifiedTrue(emptyModel)
    is MethodFormula.Cube -> simplifyCubeWrtModel(formula, model)
    is MethodFormula.Literal -> simplifyLiteralWrtModel(formula, model)
    is MethodFormula.And -> simplifyAndWrtModel(model, formula)
    is MethodFormula.Or -> simplifyOrWrtModel(formula, model)
}

private fun simplifyOrWrtModel(
    formula: MethodFormula.Or,
    model: MethodFormulaCubeCompact
): SimplificationResult {
    val trueBranches = mutableListOf<MethodFormulaCubeCompact>()
    val partialBranches = mutableListOf<SimplifiedPartial>()

    for (arg in formula.any) {
        val simplResult = simplifyWrtModel(arg, model)
        simplResult.handle(
            failed = { /*continue*/ },
            simplifiedTrue = { trueBranches.add(it) },
            partial = { partialBranches.add(it) },
        )
    }

    if (trueBranches.isEmpty() && partialBranches.isEmpty()) {
        return simplificationFailed()
    }

    var modelIntersection: MethodFormulaCubeCompact? = null

    for (branchModel in trueBranches) {
        branchModel.mutableRemove(model)

        if (branchModel.isEmpty) {
            return simplifiedTrue(emptyModel)
        }

        if (modelIntersection == null) {
            modelIntersection = branchModel.copy()
            continue
        }

        modelIntersection.mutableIntersect(branchModel)
    }

    if (partialBranches.isEmpty()) {
        check(modelIntersection != null)

        if (trueBranches.any { modelIntersection == it }) {
            return simplifiedTrue(modelIntersection)
        }
    }

    val unassignedVars = BitSet()

    val result = arrayOfNulls<MethodFormula>(trueBranches.size + partialBranches.size)
    var resultWritePtr = 0

    for (trueBranch in trueBranches) {
        unassignedVars.or(trueBranch.positiveLiterals)
        unassignedVars.or(trueBranch.negativeLiterals)

        result[resultWritePtr++] = MethodFormula.Cube(trueBranch, negated = false)
    }

    for (partial in partialBranches) {
        unassignedVars.or(partial.requiredVars)
        unassignedVars.or(partial.requiredModel.positiveLiterals)
        unassignedVars.or(partial.requiredModel.negativeLiterals)

        result[resultWritePtr++] = partial.formula

        if (modelIntersection == null) {
            modelIntersection = partial.requiredModel.copy()
            continue
        }

        modelIntersection.mutableIntersect(partial.requiredModel)
    }

    check(modelIntersection != null)
    unassignedVars.andNot(modelIntersection.positiveLiterals)
    unassignedVars.andNot(modelIntersection.negativeLiterals)

    if (unassignedVars.isEmpty) {
        error("simplification failed")
    }

    @Suppress("UNCHECKED_CAST")
    return SimplifiedPartial(
        MethodFormula.Or(result as Array<MethodFormula>),
        modelIntersection,
        unassignedVars
    )
}

private fun simplifyAndWrtModel(
    model: MethodFormulaCubeCompact,
    formula: MethodFormula.And
): SimplificationResult {
    val result = model.copy()
    val partialResults = mutableListOf<SimplifiedPartial>()

    for (arg in formula.all) {
        val simplResult = simplifyWrtModel(arg, model)
        simplResult.handle(
            failed = { return simplificationFailed() },
            simplifiedTrue = {
                result.mutableAdd(it)
                if (result.hasConflict()) return simplificationFailed()
            },
            partial = {
                result.mutableAdd(it.requiredModel)
                if (result.hasConflict()) return simplificationFailed()

                partialResults.add(it)
            }
        )
    }

    if (partialResults.isEmpty()) {
        result.mutableRemove(model)
        return simplifiedTrue(result)
    }

    result.mutableAdd(model)

    if (result.hasConflict()) {
        return simplificationFailed()
    }

    val unassignedVars = BitSet()

    val conjuncts = arrayOfNulls<MethodFormula>(partialResults.size + 1)
    for (i in partialResults.indices){
        val partialRes = partialResults[i]
        conjuncts[i] = partialRes.formula
        unassignedVars.or(partialRes.requiredVars)
    }

    unassignedVars.andNot(result.positiveLiterals)
    unassignedVars.andNot(result.negativeLiterals)

    if (unassignedVars.isEmpty) {
        for (arg in conjuncts) {
            if (arg == null) continue

            val simplResult = simplifyWrtModel(arg, result)
            simplResult.handle(
                failed = { return simplificationFailed() },
                simplifiedTrue = {
                    result.mutableAdd(it)
                    if (result.hasConflict()) return simplificationFailed()
                },
                partial = { error("Simplification failed") }
            )
        }

        result.mutableRemove(model)
        return simplifiedTrue(result)
    }

    result.mutableRemove(model)

    conjuncts[conjuncts.lastIndex] = MethodFormula.Cube(result.copy(), negated = false)

    @Suppress("UNCHECKED_CAST")
    return SimplifiedPartial(
        MethodFormula.And(conjuncts as Array<MethodFormula>),
        result, unassignedVars
    )
}

private fun simplifyLiteralWrtModel(
    formula: MethodFormula.Literal,
    model: MethodFormulaCubeCompact
): SimplificationResult {
    val litValue = evalLiteral(formula.predicate, formula.negated, model)

    if (litValue.isTrue) return simplifiedTrue(emptyModel)
    if (litValue.isFalse) return simplificationFailed()

    return simplifiedTrue(MethodFormulaCubeCompact.singleLiteral(formula.predicate, formula.negated))
}

private fun simplifyCubeWrtModel(
    formula: MethodFormula.Cube,
    model: MethodFormulaCubeCompact
): SimplificationResult = if (!formula.negated) {
    simplifyPositiveCubeWrtModel(formula, model)
} else {
    simplifyNegativeCubeWrtModel(formula, model)
}

private fun simplifyNegativeCubeWrtModel(
    formula: MethodFormula.Cube,
    model: MethodFormulaCubeCompact
): SimplificationResult {
    if (formula.cube.positiveLiterals.intersects(model.negativeLiterals)) {
        return simplifiedTrue(emptyModel)
    }

    if (formula.cube.negativeLiterals.intersects(model.positiveLiterals)) {
        return simplifiedTrue(emptyModel)
    }

    val unassignedPositiveVars = formula.cube.positiveLiterals.copy()
    unassignedPositiveVars.andNot(model.positiveLiterals)

    val unassignedNegativeVars = formula.cube.negativeLiterals.copy()
    unassignedNegativeVars.andNot(model.negativeLiterals)

    if (unassignedPositiveVars.isEmpty && unassignedNegativeVars.isEmpty) {
        return simplificationFailed()
    }

    val unassignedVars = BitSet()
    unassignedVars.or(unassignedPositiveVars)
    unassignedVars.or(unassignedNegativeVars)

    if (unassignedVars.cardinality() == 1) {
        val requiredModel = MethodFormulaCubeCompact(unassignedNegativeVars, unassignedPositiveVars)
        return simplifiedTrue(requiredModel)
    }

    val simplifiedCube = MethodFormulaCubeCompact(unassignedPositiveVars, unassignedNegativeVars)
    return SimplifiedPartial(
        MethodFormula.Cube(simplifiedCube, negated = true),
        emptyModel,
        unassignedVars
    )
}

private fun simplifyPositiveCubeWrtModel(
    formula: MethodFormula.Cube,
    model: MethodFormulaCubeCompact
): SimplificationResult {
    if (formula.cube.positiveLiterals.intersects(model.negativeLiterals)) {
        return simplificationFailed()
    }

    if (formula.cube.negativeLiterals.intersects(model.positiveLiterals)) {
        return simplificationFailed()
    }

    return simplifiedTrue(formula.cube.copy())
}

fun MethodFormula.eval(model: MethodFormulaCubeCompact): VariableValue = when (this) {
    MethodFormula.False -> FalseValue
    MethodFormula.True -> TrueValue
    is MethodFormula.And -> evalAnd(model)
    is MethodFormula.Or -> evalOr(model)
    is MethodFormula.Literal -> evalLiteral(predicate, negated, model)
    is MethodFormula.Cube -> {
        val value = evalCube(cube, model)
        if (negated) value.negated else value
    }
}

private fun evalCube(cube: MethodFormulaCubeCompact, model: MethodFormulaCubeCompact): VariableValue {
    var result: VariableValue = TrueValue
    cube.positiveLiterals.forEach {
        val value = evalLiteral(it, negated = false, model)
        if (value.isFalse) return FalseValue
        if (value.isUnknown) {
            result = UnknownValue
        }
    }

    cube.negativeLiterals.forEach {
        val value = evalLiteral(it, negated = true, model)
        if (value.isFalse) return FalseValue
        if (value.isUnknown) {
            result = UnknownValue
        }
    }

    return result
}

private fun MethodFormula.Or.evalOr(model: MethodFormulaCubeCompact): VariableValue {
    var result: VariableValue = FalseValue
    for (arg in any) {
        val value = arg.eval(model)
        if (value.isTrue) return TrueValue
        if (value.isFalse) continue

        result = UnknownValue
    }
    return result
}

private fun MethodFormula.And.evalAnd(model: MethodFormulaCubeCompact): VariableValue {
    var result: VariableValue = TrueValue
    for (arg in all) {
        val value = arg.eval(model)
        if (value.isTrue) continue
        if (value.isFalse) return FalseValue

        result = UnknownValue
    }
    return result
}

private fun evalLiteral(litVar: Int, negated: Boolean, model: MethodFormulaCubeCompact): VariableValue {
    val atomValue = model.value(litVar)
    return if (negated) atomValue.negated else atomValue
}

private fun removeFreeVariables(
    formula: MethodFormula,
    model: MethodFormulaCubeCompact,
    usedVars: BitSet,
    startModel: MethodFormulaCubeCompact,
) {
    // TODO: rewrite cube simplifier
//    val possiblyFreePos = model.positiveLiterals.copy()
//    possiblyFreePos.andNot(startModel.positiveLiterals)
//    removeVariablesFromModel(model.positiveLiterals, possiblyFreePos, model, formula, usedVars)

    val possiblyFreeNeg = model.negativeLiterals.copy()
    possiblyFreeNeg.andNot(startModel.negativeLiterals)
    removeVariablesFromModel(model.negativeLiterals, possiblyFreeNeg, model, formula, usedVars)
}

private fun removeVariablesFromModel(
    litVars: BitSet,
    possiblyFree: BitSet,
    model: MethodFormulaCubeCompact,
    formula: MethodFormula,
    usedVars: BitSet
) {
    if (litVars.isEmpty) return
    possiblyFree.forEach {
        litVars.clear(it)
        if (!formula.eval(model).isTrue) {
            litVars.set(it)
        } else {
            usedVars.clear(it)
        }
    }
}
