package org.opentaint.semgrep.simplifier

import org.opentaint.semgrep.pattern.conversion.automata.MethodFormulaCubeCompact
import org.opentaint.semgrep.pattern.conversion.automata.MethodFormulaManager
import org.opentaint.semgrep.pattern.conversion.automata.OperationCancelation
import org.opentaint.semgrep.pattern.conversion.taint.FormulaManagerAwareDecisionVarSelector
import org.opentaint.semgrep.pattern.conversion.taint.MethodFormulaBenchmark
import org.opentaint.semgrep.pattern.conversion.taint.MethodFormulaBenchmark.FormulaAllModelsBenchmark
import org.opentaint.semgrep.pattern.conversion.taint.MethodFormulaBenchmark.FormulaBenchmark
import org.opentaint.semgrep.pattern.conversion.taint.MethodFormulaBenchmark.FormulaCheckSatBenchmark
import org.opentaint.semgrep.pattern.conversion.taint.methodFormulaCheckSat
import org.opentaint.semgrep.pattern.conversion.taint.methodFormulaDNF
import org.opentaint.semgrep.pattern.conversion.taint.methodFormulaDNFAlgebraic
import org.opentaint.semgrep.pattern.conversion.taint.methodFormulaDNFSinglePass
import org.opentaint.semgrep.pattern.conversion.taint.simplifyMethodFormulaCube
import org.opentaint.semgrep.pattern.conversion.taint.toMethodFormula
import java.util.zip.ZipInputStream
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.measureTimedValue

class MethodFormulaSimplifierBench {

    private val json = MethodFormulaBenchmark.benchmarkJson
    private val cancelation = OperationCancelation(Duration.INFINITE)

    private data class TimedSample<T>(
        val name: String,
        val duration: Duration,
        val isValid: Boolean,
    )

    private fun loadBenchmarks(): List<Pair<String, FormulaBenchmark>> {
        val stream = javaClass.classLoader.getResourceAsStream("simplifier/bench.zip")
            ?: error("Resource 'simplifier/bench.zip' not found")
        return ZipInputStream(stream).use { zip ->
            generateSequence { zip.nextEntry }
                .filter { !it.isDirectory && it.name.endsWith(".json") }
                .map { entry ->
                    val name = entry.name.substringAfterLast('/')
                    val content = zip.readBytes().decodeToString()
                    name to json.decodeFromString<FormulaBenchmark>(content)
                }
                .sortedBy { it.first }
                .toList()
        }
    }

    private fun <T> reportTimings(testSetName: String, allRuns: List<List<TimedSample<T>>>) {
        val measured = allRuns.drop(WARMUP_RUNS)
        println(testSetName)
        println("Runs: ${allRuns.size} total, ${measured.size} measured (dropped $WARMUP_RUNS warmup)")

        for ((runIdx, samples) in measured.withIndex()) {
            val durations = samples.map { it.duration }
            val sum = durations.fold(Duration.ZERO) { acc, d -> acc + d }
            val max = durations.max()
            val avg = sum / samples.size
            println("  Run ${runIdx + 1}: Sum=$sum | Max=$max | Avg=$avg")
        }

        val avgSums = measured.map { samples ->
            samples.map { it.duration }.fold(Duration.ZERO) { acc, d -> acc + d }
        }
        val overallAvgSum = avgSums.fold(Duration.ZERO) { acc, d -> acc + d } / measured.size
        println("  Avg Sum across measured runs: $overallAvgSum")

        val lastRun = measured.last()
        println("Top-5 slowest (last measured run):")
        lastRun.sortedByDescending { it.duration }
            .take(TOP_SLOWEST_COUNT)
            .forEachIndexed { i, s ->
                println("  ${i + 1}. ${s.name} — ${s.duration}")
            }
        
        assertTrue("Failed: $testSetName") { allRuns.all { r -> r.all { it.isValid } } }
    }

    @Test
    fun checkSat() {
        val benchmarks = loadBenchmarks()
            .filter { (_, b) -> b is FormulaCheckSatBenchmark }
            .map { (name, b) -> name to (b as FormulaCheckSatBenchmark) }

        val allRuns = (1..TOTAL_RUNS).map {
            benchmarks.map { (name, bench) -> bench.run(name) }
        }

        reportTimings("chack-sat", allRuns)
    }

    @Test
    fun models() {
        val benchmarks = loadBenchmarks()
            .filter { (_, b) -> b is FormulaAllModelsBenchmark }
            .map { (name, b) -> name to (b as FormulaAllModelsBenchmark) }

        val allRuns = (1..TOTAL_RUNS).map {
            benchmarks.map { (name, bench) -> bench.run(name) }
        }

        reportTimings("all models", allRuns)
    }

    private fun FormulaCheckSatBenchmark.run(name: String): TimedSample<Boolean> {
        val manager = MethodFormulaManager(predicates)
        val formula = formula.toMethodFormula()

        val (result, duration) = measureTimedValue {
            methodFormulaCheckSat(formula, cancelation, usePrunning = false) { model ->
                val simplifiedCube = manager.simplifyMethodFormulaCube(
                    model, metaVarInfo, applyNotEquivalentTransformations = false
                )
                simplifiedCube != null
            }
        }

        val isValid = checkEquals(isSat, result, "check-sat mismatch for $name")
        return TimedSample(name, duration, isValid)
    }

    private fun FormulaAllModelsBenchmark.run(name: String): TimedSample<List<MethodFormulaCubeCompact>> {
        val manager = MethodFormulaManager(predicates)
        val formula = formula.toMethodFormula()

        val (rawResult, duration) = measureTimedValue {
            methodFormulaDNF(formula, cancelation, FormulaManagerAwareDecisionVarSelector(manager))
        }

        val result = rawResult.mapNotNull {
            manager.simplifyMethodFormulaCube(it, metaVarInfo, applyNotEquivalentTransformations = false)
        }

        val expectedModels = models.map { it.toMethodFormula() }.mapNotNullTo(hashSetOf()) {
            manager.simplifyMethodFormulaCube(it, metaVarInfo, applyNotEquivalentTransformations = false)
        }

        val isValid = checkEquals(expectedModels, result.toSet(), "models mismatch for $name")
        return TimedSample(name, duration, isValid)
    }

    @Test
    fun modelsSinglePass() {
        val benchmarks = loadBenchmarks()
            .filter { (_, b) -> b is FormulaAllModelsBenchmark }
            .map { (name, b) -> name to (b as FormulaAllModelsBenchmark) }

        val allRuns = (1..TOTAL_RUNS).map {
            benchmarks.map { (name, bench) -> bench.runSinglePass(name) }
        }

        reportTimings("all models (single-pass)", allRuns)
    }

    private fun FormulaAllModelsBenchmark.runSinglePass(
        name: String
    ): TimedSample<List<MethodFormulaCubeCompact>> {
        val manager = MethodFormulaManager(predicates)
        val formula = formula.toMethodFormula()

        val (rawResult, duration) = measureTimedValue {
            methodFormulaDNFSinglePass(formula, cancelation, FormulaManagerAwareDecisionVarSelector(manager))
        }

        val result = rawResult.mapNotNull {
            manager.simplifyMethodFormulaCube(it, metaVarInfo, applyNotEquivalentTransformations = false)
        }

        val expectedModels = models.map { it.toMethodFormula() }.mapNotNullTo(hashSetOf()) {
            manager.simplifyMethodFormulaCube(it, metaVarInfo, applyNotEquivalentTransformations = false)
        }

        val isValid = checkEquals(expectedModels, result.toSet(), "single-pass models mismatch for $name")
        return TimedSample(name, duration, isValid)
    }

    @Test
    fun modelsSinglePassEndToEnd() {
        val benchmarks = loadBenchmarks()
            .filter { (_, b) -> b is FormulaAllModelsBenchmark }
            .map { (name, b) -> name to (b as FormulaAllModelsBenchmark) }

        val allRuns = (1..TOTAL_RUNS).map {
            benchmarks.map { (name, bench) -> bench.runSinglePassEndToEnd(name) }
        }

        reportTimings("all models (single-pass end-to-end)", allRuns)
    }

    private fun FormulaAllModelsBenchmark.runSinglePassEndToEnd(
        name: String
    ): TimedSample<List<MethodFormulaCubeCompact>> {
        val manager = MethodFormulaManager(predicates)
        val formula = formula.toMethodFormula()

        val (result, duration) = measureTimedValue {
            val rawModels = methodFormulaDNFSinglePass(
                formula, cancelation, FormulaManagerAwareDecisionVarSelector(manager)
            )
            rawModels.mapNotNull {
                manager.simplifyMethodFormulaCube(it, metaVarInfo, applyNotEquivalentTransformations = false)
            }
        }

        val expectedModels = models.map { it.toMethodFormula() }.mapNotNullTo(hashSetOf()) {
            manager.simplifyMethodFormulaCube(it, metaVarInfo, applyNotEquivalentTransformations = false)
        }

        val isValid = checkEquals(expectedModels, result.toSet(), "single-pass e2e models mismatch for $name")
        return TimedSample(name, duration, isValid)
    }

    @Test
    fun modelsAlgebraic() {
        val benchmarks = loadBenchmarks()
            .filter { (_, b) -> b is FormulaAllModelsBenchmark }
            .map { (name, b) -> name to (b as FormulaAllModelsBenchmark) }

        val allRuns = (1..TOTAL_RUNS).map {
            benchmarks.map { (name, bench) -> bench.runAlgebraic(name) }
        }

        reportTimings("all models (algebraic)", allRuns)
    }

    private fun FormulaAllModelsBenchmark.runAlgebraic(
        name: String
    ): TimedSample<List<MethodFormulaCubeCompact>> {
        val manager = MethodFormulaManager(predicates)
        val formula = formula.toMethodFormula()

        val (result, duration) = measureTimedValue {
            val rawModels = methodFormulaDNFAlgebraic(formula, cancelation)
            rawModels.mapNotNull {
                manager.simplifyMethodFormulaCube(it, metaVarInfo, applyNotEquivalentTransformations = false)
            }
        }

        val expectedModels = models.map { it.toMethodFormula() }.mapNotNullTo(hashSetOf()) {
            manager.simplifyMethodFormulaCube(it, metaVarInfo, applyNotEquivalentTransformations = false)
        }

        val resultSet = result.toSet()
        val isValid = if (name in ALGEBRAIC_KNOWN_DEVIATIONS) {
            checkCovers(expectedModels, resultSet, "algebraic models deviation for $name")
        } else {
            checkEquals(expectedModels, resultSet, "algebraic models mismatch for $name")
        }
        return TimedSample(name, duration, isValid)
    }

    @Test
    fun prunningCheckSat() {
        val benchmarks = loadBenchmarks()
            .filter { (_, b) -> b is FormulaCheckSatBenchmark }
            .map { (name, b) -> name to (b as FormulaCheckSatBenchmark) }

        val allRuns = (1..TOTAL_RUNS).map {
            benchmarks.map { (name, bench) -> bench.runWithPrunning(name) }
        }

        reportTimings("prunning-check-sat", allRuns)
    }

    private fun FormulaCheckSatBenchmark.runWithPrunning(name: String): TimedSample<Boolean> {
        val manager = MethodFormulaManager(predicates)
        val formula = formula.toMethodFormula()

        val (result, duration) = measureTimedValue {
            methodFormulaCheckSat(formula, cancelation, usePrunning = true) { model: MethodFormulaCubeCompact ->
                val simplifiedCube = manager.simplifyMethodFormulaCube(
                    model, metaVarInfo, applyNotEquivalentTransformations = false
                )
                simplifiedCube != null
            }
        }

        val isValid = checkEquals(isSat, result, "dpll check-sat mismatch for $name")
        return TimedSample(name, duration, isValid)
    }

    private fun <T> checkEquals(expected: T, actual: T, message: String): Boolean {
        if (actual == expected) return true
        System.err.println("$message: Expected <$expected>, actual <$actual>.")
        return false
    }

    private fun checkCovers(
        expected: Set<MethodFormulaCubeCompact>,
        actual: Set<MethodFormulaCubeCompact>,
        message: String
    ): Boolean {
        val allExpectedCovered = expected.all { exp -> actual.any { act -> act.containsAll(exp) } }
        val allActualValid = actual.all { act -> expected.any { exp -> act.containsAll(exp) } }
        if (allExpectedCovered && allActualValid) return true
        System.err.println("$message: Expected <$expected>, actual <$actual>.")
        return false
    }

    companion object {
        private const val TOP_SLOWEST_COUNT = 5
        private const val TOTAL_RUNS = 5
        private const val WARMUP_RUNS = 2

        private val ALGEBRAIC_KNOWN_DEVIATIONS = setOf(
            "formula_bench_59ee2b32894d151cdbc5d6a3ce41460fbfa03a67bd3a3bffcd52033f9f237a92.json"
        )
    }
}
