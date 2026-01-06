package org.opentaint.semgrep.pattern.conversion

import org.opentaint.org.opentaint.semgrep.pattern.conversion.generateMethodInvocation
import org.opentaint.semgrep.pattern.NormalizedSemgrepRule
import org.opentaint.semgrep.pattern.SemgrepJavaPattern

// todo: rewrite all AddExpr as string concat for now
// we can consider split on string/non-string
// or opentaint.plus utility method with special handling in engine
fun rewriteAddExpr(rule: NormalizedSemgrepRule): List<NormalizedSemgrepRule> {
    val rewriter = object : PatternRewriter {
        override fun createAddExpr(left: SemgrepJavaPattern, right: SemgrepJavaPattern): List<SemgrepJavaPattern> =
            listOf(generateStringConcat(left, right))
    }

    return rewriter.safeRewrite(rule) {
        error("No failures expected")
    }
}

const val generatedStringConcatMethodName = "__stringConcat__"

private fun generateStringConcat(first: SemgrepJavaPattern, second: SemgrepJavaPattern): SemgrepJavaPattern {
    return generateMethodInvocation(generatedStringConcatMethodName, listOf(first, second))
}
