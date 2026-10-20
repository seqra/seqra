package org.opentaint.semgrep.pattern.conversion

import org.opentaint.dataflow.configuration.python.serialized.SerializedPythonRule
import org.opentaint.semgrep.pattern.JoinRuleMetavarExpected
import org.opentaint.semgrep.pattern.MetaVarConstraint
import org.opentaint.semgrep.pattern.python.Metavar
import org.opentaint.semgrep.pattern.python.SemgrepPythonPattern
import org.opentaint.semgrep.pattern.python.SemgrepPythonPatternParser
import org.opentaint.semgrep.pattern.SemgrepRuleLoadStepTrace

class PythonLanguageStrategy : LanguageStrategy<SemgrepPythonPattern, SerializedPythonRule> {
    override val language = "python"
    override val parser = SemgrepPythonPatternParser()
    override val rewriter = PythonRuleRewriter()
    override val converter = PythonPatternToActionListConverter()
    override val typeOps = PythonTypeOps
    override val taintRuleStrategy = PythonTaintStrategy

    override fun extractMetaVarConstraint(pattern: SemgrepPythonPattern): MetaVarConstraint? = null

    override fun parseMetaVar(metaVar: String, trace: SemgrepRuleLoadStepTrace): MetavarAtom? {
        val parsed = parser.parseOrNull(metaVar, trace) ?: return null
        if (parsed !is Metavar) {
            trace.error(JoinRuleMetavarExpected(metaVar))
            return null
        }
        return MetavarAtom.create(parsed.name)
    }

    companion object {
        const val ATTR_READ_AUX_FN_PREFIX = "$$<attrread>$$"

        fun attrReadAuxFnName(attr: String) = "$ATTR_READ_AUX_FN_PREFIX$attr"

        fun attrReadAttrOrNull(name: String): String? =
            if (name.contains(ATTR_READ_AUX_FN_PREFIX)) name.replace(ATTR_READ_AUX_FN_PREFIX, "") else null

        const val FIELD_AUX_MODIFIER = "$$<field-modifier>$$"
        const val FIELD_NAME_SEPARATOR = "$$<dot>$$"
        const val INDEX_AUX_FIELD_NAME = "$$[*]$$"

        fun joinFieldNames(prev: String, next: String) = "$prev$FIELD_NAME_SEPARATOR$next"

        fun splitFieldNames(joinedName: String): List<String> = joinedName.split(FIELD_NAME_SEPARATOR)

        const val KWARG_CLASSIFIER_PREFIX = "$$<kwarg>$$"

        fun kwargClassifier(name: String) = "$KWARG_CLASSIFIER_PREFIX$name"

        fun kwargClassifierNameOrNull(classifier: String): String? =
            if (classifier.startsWith(KWARG_CLASSIFIER_PREFIX)) classifier.removePrefix(KWARG_CLASSIFIER_PREFIX) else null
    }
}
