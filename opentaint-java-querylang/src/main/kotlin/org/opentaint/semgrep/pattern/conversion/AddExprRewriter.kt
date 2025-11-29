package org.opentaint.semgrep.pattern.conversion

import org.opentaint.semgrep.pattern.ConcreteName
import org.opentaint.semgrep.pattern.MethodInvocation
import org.opentaint.semgrep.pattern.NoArgs
import org.opentaint.semgrep.pattern.NormalizedSemgrepRule
import org.opentaint.semgrep.pattern.PatternArgumentPrefix
import org.opentaint.semgrep.pattern.SemgrepJavaPattern
import org.opentaint.semgrep.pattern.map

// todo: rewrite all AddExpr as string concat for now
// we can consider split on string/non-string
// or opentaint.plus utility method with special handling in engine
fun rewriteAddExpr(rule: NormalizedSemgrepRule): NormalizedSemgrepRule =
    rule.map { rewritePatternAddExpr(it) }

const val opentaintStringConcatMethodName = "__opentaintStringConcat__"

private fun rewritePatternAddExpr(pattern: SemgrepJavaPattern): SemgrepJavaPattern {
    val rewriter = object : PatternRewriter {
        override fun createAddExpr(left: SemgrepJavaPattern, right: SemgrepJavaPattern): SemgrepJavaPattern =
            generateStringConcat(left, right)
    }

    return rewriter.safeRewrite(pattern) {
        error("No failures expected")
    }
}

private fun generateStringConcat(first: SemgrepJavaPattern, second: SemgrepJavaPattern): SemgrepJavaPattern {
    val args = PatternArgumentPrefix(first, PatternArgumentPrefix(second, NoArgs))
    return MethodInvocation(ConcreteName(opentaintStringConcatMethodName), obj = null, args)
}
