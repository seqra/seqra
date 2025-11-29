package org.opentaint.semgrep.simplifier

import org.opentaint.dataflow.util.forEach
import org.opentaint.dataflow.util.toSet
import org.opentaint.semgrep.pattern.conversion.automata.MethodFormula
import org.opentaint.semgrep.pattern.conversion.automata.MethodFormulaCubeCompact
import org.opentaint.semgrep.pattern.conversion.automata.isTrue
import org.opentaint.semgrep.pattern.conversion.automata.isUnknown
import org.opentaint.semgrep.pattern.conversion.taint.eval
import org.opentaint.semgrep.pattern.conversion.taint.methodFormulaModels
import java.util.BitSet
import kotlin.random.Random
import kotlin.test.Test

class MethodFormulaSimplifierTest {
    private val reduce = false

//    @Test
//    fun testFormulaModels() {
//        testRandomFormulas()
//    }

    @Test
    fun test1() = runTest(-712111827, 2)

    @Test
    fun test2() = runTest(-1229009901, 4)

    @Test
    fun test3() = runTest(1453285908, 4)

    @Test
    fun test4() = runTest(709986060, 6)

    @Test
    fun test5() = runTest(-847516676, 4)

    @Test
    fun test6() = runTest(-73630590, 7)

    @Test
    fun test7() = runTest(1962457366, 5)

    @Test
    fun test8() = runTest(-520589524, 5)

    @Test
    fun test9() = runTest(2112271589, 42)

    private fun testRandomFormulas() {
        for (limit in 0 until 100) {
            println("Start search with limit $limit")
            repeat(10000) {
                val seed = Random.nextInt()
                runTest(seed, limit)
            }
        }
    }

    private val maxVar = 10

    private fun runTest(formulaSeed: Int, limit: Int) {
        check(maxVar == 10) { "Test are valid only with maxVar = 10" }

        val random = Random(seed = formulaSeed)
        val generator = FormulaGenerator(random, maxVar, limit)
        val formula = generator.generateRandomFormula()

        checkInvariantsAndReduce(random, formula, formulaSeed, limit)
    }

    private fun checkInvariantsAndReduce(
        random: Random,
        formula: MethodFormula,
        formulaSeed: Int,
        limit: Int,
    ) {
        val result = checkInvariants(formula)
        if (result) return

        if (!reduce) {
            error("Incorrect model: $formulaSeed, $limit")
        }

        var reduced = formula
        repeat(10000) {
            val f = reduceFormula(random, reduced)
            if (!checkInvariants(f)) {
                reduced = f
            }
        }

        checkInvariants(reduced)
        error("Incorrect model: $formulaSeed, $limit")
    }

    private fun checkInvariants(
        formula: MethodFormula
    ): Boolean {
        val groundTruth = groundTruthFormulaModels(formula)
        val actualModels = methodFormulaModels(formula)
        val completeActualModels = actualModels.flatMapTo(hashSetOf()) {
            completeModel(it)
        }

        return groundTruth == completeActualModels
    }

    private val modelCandidates by lazy {
        val models = mutableListOf<MethodFormulaCubeCompact>()
        generateModelCandidate(1, MethodFormulaCubeCompact(), models)
        models
    }

    private fun generateModelCandidate(
        currentVar: Int,
        cube: MethodFormulaCubeCompact,
        models: MutableList<MethodFormulaCubeCompact>
    ) {
        if (currentVar >= maxVar) {
            models.add(cube.copy())
            return
        }

        cube.positiveLiterals.set(currentVar)
        generateModelCandidate(currentVar + 1, cube, models)
        cube.positiveLiterals.clear(currentVar)

        cube.negativeLiterals.set(currentVar)
        generateModelCandidate(currentVar + 1, cube, models)
        cube.negativeLiterals.clear(currentVar)
    }

    private fun groundTruthFormulaModels(formula: MethodFormula): Set<MethodFormulaCubeCompact> =
        modelCandidates.filterTo(hashSetOf()) { formula.evalEnsureComplete(it) }

    private fun MethodFormula.evalEnsureComplete(model: MethodFormulaCubeCompact): Boolean {
        val value = eval(model)
        check(!value.isUnknown) { "Incomplete model" }

        return value.isTrue
    }

    private fun completeModel(model: MethodFormulaCubeCompact): List<MethodFormulaCubeCompact> {
        val freeVariables = BitSet()
        freeVariables.set(1, maxVar)

        freeVariables.andNot(model.positiveLiterals)
        freeVariables.andNot(model.negativeLiterals)

        var result = listOf(model)
        freeVariables.forEach { variable ->
            val completeModels = mutableListOf<MethodFormulaCubeCompact>()
            for (m in result) {
                val pos = m.copy()
                pos.positiveLiterals.set(variable)
                completeModels.add(pos)

                val neg = m.copy()
                neg.negativeLiterals.set(variable)
                completeModels.add(neg)
            }
            result = completeModels
        }
        return result
    }

    class FormulaGenerator(
        private val random: Random,
        private val maxVar: Int,
        resourceLimit: Int,
    ) {
        private var resource = resourceLimit

        private fun generateLit(): MethodFormula.Literal {
            resource--

            val variable = random.nextInt(1, maxVar)
            val negated = random.nextBoolean()
            return MethodFormula.Literal(variable, negated)
        }

        private fun generateCube(): MethodFormula.Cube {
            val cubeVariables = BitSet()

            val maxCubeSize = minOf(resource, maxVar).coerceAtLeast(1)
            val cubeSize = random.nextInt(0, maxCubeSize)
            resource -= cubeSize

            repeat(cubeSize) {
                cubeVariables.set(random.nextInt(1, maxVar))
            }

            val cube = MethodFormulaCubeCompact()
            cubeVariables.forEach {
                val negated = random.nextBoolean()
                if (negated) {
                    cube.negativeLiterals.set(it)
                } else {
                    cube.positiveLiterals.set(it)
                }
            }

            val cubeNegated = random.nextBoolean()
            return MethodFormula.Cube(cube, cubeNegated)
        }

        private fun generateAnd(): MethodFormula.And {
            resource--
            return MethodFormula.And(generateArray())
        }

        private fun generateOr(): MethodFormula.Or {
            resource--
            return MethodFormula.Or(generateArray())
        }

        private fun generateArray(): Array<MethodFormula> {
            val result = mutableListOf<MethodFormula>()
            for (i in 0 until random.nextInt(0, maxVar)) {
                if (resource < 0) break

                result += generateRandomFormula()
            }
            return result.toTypedArray()
        }

        fun generateRandomFormula(): MethodFormula {
            val op = random.nextDouble()
            return when {
                op < 0.02 -> MethodFormula.True
                op < 0.04 -> MethodFormula.False
                op < 0.3 -> generateLit()
                op < 0.5 -> generateCube()
                op < 0.75 -> generateAnd()
                else -> generateOr()
            }
        }
    }

    private fun reduceFormula(
        random: Random,
        formula: MethodFormula
    ): MethodFormula {
        return when (formula) {
            MethodFormula.False -> MethodFormula.False
            MethodFormula.True -> MethodFormula.True

            is MethodFormula.Literal -> {
                val op = random.nextDouble()
                when {
                    op < 0.25 -> MethodFormula.False
                    op < 0.5 -> MethodFormula.True
                    else -> formula
                }
            }

            is MethodFormula.And -> {
                val op = random.nextDouble()
                when {
                    op < 0.1 -> MethodFormula.False
                    op < 0.2 -> MethodFormula.True
                    op < 0.8 -> {
                        val args = reduceArray(random, formula.all)
                        if (args.isEmpty()) MethodFormula.True else MethodFormula.And(args)
                    }

                    else -> formula
                }
            }

            is MethodFormula.Or -> {
                val op = random.nextDouble()
                when {
                    op < 0.1 -> MethodFormula.False
                    op < 0.2 -> MethodFormula.True
                    op < 0.8 -> {
                        val args = reduceArray(random, formula.any)
                        if (args.isEmpty()) MethodFormula.False else MethodFormula.Or(args)
                    }

                    else -> formula
                }
            }

            is MethodFormula.Cube -> {
                if (random.nextBoolean()) return formula

                val vars = formula.cube.negativeLiterals.toSet() + formula.cube.positiveLiterals.toSet()
                if (vars.isEmpty()) return formula

                val varList = vars.toList()
                val randomVar = varList.random(random)

                val lits = formula.cube.copy()
                lits.positiveLiterals.clear(randomVar)
                lits.negativeLiterals.clear(randomVar)

                MethodFormula.Cube(lits, formula.negated)
            }
        }
    }

    private fun reduceArray(random: Random, args: Array<MethodFormula>): Array<MethodFormula> {
        if (args.isEmpty()) {
            return args
        }

        val result = args.toMutableList()

        if (random.nextBoolean()) {
            val idx = random.nextInt(0, result.size)
            result.removeAt(idx)
        }

        return result.map { reduceFormula(random, it) }.toTypedArray()
    }
}
