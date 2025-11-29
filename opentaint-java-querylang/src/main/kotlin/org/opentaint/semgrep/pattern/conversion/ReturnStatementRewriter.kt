package org.opentaint.semgrep.pattern.conversion

import org.opentaint.semgrep.pattern.ConcreteName
import org.opentaint.semgrep.pattern.MethodInvocation
import org.opentaint.semgrep.pattern.NoArgs
import org.opentaint.semgrep.pattern.NormalizedSemgrepRule
import org.opentaint.semgrep.pattern.PatternArgumentPrefix
import org.opentaint.semgrep.pattern.SemgrepJavaPattern
import org.opentaint.semgrep.pattern.map

const val opentaintReturnValueMethod = "__opentaintReturnValue__"

fun rewriteReturnStatement(rule: NormalizedSemgrepRule): NormalizedSemgrepRule {
    val rewriter = object : PatternRewriter {
        override fun createReturnStmt(value: SemgrepJavaPattern?): SemgrepJavaPattern {
            val valuePattern = value ?: return super.createReturnStmt(value)
            val args = PatternArgumentPrefix(valuePattern, NoArgs)
            return MethodInvocation(ConcreteName(opentaintReturnValueMethod), obj = null, args)
        }
    }

    return rule.map {
        rewriter.safeRewrite(it) { error("No failures expected") }
    }
}
