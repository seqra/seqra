package org.opentaint.semgrep.pattern

import mu.KLogging
import org.opentaint.dataflow.ap.ifds.TaintMarkAccessor
import org.opentaint.dataflow.ap.ifds.access.InitialFactAp
import org.opentaint.semgrep.pattern.conversion.MetavarAtom

sealed interface Mark {
    data class StringMark(val mark: String) : Mark

    data object ArtificialMark : Mark

    data object StateMark : Mark

    data object TaintMark : Mark

    data class RuleUniqueMarkPrefix(
        val ruleId: String,
        val idx: Int,
        val classifier: String? = null,
    ) {
        private val classifierPrefix = classifier?.let { "${classifier}_" }.orEmpty()
        private val ruleClassifier = "${classifierPrefix}_$idx"

        fun metaVarState(metaVar: MetavarAtom, state: Int): GeneratedMark =
            GeneratedMark(ruleId, ruleClassifier, metaVarStr(metaVar), "$state")

        fun artificialState(state: String): GeneratedMark =
            GeneratedMark(ruleId, ruleClassifier, StateName, state)

        fun createTaintMark(label: String): GeneratedMark =
            GeneratedMark(ruleId, ruleClassifier, TaintName, label)
    }

    data class GeneratedMark(
        val ruleId: String,
        val ruleClassifier: String,
        val variablesStr: String,
        val value: String,
    ) {
        fun taintMarkStr(): String = markToStr(this)
    }

    companion object {
        private const val StateName = "_<S>_"
        private const val TaintName = "_<T>_"
        private const val MetaVarSeparator = "_&_"
        private const val GeneratedMarkPartSeparator = ";"

        private fun metaVarStr(metaVar: MetavarAtom): String =
            metaVar.basics.joinToString(MetaVarSeparator)

        private fun parseMetaVarStr(str: String): MetavarAtom =
            MetavarAtom.create(str.split(MetaVarSeparator).map { MetavarAtom.create(it) })

        private fun markToStr(mark: GeneratedMark): String =
            listOf(mark.ruleId, mark.ruleClassifier, mark.variablesStr, mark.value)
                .joinToString(GeneratedMarkPartSeparator)

        fun parseMark(markStr: String): GeneratedMark =
            tryParseMark(markStr)
                ?: error("Mark is not generated: $markStr")

        private fun tryParseMark(markStr: String): GeneratedMark? {
            val parts = markStr.split(GeneratedMarkPartSeparator)
            if (parts.size != 4) return null
            val (rid, rc, vs, v) = parts
            return GeneratedMark(rid, rc, vs, v)
        }

        private val logger = object : KLogging() {}.logger

        fun getMarkFromString(rawMark: String): Mark {
            val mark = tryParseMark(rawMark)
                ?: return StringMark(rawMark) // running with config

            if (mark.variablesStr == StateName) {
                return StateMark
            }

            if (mark.variablesStr == TaintName) {
                if (mark.value.isBlank()) return TaintMark
                return StringMark(mark.value)
            }

            val metaVars = parseMetaVarStr(mark.variablesStr)
            if (metaVars.basics.any { it.isArtificial }) {
                return ArtificialMark
            }

            val metaVarsName = metaVars.basics.joinToString(" or ")
            return StringMark(metaVarsName)
        }

        fun InitialFactAp.getMark(): Mark {
            val taintMarks = getAllAccessors().filterIsInstance<TaintMarkAccessor>()
            if (taintMarks.size != 1) {
                logger.error { "Expected exactly one taint mark but got ${taintMarks.size}!" }
            }
            if (taintMarks.isEmpty()) {
                return TaintMark
            }
            return getMarkFromString(taintMarks.first().mark)
        }
    }
}
