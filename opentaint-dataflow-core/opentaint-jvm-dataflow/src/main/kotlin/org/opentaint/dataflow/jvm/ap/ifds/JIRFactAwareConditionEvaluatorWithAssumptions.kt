package org.opentaint.dataflow.jvm.ap.ifds

import org.opentaint.ir.api.common.cfg.CommonValue
import org.opentaint.ir.taint.configuration.And
import org.opentaint.ir.taint.configuration.Condition
import org.opentaint.ir.taint.configuration.ConstantEq
import org.opentaint.ir.taint.configuration.ConstantGt
import org.opentaint.ir.taint.configuration.ConstantLt
import org.opentaint.ir.taint.configuration.ConstantMatches
import org.opentaint.ir.taint.configuration.ConstantTrue
import org.opentaint.ir.taint.configuration.ContainsMark
import org.opentaint.ir.taint.configuration.IsConstant
import org.opentaint.ir.taint.configuration.Not
import org.opentaint.ir.taint.configuration.Or
import org.opentaint.ir.taint.configuration.PositionResolver
import org.opentaint.ir.taint.configuration.TypeMatches
import org.opentaint.dataflow.ap.ifds.FactReader
import org.opentaint.dataflow.ap.ifds.PositionAccess
import org.opentaint.dataflow.ap.ifds.taint.FactAwareConditionEvaluatorWithAssumptions
import org.opentaint.dataflow.ap.ifds.taint.ResultWithFactAssumptions
import org.opentaint.dataflow.ap.ifds.taint.TaintSinkTracker.FactAssumption
import org.opentaint.dataflow.jvm.util.JIRTraits
import org.opentaint.util.Maybe
import org.opentaint.util.onSome

class JIRFactAwareConditionEvaluatorWithAssumptions(
    traits: JIRTraits,
    factReader: FactReader,
    private val accessPathResolver: PositionResolver<Maybe<List<PositionAccess>>>,
    positionResolver: PositionResolver<Maybe<CommonValue>>,
) : FactAwareConditionEvaluatorWithAssumptions {
    private val factAwareConditionEvaluator = JIRFactAwareConditionEvaluator(
        traits, listOf(factReader), accessPathResolver, positionResolver
    )

    private var hasEvaluatedContainsMark: Boolean = false

    override fun evalWithAssumptions(condition: Condition): List<ResultWithFactAssumptions> = try {
        hasEvaluatedContainsMark = false
        val result = condition.accept(this).filter { it.result }
        if (hasEvaluatedContainsMark) result else emptyList()
    } finally {
        hasEvaluatedContainsMark = false
    }

    override fun visit(condition: ContainsMark): List<ResultWithFactAssumptions> {
        val result = condition.accept(factAwareConditionEvaluator)
        hasEvaluatedContainsMark = hasEvaluatedContainsMark || result
        if (result) {
            return true.withoutAssumptions
        }

        val results = mutableListOf<ResultWithFactAssumptions>()
        results += false.withoutAssumptions

        accessPathResolver.resolve(condition.position).onSome { values ->
            for (value in values) {
                val assumedFact = FactAssumption(condition.mark, value)
                results += ResultWithFactAssumptions(true, setOf(assumedFact))
            }
        }

        return results
    }

    override fun visit(condition: Not): List<ResultWithFactAssumptions> {
        if (condition.arg is ContainsMark) {
            return trueWithoutAssumptions
        }

        return condition.arg.accept(this).mapNotNull {
            if (it.result) return@mapNotNull null

            it.copy(result = true)
        }
    }

    override fun visit(condition: And): List<ResultWithFactAssumptions> {
        val args = condition.args.map { it.accept(this).filter { it.result } }
        return args.cartesianProductMapTo { results ->
            var mergedAssumptions: Set<FactAssumption>? = null
            var mergedMutableAssumptions: MutableSet<FactAssumption>? = null

            for (r in results) {
                if (r.assumptions.isEmpty()) continue

                if (mergedAssumptions == null) {
                    mergedAssumptions = r.assumptions
                    continue
                }

                if (mergedMutableAssumptions == null) {
                    mergedMutableAssumptions = mergedAssumptions.toMutableSet()
                    mergedAssumptions = mergedMutableAssumptions
                }

                mergedMutableAssumptions.addAll(r.assumptions)
            }

            ResultWithFactAssumptions(true, mergedAssumptions ?: emptySet())
        }
    }

    override fun visit(condition: Or): List<ResultWithFactAssumptions> =
        condition.args.flatMap { it.accept(this).filter { it.result } }

    override fun visit(condition: ConstantTrue): List<ResultWithFactAssumptions> =
        condition.accept(factAwareConditionEvaluator).withoutAssumptions

    override fun visit(condition: IsConstant): List<ResultWithFactAssumptions> =
        condition.accept(factAwareConditionEvaluator).withoutAssumptions

    override fun visit(condition: ConstantEq): List<ResultWithFactAssumptions> =
        condition.accept(factAwareConditionEvaluator).withoutAssumptions

    override fun visit(condition: ConstantLt): List<ResultWithFactAssumptions> =
        condition.accept(factAwareConditionEvaluator).withoutAssumptions

    override fun visit(condition: ConstantGt): List<ResultWithFactAssumptions> =
        condition.accept(factAwareConditionEvaluator).withoutAssumptions

    override fun visit(condition: ConstantMatches): List<ResultWithFactAssumptions> =
        condition.accept(factAwareConditionEvaluator).withoutAssumptions

    override fun visit(condition: TypeMatches): List<ResultWithFactAssumptions> =
        condition.accept(factAwareConditionEvaluator).withoutAssumptions

    companion object {
        private val trueWithoutAssumptions = listOf(ResultWithFactAssumptions(result = true, assumptions = emptySet()))
        private val falseWithoutAssumptions =
            listOf(ResultWithFactAssumptions(result = false, assumptions = emptySet()))

        private val Boolean.withoutAssumptions: List<ResultWithFactAssumptions>
            get() = if (this) trueWithoutAssumptions else falseWithoutAssumptions

        private inline fun <reified T, R> List<List<T>>.cartesianProductMapTo(body: (Array<T>) -> R): List<R> {
            val resultSize = fold(1) { acc, lst -> acc * lst.size }
            if (resultSize == 0) return emptyList()

            val result = mutableListOf<R>()
            val chunk = arrayOfNulls<T>(size)
            for (chunkIdx in 0 until resultSize) {

                var currentChunkPos = chunkIdx
                for (i in indices) {
                    val lst = this[i]
                    val lstSize = lst.size
                    chunk[i] = lst[currentChunkPos % lstSize]
                    currentChunkPos /= lstSize
                }

                @Suppress("UNCHECKED_CAST")
                result += body(chunk as Array<T>)
            }

            return result
        }
    }
}
