package org.opentaint.org.opentaint.semgrep.pattern.conversion.taint

import org.opentaint.dataflow.util.filter
import org.opentaint.dataflow.util.forEach
import org.opentaint.org.opentaint.semgrep.pattern.conversion.ParamCondition.Atom
import org.opentaint.org.opentaint.semgrep.pattern.conversion.automata.ClassModifierConstraint
import org.opentaint.org.opentaint.semgrep.pattern.conversion.automata.MethodConstraint
import org.opentaint.org.opentaint.semgrep.pattern.conversion.automata.MethodEnclosingClassName
import org.opentaint.org.opentaint.semgrep.pattern.conversion.automata.MethodFormula
import org.opentaint.org.opentaint.semgrep.pattern.conversion.automata.MethodFormula.Cube
import org.opentaint.org.opentaint.semgrep.pattern.conversion.automata.MethodFormulaCubeCompact
import org.opentaint.org.opentaint.semgrep.pattern.conversion.automata.MethodFormulaManager
import org.opentaint.org.opentaint.semgrep.pattern.conversion.automata.MethodModifierConstraint
import org.opentaint.org.opentaint.semgrep.pattern.conversion.automata.MethodName
import org.opentaint.org.opentaint.semgrep.pattern.conversion.automata.MethodSignature
import org.opentaint.org.opentaint.semgrep.pattern.conversion.automata.NumberOfArgsConstraint
import org.opentaint.org.opentaint.semgrep.pattern.conversion.automata.ParamConstraint
import org.opentaint.org.opentaint.semgrep.pattern.conversion.automata.Position
import org.opentaint.org.opentaint.semgrep.pattern.conversion.automata.Predicate
import java.util.BitSet

fun simplifyMethodFormulaAnd(manager: MethodFormulaManager, formulas: List<MethodFormula>): MethodFormula {
    return manager.mkOr(simplifyMethodFormula(manager, manager.mkAnd(formulas)))
}

fun simplifyMethodFormulaOr(manager: MethodFormulaManager, formulas: List<MethodFormula>): MethodFormula {
    val simplifiedCubes = formulas.flatMap { formula ->
        manager.formulaSimplifiedCubes(formula)
    }

    val result = manager.simplifyUnion(simplifiedCubes.toList())
    return manager.mkOr(result.map { manager.mkCube(it) })
}

fun trySimplifyMethodFormula(manager: MethodFormulaManager, formula: MethodFormula): MethodFormula {
    val cubes = simplifyMethodFormula(manager, formula)

    if (cubes.size > 100) {
        // todo: avoid formula size explosion
        return formula
    }

    return manager.mkOr(cubes)
}

fun simplifyMethodFormula(manager: MethodFormulaManager, formula: MethodFormula): List<Cube> {
    val simplifiedCubes = manager.formulaSimplifiedCubes(formula)
    val result = manager.simplifyUnion(simplifiedCubes.toList())
    return result.map { Cube(it, negated = false) }
}

fun methodFormulaSat(manager: MethodFormulaManager, formula: MethodFormula): Boolean {
    val simplifiedCubes = formula.tryFindSimplifiedCubes()
    if (simplifiedCubes != null) return true

    return methodFormulaCheckSat(formula) { model ->
        val simplifiedCube = manager.simplifyMethodFormulaCube(model)
        simplifiedCube != null
    }
}

private fun MethodFormulaManager.formulaSimplifiedCubes(
    formula: MethodFormula
): List<MethodFormulaCubeCompact> {
    val simplifiedCubes = formula.tryFindSimplifiedCubes()
    if (simplifiedCubes != null) {
        return simplifiedCubes
    }

    val dnf = when (formula) {
        MethodFormula.True -> return listOf(MethodFormulaCubeCompact())
        MethodFormula.False -> return emptyList()
        else -> methodFormulaDNF(formula)
    }

    return dnf.mapNotNull { simplifyMethodFormulaCube(it) }
}

private fun MethodFormula.tryFindSimplifiedCubes(): List<MethodFormulaCubeCompact>? {
    if (this is Cube) {
        if (!negated) {
            return listOf(cube)
        }
    }

    if (this is MethodFormula.Or) {
        val cubes = any.mapNotNull {
            if (it !is Cube) return@mapNotNull null
            if (it.negated) return@mapNotNull null

            it.cube
        }

        // already simplified
        if (cubes.size == any.size) {
            return cubes
        }
    }

    return null
}

private fun MethodFormulaManager.simplifyMethodFormulaCube(
    cube: MethodFormulaCubeCompact
): MethodFormulaCubeCompact? {
    var solver = MethodFormulaSolver()

    cube.positiveLiterals.forEach {
        solver = solver.addPositivePredicate(predicate(it)) ?: return null
    }

    cube.negativeLiterals.forEach {
        solver = solver.addNegativePredicate(predicate(it)) ?: return null
    }

    val solution = solver.solution()

    val result = MethodFormulaCubeCompact()
    solution.forEach { lit ->
        val predicateId = predicateId(lit.predicate)
        if (lit.negated) {
            result.negativeLiterals.set(predicateId)
        } else {
            result.positiveLiterals.set(predicateId)
        }
    }
    return result
}

private class MethodConstraintsSolver {
    private val positiveParams = hashMapOf<Position, MutableSet<Atom>>()
    private var positiveNumberOfArgs: NumberOfArgsConstraint? = null
    private val positiveMethodModifiers = hashSetOf<MethodModifierConstraint>()
    private val positiveClassModifiers = hashSetOf<ClassModifierConstraint>()

    private val negative = hashSetOf<MethodConstraint>()

    fun addPositive(constraint: MethodConstraint): Unit? {
        when (constraint) {
            is ParamConstraint -> {
                positiveParams.getOrPut(constraint.position, ::hashSetOf).add(constraint.condition)
            }

            is NumberOfArgsConstraint -> {
                val current = positiveNumberOfArgs
                if (current != null && current != constraint) return null

                positiveNumberOfArgs = constraint
            }

            is ClassModifierConstraint -> {
                positiveClassModifiers.add(constraint)
            }

            is MethodModifierConstraint -> {
                positiveMethodModifiers.add(constraint)
            }
        }

        return Unit
    }

    fun addNegative(constraint: MethodConstraint): Unit? {
        when (constraint) {
            is ParamConstraint -> {
                val currentPositive = positiveParams[constraint.position].orEmpty()
                if (constraint.condition in currentPositive) return null
            }

            is NumberOfArgsConstraint -> {
                if (positiveNumberOfArgs == constraint) return null
                if (positiveNumberOfArgs != null) return Unit
            }

            is ClassModifierConstraint -> {
                if (constraint in positiveClassModifiers) return null
            }

            is MethodModifierConstraint -> {
                if (constraint in positiveMethodModifiers) return null
            }
        }

        negative.add(constraint)
        return Unit
    }

    fun solution(): Pair<Set<MethodConstraint>, Set<MethodConstraint>> {
        val posConditions = hashSetOf<MethodConstraint>()
        positiveParams.forEach { (pos, conds) -> conds.mapTo(posConditions) { ParamConstraint(pos, it) } }
        posConditions.addAll(positiveMethodModifiers)
        posConditions.addAll(positiveClassModifiers)
        positiveNumberOfArgs?.let { posConditions.add(it) }

        return posConditions to negative
    }
}

private class SolverConstraints(
    var signature: MethodSignature? = null,
    val constraints: MethodConstraintsSolver = MethodConstraintsSolver()
)

private class MethodFormulaSolver(
    private val positive: SolverConstraints = SolverConstraints(signature = null),
    private val negated: MutableMap<MethodSignature, MutableList<SolverConstraints>> = hashMapOf()
) {
    fun addPositivePredicate(predicate: Predicate): MethodFormulaSolver? {
        positive.signature = positive.signature.unify(predicate.signature) ?: return null
        predicate.constraint?.let { positive.constraints.addPositive(it) ?: return null }
        return this
    }

    fun addNegativePredicate(predicate: Predicate): MethodFormulaSolver? {
        val signature = positive.signature.unify(predicate.signature)
        // incompatible signature -> predicate always false -> skip negated predicate
            ?: return this

        if (signature != predicate.signature) {
            // todo: better handling of such signatures
        }

        if (predicate.signature == positive.signature) {
            val param = predicate.constraint ?: return null
            positive.constraints.addNegative(param) ?: return null

            return this
        }

        val constraints = SolverConstraints(predicate.signature)
        predicate.constraint?.let { constraints.constraints.addPositive(it) ?: error("impossible") }
        negated.getOrPut(predicate.signature, ::mutableListOf).add(constraints)

        return this
    }

    data class Lit(val predicate: Predicate, val negated: Boolean)

    fun solution(): List<Lit> {
        val literals = mutableListOf<Lit>()

        val positiveSig = positive.signature
        if (positiveSig != null) {
            positive.constraints.addSolution(literals, positiveSig, negated = false)
        }

        for ((signature, paramConstraints) in negated) {
            for (constraint in paramConstraints) {
                constraint.constraints.addSolution(literals, signature, negated = true)
            }
        }

        return literals
    }

    private fun MethodConstraintsSolver.addSolution(
        result: MutableList<Lit>,
        signature: MethodSignature,
        negated: Boolean
    ) {
        val (posParams, negParams) = solution()

        if (posParams.isEmpty() && negParams.isEmpty()) {
            result += Lit(Predicate(signature, constraint = null), negated)
            return
        }

        posParams.mapTo(result) {
            Lit(Predicate(signature, it), negated)
        }

        check(!negated || negParams.isEmpty())

        negParams.mapTo(result) {
            Lit(Predicate(signature, it), negated = true)
        }
    }
}

private fun MethodFormulaManager.simplifyUnion(
    cubes: List<MethodFormulaCubeCompact>
): List<MethodFormulaCubeCompact> {
    if (cubes.size < 2) return cubes

    var mutableCubes = cubes.toMutableList()
    while (true) {
        mutableCubes.sortBy { it.size }

        val removedIndices = BitSet()
        val newCubes = mutableListOf<MethodFormulaCubeCompact>()

        for (i in mutableCubes.indices) {
            if (removedIndices.get(i)) continue

            val first = mutableCubes[i]

            if (first.size == 0) {
                // cube evaluates to T -> all union is T
                return listOf(first)
            }

            for (j in i + 1 until mutableCubes.size) {
                if (removedIndices.get(j)) continue

                val second = mutableCubes[j]

                val simplified = trySimplify(first, second) ?: continue

                newCubes.addAll(simplified)

                removedIndices.set(i)
                removedIndices.set(j)

                break
            }
        }

        if (removedIndices.isEmpty) {
            break
        }

        for (i in mutableCubes.indices) {
            if (removedIndices.get(i)) continue
            newCubes.add(mutableCubes[i])
        }

        mutableCubes = newCubes
    }

    return mutableCubes
}

private data class CubeDiff(
    val same: MethodFormulaCubeCompact,
    val first: MethodFormulaCubeCompact,
    val second: MethodFormulaCubeCompact,
)

private fun cubeDiff(first: MethodFormulaCubeCompact, second: MethodFormulaCubeCompact): CubeDiff {
    val diff = CubeDiff(first.copy(), first.copy(), second.copy())
    diff.same.positiveLiterals.and(second.positiveLiterals)
    diff.same.negativeLiterals.and(second.negativeLiterals)

    diff.first.positiveLiterals.andNot(diff.same.positiveLiterals)
    diff.second.positiveLiterals.andNot(diff.same.positiveLiterals)

    diff.first.negativeLiterals.andNot(diff.same.negativeLiterals)
    diff.second.negativeLiterals.andNot(diff.same.negativeLiterals)

    return diff
}

private fun MethodFormulaManager.trySimplify(
    first: MethodFormulaCubeCompact,
    second: MethodFormulaCubeCompact
): List<MethodFormulaCubeCompact>? {
    check(first.size <= second.size)

    val diff = cubeDiff(first, second)

    val simplifiedDisjunction = trySimplifyDisjunctionUnderAssumptions(
        diff.same, diff.first, diff.second
    )

    if (simplifiedDisjunction != null) {
        return simplifiedDisjunction.map { diff.same.add(it) }
    }

    return null
}

private fun MethodFormulaManager.trySimplifyDisjunctionUnderAssumptions(
    assumptions: MethodFormulaCubeCompact,
    first: MethodFormulaCubeCompact,
    second: MethodFormulaCubeCompact,
): List<MethodFormulaCubeCompact>? {
    if (first.size == 0) return listOf(first)

    val positiveContr = first.positiveLiterals.filter { second.negativeLiterals.get(it) }
    positiveContr.forEach {
        val result = tryRemoveLit(assumptions, first, second, it)
        if (result != null) return result
    }

    val negativeContr = second.positiveLiterals.filter { first.negativeLiterals.get(it) }
    negativeContr.forEach {
        val result = tryRemoveLit(assumptions, second, first, it)
        if (result != null) return result
    }

    return null
}

private fun MethodFormulaManager.tryRemoveLit(
    assumptions: MethodFormulaCubeCompact,
    first: MethodFormulaCubeCompact,
    second: MethodFormulaCubeCompact,
    litToRemove: Int,
): List<MethodFormulaCubeCompact>? {
    check(first.positiveLiterals.get(litToRemove) && second.negativeLiterals.get(litToRemove))

    val resultCube = first.copy().add(second)
    resultCube.positiveLiterals.clear(litToRemove)
    resultCube.negativeLiterals.clear(litToRemove)

    val firstWithAssumptions = assumptions.add(first)
    val secondWithAssumptions = assumptions.add(second)
    if (cubeIsImplied(resultCube, firstWithAssumptions) && cubeIsImplied(resultCube, secondWithAssumptions)) {
        return listOf(resultCube)
    }

    if (first.size == 1) {
        // A \/ (!A /\ x)
        // A \/ x
        val result = second.copy()
        result.negativeLiterals.clear(litToRemove)
        return listOf(first, result)
    }

    if (second.size == 1) {
        // (A /\ x) \/ !A
        // x \/ !A
        val result = first.copy()
        result.positiveLiterals.clear(litToRemove)
        return listOf(second, result)
    }

    return null
}

private fun MethodFormulaManager.cubeIsImplied(
    cube: MethodFormulaCubeCompact,
    impliedBy: MethodFormulaCubeCompact
): Boolean {
    cube.positiveLiterals.forEach { cubePosLit ->
        val predicate = predicate(cubePosLit)
        if (!cubeImplyLiteral(impliedBy, predicate, negated = false)) return false
    }

    cube.negativeLiterals.forEach { cubeNegLit ->
        val predicate = predicate(cubeNegLit)
        if (!cubeImplyLiteral(impliedBy, predicate, negated = true)) return false
    }

    return true
}

private fun MethodFormulaManager.cubeImplyLiteral(
    cube: MethodFormulaCubeCompact,
    predicate: Predicate, negated: Boolean
): Boolean {
    cube.positiveLiterals.forEach { cubePosLit ->
        val cubePredicate = predicate(cubePosLit)
        if (implyLiteral(cubePredicate, firstNegated = false, predicate, negated)) return true
    }
    cube.negativeLiterals.forEach { cubeNegLit ->
        val cubePredicate = predicate(cubeNegLit)
        if (implyLiteral(cubePredicate, firstNegated = true, predicate, negated)) return true
    }
    return false
}

private fun implyLiteral(
    firstPredicate: Predicate, firstNegated: Boolean,
    secondPredicate: Predicate, secondNegated: Boolean
): Boolean {
    if (firstNegated == secondNegated) {
        if (firstPredicate == secondPredicate) return true
    }

    if (!firstNegated && secondNegated) {
        if (secondPredicate.signature != firstPredicate.signature) return true
    }

    return false
}

private fun MethodSignature?.unify(
    other: MethodSignature
): MethodSignature? {
    if (this == null) return other
    return MethodSignature(
        methodName.unify(other.methodName) ?: return null,
        enclosingClassName.unify(other.enclosingClassName) ?: return null,
    )
}

private fun MethodName.unify(
    other: MethodName
): MethodName? = when {
    this.name == null -> other
    other.name == null -> this
    this.name == other.name -> this
    else -> null
}

private fun MethodEnclosingClassName.unify(
    other: MethodEnclosingClassName
): MethodEnclosingClassName? = when {
    this.name == null -> other
    other.name == null -> this
    this.name == other.name -> this
    else -> null
}
