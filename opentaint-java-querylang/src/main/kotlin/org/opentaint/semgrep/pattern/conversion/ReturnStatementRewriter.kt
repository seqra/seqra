package org.opentaint.semgrep.pattern.conversion

import org.opentaint.org.opentaint.semgrep.pattern.conversion.PatternRewriter
import org.opentaint.org.opentaint.semgrep.pattern.conversion.safeRewrite
import org.opentaint.semgrep.pattern.ConcreteName
import org.opentaint.semgrep.pattern.MethodInvocation
import org.opentaint.semgrep.pattern.NoArgs
import org.opentaint.semgrep.pattern.NormalizedSemgrepRule
import org.opentaint.semgrep.pattern.PatternArgumentPrefix
import org.opentaint.semgrep.pattern.SemgrepJavaPattern

const val opentaintReturnValueMethod = "__opentaintReturnValue__"

fun rewriteReturnStatement(rule: NormalizedSemgrepRule): List<NormalizedSemgrepRule> {
    val rewriter = object : PatternRewriter {
        override fun createReturnStmt(value: SemgrepJavaPattern?): List<SemgrepJavaPattern> {
            val valuePattern = value ?: return super.createReturnStmt(value)
            val args = PatternArgumentPrefix(valuePattern, NoArgs)
            return listOf(MethodInvocation(ConcreteName(opentaintReturnValueMethod), obj = null, args))
        }
    }

    return rewriter.safeRewrite(rule) { error("No failures expected") }
}
