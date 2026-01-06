package org.opentaint.semgrep.pattern.conversion

import org.opentaint.org.opentaint.semgrep.pattern.conversion.generateMethodInvocation
import org.opentaint.semgrep.pattern.NormalizedSemgrepRule
import org.opentaint.semgrep.pattern.SemgrepJavaPattern

const val generatedReturnValueMethod = "__returnValue__"

fun rewriteReturnStatement(rule: NormalizedSemgrepRule): List<NormalizedSemgrepRule> {
    val rewriter = object : PatternRewriter {
        override fun createReturnStmt(value: SemgrepJavaPattern?): List<SemgrepJavaPattern> {
            val valuePattern = value ?: return super.createReturnStmt(value)
            return listOf(generateMethodInvocation(generatedReturnValueMethod, listOf(valuePattern)))
        }
    }

    return rewriter.safeRewrite(rule) { error("No failures expected") }
}
