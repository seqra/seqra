package org.opentaint.semgrep.pattern.conversion.taint

import org.opentaint.dataflow.util.copy
import org.opentaint.dataflow.util.filter
import org.opentaint.dataflow.util.forEach
import org.opentaint.dataflow.util.map
import org.opentaint.dataflow.util.toSet
import org.opentaint.org.opentaint.semgrep.pattern.conversion.automata.OperationCancelation
import org.opentaint.org.opentaint.semgrep.pattern.conversion.taint.FormulaManagerAwareDecisionVarSelector
import org.opentaint.org.opentaint.semgrep.pattern.conversion.taint.SemanticPredicateOrderer
import org.opentaint.semgrep.pattern.MetaVarConstraint
import org.opentaint.semgrep.pattern.MetaVarConstraintFormula
import org.opentaint.semgrep.pattern.MetaVarConstraints
import org.opentaint.semgrep.pattern.ResolvedMetaVarInfo
import org.opentaint.semgrep.pattern.conversion.IsMetavar
import org.opentaint.semgrep.pattern.conversion.MetavarAtom
import org.opentaint.semgrep.pattern.conversion.ParamCondition.Atom
import org.opentaint.semgrep.pattern.conversion.SemgrepPatternAction.SignatureName
import org.opentaint.semgrep.pattern.conversion.TypeNamePattern
import org.opentaint.semgrep.pattern.conversion.automata.ClassModifierConstraint
import org.opentaint.semgrep.pattern.conversion.automata.MethodConstraint
import org.opentaint.semgrep.pattern.conversion.automata.MethodEnclosingClassName
import org.opentaint.semgrep.pattern.conversion.automata.MethodFormula
import org.opentaint.semgrep.pattern.conversion.automata.MethodFormula.Cube
import org.opentaint.semgrep.pattern.conversion.automata.MethodFormulaCubeCompact
import org.opentaint.semgrep.pattern.conversion.automata.MethodFormulaManager
import org.opentaint.semgrep.pattern.conversion.automata.MethodModifierConstraint
import org.opentaint.semgrep.pattern.conversion.automata.MethodName
import org.opentaint.semgrep.pattern.conversion.automata.MethodSignature
import org.opentaint.semgrep.pattern.conversion.automata.NumberOfArgsConstraint
import org.opentaint.semgrep.pattern.conversion.automata.ParamConstraint
import org.opentaint.semgrep.pattern.conversion.automata.Position
import org.opentaint.semgrep.pattern.conversion.automata.Predicate
import java.util.BitSet

fun trySimplifyMethodFormula(
    manager: MethodFormulaManager,
    formula: MethodFormula,
    metaVarInfo: ResolvedMetaVarInfo,
    cancelation: OperationCancelation,
): MethodFormula {
    val cubes = simplifyMethodFormula(manager, formula, metaVarInfo, cancelation)

    if (cubes.size > 100) {
        // todo: avoid formula size explosion
        return formula
    }

    return manager.mkOr(cubes)
}

fun simplifyMethodFormula(
    manager: MethodFormulaManager,
    formula: MethodFormula,
    metaVarInfo: ResolvedMetaVarInfo,
    cancelation: OperationCancelation,
    applyNotEquivalentTransformations: Boolean = false
): List<Cube> {
    val simplifiedCubes = manager.formulaSimplifiedCubes(formula, metaVarInfo, cancelation, applyNotEquivalentTransformations)
    val result = manager.simplifyUnion(simplifiedCubes.toList(), applyNotEquivalentTransformations)
    return result.map { Cube(it, negated = false) }
}

fun methodFormulaSat(
    manager: MethodFormulaManager,
    formula: MethodFormula,
    metaVarInfo: ResolvedMetaVarInfo,
    cancelation: OperationCancelation,
): Boolean {
    val simplifiedCubes = formula.tryFindSimplifiedCubes()
    if (simplifiedCubes != null) return true

    return methodFormulaCheckSat(formula, cancelation) { model ->
        val simplifiedCube = manager.simplifyMethodFormulaCube(
            model, metaVarInfo, applyNotEquivalentTransformations = false
        )
        simplifiedCube != null
    }
}

private class MethodFormulaCubeCompactComparator(
    formulaManager: MethodFormulaManager
) : Comparator<MethodFormulaCubeCompact> {
    private val orderer = SemanticPredicateOrderer(formulaManager)

    private fun compareSets(o1: BitSet, o2: BitSet): Int {
        val o1Mapped = o1.map(orderer::stablePredicateId)
        val o2Mapped = o2.map(orderer::stablePredicateId)

        return o1Mapped.compareTo(o2Mapped)
    }

    override fun compare(o1: MethodFormulaCubeCompact, o2: MethodFormulaCubeCompact): Int {
        return compareSets(o1.positiveLiterals, o2.positiveLiterals).takeIf { it != 0 }
            ?: compareSets(o1.negativeLiterals, o2.negativeLiterals)
    }

    private fun BitSet.compareTo(other: BitSet): Int {
        val diff = copy()
        diff.xor(other)

        val firstDiffPosition = diff.nextSetBit(0)
        return when {
            firstDiffPosition == -1 -> 0
            get(firstDiffPosition)  -> 1
            else                    -> -1
        }
    }
}

private fun MethodFormulaManager.reduceCubes(
    cubes: List<MethodFormulaCubeCompact>,
    metaVarInfo: ResolvedMetaVarInfo,
    applyNotEquivalentTransformations: Boolean
): List<MethodFormulaCubeCompact> {
    val cmp = MethodFormulaCubeCompactComparator(this)
    val resultCubes = cubes.toMutableList().sortedWith(cmp).toMutableList()

    var i = 0
    var j = 0
    while (true) {
        var foundSimplification = false

        while (i < resultCubes.size) {
            while (j < resultCubes.size) {
                if (i == j) {
                    j++
                    continue
                }

                if (resultCubes[i].positiveLiterals.intersects(resultCubes[j].negativeLiterals)) {
                    j++
                    continue
                }

                if (resultCubes[i].negativeLiterals.intersects(resultCubes[j].positiveLiterals)) {
                    j++
                    continue
                }

                val iLiterals = resultCubes[i].negativeLiterals.copy()
                iLiterals.or(resultCubes[i].positiveLiterals)

                val jLiterals = resultCubes[j].negativeLiterals.copy()
                jLiterals.or(resultCubes[j].positiveLiterals)

                iLiterals.andNot(jLiterals)
                val refiningLiteral = iLiterals.toSet().singleOrNull()
                if (refiningLiteral == null) {
                    j++
                    continue
                }

                val refinedJCube = if (resultCubes[i].negativeLiterals.get(refiningLiteral)) {
                    val newPositive = resultCubes[j].positiveLiterals.copy()
                    newPositive.set(refiningLiteral)
                    MethodFormulaCubeCompact(newPositive, resultCubes[j].negativeLiterals.copy())
                } else {
                    check(resultCubes[i].positiveLiterals.get(refiningLiteral))

                    val newNegative = resultCubes[j].negativeLiterals.copy()
                    newNegative.set(refiningLiteral)
                    MethodFormulaCubeCompact(resultCubes[j].positiveLiterals.copy(), newNegative)
                }

                val simplifiedJCube = simplifyMethodFormulaCube(refinedJCube, metaVarInfo, applyNotEquivalentTransformations)
                if (simplifiedJCube == null) {
                    resultCubes.removeAt(j)
                    foundSimplification = true
                    if (i > j) {
                        i--
                    }
                    break
                }
                j++
            }

            if (foundSimplification) {
                break
            }
            j = 0
            i++
        }
        if (!foundSimplification) {
            break
        }
    }

    return resultCubes
}

private fun MethodFormulaManager.formulaSimplifiedCubes(
    formula: MethodFormula,
    metaVarInfo: ResolvedMetaVarInfo,
    cancelation: OperationCancelation,
    applyNotEquivalentTransformations: Boolean
): List<MethodFormulaCubeCompact> {
    if (!applyNotEquivalentTransformations) {
        val simplifiedCubes = formula.tryFindSimplifiedCubes()
        if (simplifiedCubes != null) {
            return simplifiedCubes
        }
    }

    val dnf = when (formula) {
        MethodFormula.True -> return listOf(MethodFormulaCubeCompact())
        MethodFormula.False -> return emptyList()
        else -> methodFormulaDNF(formula, cancelation, FormulaManagerAwareDecisionVarSelector(this))
    }

    val cubes = dnf
        .mapNotNull { simplifyMethodFormulaCube(it, metaVarInfo, applyNotEquivalentTransformations) }
        .toMutableList()

    return reduceCubes(cubes, metaVarInfo, applyNotEquivalentTransformations)
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
    cube: MethodFormulaCubeCompact,
    metaVarInfo: ResolvedMetaVarInfo,
    applyNotEquivalentTransformations: Boolean,
): MethodFormulaCubeCompact? {
    var solver = MethodFormulaSolver(metaVarInfo, applyNotEquivalentTransformations)

    cube.positiveLiterals.forEach {
        solver = solver.addPositivePredicate(predicate(it))
            ?: return null
    }

    cube.negativeLiterals.forEach {
        solver = solver.addNegativePredicate(predicate(it))
            ?: return null
    }

    if (!applyNotEquivalentTransformations) {
        return cube
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

    fun hasPositiveConstraint(constraint: MethodConstraint): Boolean {
        return when (constraint) {
            is ClassModifierConstraint -> positiveClassModifiers.contains(constraint)
            is MethodModifierConstraint -> positiveMethodModifiers.contains(constraint)
            is NumberOfArgsConstraint -> positiveNumberOfArgs?.equals(constraint) ?: false
            is ParamConstraint -> positiveParams[constraint.position].orEmpty().contains(constraint.condition)
        }
    }

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
                val currentPositive = positiveParams[constraint.position].orEmpty() // TODO: support unified metavars
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
    private val metaVarInfo: ResolvedMetaVarInfo,
    private val applyNotEquivalentTransformations: Boolean,
    private val positive: SolverConstraints = SolverConstraints(signature = null),
    private val negated: MutableMap<MethodSignature, MutableList<SolverConstraints>> = hashMapOf()
) {
    private val metavarsSeen: MutableSet<MetavarAtom> = hashSetOf()

    private fun checkMetavars(predicate: Predicate): Boolean {
        val constraint = predicate.constraint as? ParamConstraint ?: return true
        val metavar = (constraint.condition as? IsMetavar)?.metavar ?: return true

        if (metavarsSeen.add(metavar)) {
            val newBasic = metavar.basics
            return metavarsSeen.all {
                val prevBasic = it.basics
                (prevBasic == newBasic) || (prevBasic.intersect(newBasic).isEmpty())
            }
        }

        return true
    }


    fun addPositivePredicate(predicate: Predicate): MethodFormulaSolver? {
        if (!checkMetavars(predicate)) {
            return null
        }

        positive.signature = positive.signature.unify(predicate.signature, metaVarInfo) ?: return null
        predicate.constraint?.let { positive.constraints.addPositive(it) ?: return null }
        return this
    }

    fun addNegativePredicate(predicate: Predicate): MethodFormulaSolver? {
        if (applyNotEquivalentTransformations) {
            val param = predicate.constraint
            if (param is ParamConstraint && param.position is Position.Result) {
                // Position.Result holds effect but implies no constraint on condition
                //  => same case as when predicate.constraint == null
                val simplifiedPredicate = Predicate(predicate.signature, constraint = null)
                return addNegativePredicate(simplifiedPredicate)
            }
        }

        if (!checkMetavars(predicate)) {
            return null
        }

        val signature = positive.signature.unify(predicate.signature, metaVarInfo)
        // incompatible signature -> predicate always false -> skip negated predicate
            ?: return this

        if (signature != predicate.signature) {
            // todo: better handling of such signatures
        }

        if (predicate.signature == positive.signature || (applyNotEquivalentTransformations && signature == positive.signature)) {
            val param = predicate.constraint
                ?: return null

            positive.constraints.addNegative(param)
                ?: return null
            return this
        }

        val constraints = SolverConstraints(predicate.signature)
        predicate.constraint?.let { constraint ->
            // Can skip constraint if it is ensured by positive
            if (!applyNotEquivalentTransformations || !positive.constraints.hasPositiveConstraint(constraint)) {
                constraints.constraints.addPositive(constraint) ?: error("impossible")
            }
        }
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
    cubes: List<MethodFormulaCubeCompact>,
    applyNotEquivalentTransformations: Boolean = false
): List<MethodFormulaCubeCompact> {
    if (!applyNotEquivalentTransformations) {
        // todo: temporary disabled. looks like union contains non-equivalent transformations
        return cubes
    }

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
    other: MethodSignature,
    metaVarInfo: ResolvedMetaVarInfo,
): MethodSignature? {
    if (this == null) return other
    return MethodSignature(
        methodName.unify(other.methodName, metaVarInfo) ?: return null,
        enclosingClassName.unify(other.enclosingClassName, metaVarInfo) ?: return null,
    )
}

private fun MethodName.unify(
    other: MethodName,
    metaVarInfo: ResolvedMetaVarInfo,
): MethodName? {
    if (this.name == other.name) return this

    when (this.name) {
        is SignatureName.AnyName -> return other
        is SignatureName.Concrete -> when (other.name) {
            SignatureName.AnyName -> return this
            is SignatureName.Concrete -> {
                if (this.name.name != other.name.name) return null
                return this
            }

            is SignatureName.MetaVar -> {
                if (!stringMatches(this.name.name, metaVarInfo.metaVarConstraints[other.name.metaVar])) return null
                return this
            }
        }

        is SignatureName.MetaVar -> when (other.name) {
            SignatureName.AnyName -> return this
            is SignatureName.Concrete -> {
                if (!stringMatches(other.name.name, metaVarInfo.metaVarConstraints[this.name.metaVar])) return null
                return other
            }

            is SignatureName.MetaVar -> {
                val thisConstraints = metaVarInfo.metaVarConstraints[this.name.metaVar] ?: return other
                val otherConstraints = metaVarInfo.metaVarConstraints[other.name.metaVar] ?: return this
                TODO("Method name metavar constraints intersection")
            }
        }
    }
}

private fun MethodEnclosingClassName.unify(
    other: MethodEnclosingClassName,
    metaVarInfo: ResolvedMetaVarInfo,
): MethodEnclosingClassName? {
    if (this.name == other.name) return this

    when (this.name) {
        TypeNamePattern.AnyType -> return other

        is TypeNamePattern.PrimitiveName -> return null

        is TypeNamePattern.ClassName -> when (other.name) {
            TypeNamePattern.AnyType -> return this

            is TypeNamePattern.ClassName,
            is TypeNamePattern.PrimitiveName -> return null

            is TypeNamePattern.FullyQualified -> {
                if (other.name.name.endsWith(this.name.name)) return other
                return null
            }

            is TypeNamePattern.MetaVar -> {
                if (!stringMatches(this.name.name, metaVarInfo.metaVarConstraints[other.name.metaVar])) return null
                return this
            }
        }

        is TypeNamePattern.FullyQualified -> when (other.name) {
            TypeNamePattern.AnyType -> return this

            is TypeNamePattern.PrimitiveName -> return null

            is TypeNamePattern.ClassName -> {
                if (this.name.name.endsWith(other.name.name)) return this
                return null
            }

            is TypeNamePattern.FullyQualified -> return null

            is TypeNamePattern.MetaVar -> {
                if (!stringMatches(this.name.name, metaVarInfo.metaVarConstraints[other.name.metaVar])) return null
                return this
            }
        }

        is TypeNamePattern.MetaVar -> when (other.name) {
            TypeNamePattern.AnyType -> return this

            is TypeNamePattern.PrimitiveName -> return null

            is TypeNamePattern.ClassName -> {
                if (!stringMatches(other.name.name, metaVarInfo.metaVarConstraints[this.name.metaVar])) return null
                return other
            }

            is TypeNamePattern.FullyQualified -> {
                if (!stringMatches(other.name.name, metaVarInfo.metaVarConstraints[this.name.metaVar])) return null
                return other
            }

            is TypeNamePattern.MetaVar -> {
                val thisConstraints = metaVarInfo.metaVarConstraints[this.name.metaVar] ?: return other
                val otherConstraints = metaVarInfo.metaVarConstraints[other.name.metaVar] ?: return this
                TODO("Type metavar constraints intersection")
            }
        }
    }
}

private fun stringMatches(name: String, constraints: MetaVarConstraints?): Boolean {
    if (constraints == null) return true
    return constraints.constraint.stringMatches(name)
}

private fun MetaVarConstraintFormula<MetaVarConstraint>.stringMatches(name: String): Boolean = when (this) {
    is MetaVarConstraintFormula.Constraint -> stringMatches(name, constraint)
    is MetaVarConstraintFormula.Not -> !negated.stringMatches(name)
    is MetaVarConstraintFormula.And -> args.all { it.stringMatches(name) }
}

private fun stringMatches(name: String, constraint: MetaVarConstraint): Boolean = when (constraint) {
    is MetaVarConstraint.Concrete -> name == constraint.value
    is MetaVarConstraint.RegExp -> {
        val pattern = Regex(constraint.regex)
        pattern.matches(name)
    }
}
