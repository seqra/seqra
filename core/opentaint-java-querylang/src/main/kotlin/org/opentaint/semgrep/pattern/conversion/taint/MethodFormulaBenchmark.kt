package org.opentaint.semgrep.pattern.conversion.taint

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass
import org.opentaint.semgrep.pattern.MetaVarConstraint
import org.opentaint.dataflow.util.forEach
import org.opentaint.semgrep.pattern.ResolvedMetaVarInfo
import org.opentaint.semgrep.pattern.conversion.automata.MethodFormula
import org.opentaint.semgrep.pattern.conversion.automata.MethodFormulaCubeCompact
import org.opentaint.semgrep.pattern.conversion.automata.MethodFormulaManager
import org.opentaint.semgrep.pattern.conversion.automata.Predicate
import java.nio.file.Path
import java.security.MessageDigest
import java.util.BitSet
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimedValue
import kotlin.time.measureTimedValue

object MethodFormulaBenchmark {
    val benchmarkDumpPath: Path? = null
    val benchmarkDumpTimeLimit = 1.milliseconds

    val benchmarkJson by lazy {
        Json {
            serializersModule = SerializersModule {
                polymorphic(Any::class) {
                    subclass(MetaVarConstraint.RegExp::class)
                    subclass(MetaVarConstraint.Concrete::class)
                }
            }
        }
    }

    @Serializable
    sealed interface FormulaBenchmark

    @Serializable
    @SerialName("SatBench")
    data class FormulaCheckSatBenchmark(
        val predicates: List<Predicate>,
        val metaVarInfo: ResolvedMetaVarInfo,
        val formula: SerializedMethodFormula,
        val isSat: Boolean,
    ) : FormulaBenchmark

    @Serializable
    @SerialName("ModelsBench")
    data class FormulaAllModelsBenchmark(
        val predicates: List<Predicate>,
        val metaVarInfo: ResolvedMetaVarInfo,
        val formula: SerializedMethodFormula,
        val models: List<SerializedMethodFormula.SerializedCube>,
    ) : FormulaBenchmark

    inline fun benchmarkFormulaDNF(
        manager: MethodFormulaManager,
        formula: MethodFormula,
        metaVarInfo: ResolvedMetaVarInfo,
        body: () -> List<MethodFormulaCubeCompact>
    ): List<MethodFormulaCubeCompact> {
        if (benchmarkDumpPath == null) return body()
        return measureTimedValue { body() }.dumpAllModelsBench(manager, formula, metaVarInfo).value
    }

    inline fun checkSat(
        manager: MethodFormulaManager,
        formula: MethodFormula,
        metaVarInfo: ResolvedMetaVarInfo,
        body: () -> Boolean
    ): Boolean {
        if (benchmarkDumpPath == null) return body()
        return measureTimedValue { body() }.dumpCheckSatBench(manager, formula, metaVarInfo).value
    }

    fun TimedValue<Boolean>.dumpCheckSatBench(
        manager: MethodFormulaManager,
        formula: MethodFormula,
        metaVarInfo: ResolvedMetaVarInfo,
    ) = apply {
        if (duration < benchmarkDumpTimeLimit) return this

        val bench = FormulaCheckSatBenchmark(
            manager.allPredicates,
            metaVarInfo,
            formula.toSerializedMethodFormula(),
            isSat = value
        )

        bench.dump()
    }

    fun TimedValue<List<MethodFormulaCubeCompact>>.dumpAllModelsBench(
        manager: MethodFormulaManager,
        formula: MethodFormula,
        metaVarInfo: ResolvedMetaVarInfo,
    ) = apply {
        if (duration < benchmarkDumpTimeLimit) return this

        val bench = FormulaAllModelsBenchmark(
            manager.allPredicates,
            metaVarInfo,
            formula.toSerializedMethodFormula(),
            value.map { it.toSerializedMethodFormula() },
        )

        bench.dump()
    }

    @OptIn(ExperimentalStdlibApi::class)
    private fun FormulaBenchmark.dump() {
        val dumpPath = benchmarkDumpPath ?: return
        val benchJson = benchmarkJson.encodeToString(this)

        val hash = MessageDigest.getInstance("SHA-256")
            .digest(benchJson.toByteArray())
            .toHexString()

        val file = dumpPath.resolve("formula_bench_$hash.json")
        dumpPath.createDirectories()
        file.writeText(benchJson)
    }
}

@Serializable
sealed class SerializedMethodFormula {
    @Serializable
    @SerialName("Or")
    data class Or(val any: List<SerializedMethodFormula>) : SerializedMethodFormula()

    @Serializable
    @SerialName("And")
    data class And(val all: List<SerializedMethodFormula>) : SerializedMethodFormula()

    @Serializable
    @SerialName("Literal")
    data class Literal(val predicate: Int, val negated: Boolean) : SerializedMethodFormula()

    @Serializable
    data class SerializedCube(
        val positiveLiterals: List<Int>,
        val negativeLiterals: List<Int>,
    )

    @Serializable
    @SerialName("CubeLiteral")
    data class CubeLiteral(
        val cube: SerializedCube,
        val negated: Boolean,
    ) : SerializedMethodFormula()

    @Serializable
    @SerialName("True")
    data object True : SerializedMethodFormula()

    @Serializable
    @SerialName("False")
    data object False : SerializedMethodFormula()
}

fun MethodFormula.toSerializedMethodFormula(): SerializedMethodFormula = when (this) {
    is MethodFormula.Or -> SerializedMethodFormula.Or(any.map { it.toSerializedMethodFormula() })
    is MethodFormula.And -> SerializedMethodFormula.And(all.map { it.toSerializedMethodFormula() })
    is MethodFormula.Literal -> SerializedMethodFormula.Literal(predicate, negated)
    is MethodFormula.Cube -> SerializedMethodFormula.CubeLiteral(
        cube = cube.toSerializedMethodFormula(),
        negated = negated,
    )

    is MethodFormula.True -> SerializedMethodFormula.True
    is MethodFormula.False -> SerializedMethodFormula.False
}

fun MethodFormulaCubeCompact.toSerializedMethodFormula(): SerializedMethodFormula.SerializedCube =
    SerializedMethodFormula.SerializedCube(
        positiveLiterals = positiveLiterals.toIntList(),
        negativeLiterals = negativeLiterals.toIntList(),
    )

fun SerializedMethodFormula.toMethodFormula(): MethodFormula = when (this) {
    is SerializedMethodFormula.Or -> MethodFormula.Or(any.map { it.toMethodFormula() }.toTypedArray())
    is SerializedMethodFormula.And -> MethodFormula.And(all.map { it.toMethodFormula() }.toTypedArray())
    is SerializedMethodFormula.Literal -> MethodFormula.Literal(predicate, negated)
    is SerializedMethodFormula.CubeLiteral -> MethodFormula.Cube(
        cube = cube.toMethodFormula(),
        negated = negated,
    )

    is SerializedMethodFormula.True -> MethodFormula.True
    is SerializedMethodFormula.False -> MethodFormula.False
}

fun SerializedMethodFormula.SerializedCube.toMethodFormula(): MethodFormulaCubeCompact =
    MethodFormulaCubeCompact(
        positiveLiterals = positiveLiterals.toBitSet(),
        negativeLiterals = negativeLiterals.toBitSet(),
    )

private fun BitSet.toIntList(): List<Int> {
    val result = mutableListOf<Int>()
    forEach { result += it }
    return result
}

private fun List<Int>.toBitSet(): BitSet = BitSet().also { bitSet ->
    forEach { bitSet.set(it) }
}
