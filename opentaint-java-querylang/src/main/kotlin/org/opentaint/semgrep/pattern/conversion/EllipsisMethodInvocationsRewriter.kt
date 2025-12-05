package org.opentaint.org.opentaint.semgrep.pattern.conversion

import org.opentaint.semgrep.pattern.EllipsisMethodInvocations
import org.opentaint.semgrep.pattern.NormalizedSemgrepRule
import org.opentaint.semgrep.pattern.SemgrepJavaPattern

fun rewriteEllipsisMethodInvocations(rule: NormalizedSemgrepRule): List<NormalizedSemgrepRule> {
    val rewriter = object : PatternRewriter {
        override fun createEllipsisMethodInvocations(obj: SemgrepJavaPattern): List<SemgrepJavaPattern> {
            return listOf(
                obj, // Skip it
                EllipsisMethodInvocations(obj), // Don't skip it, will be replaced with single call
            )
        }
    }

    return rewriter.safeRewrite(rule) {
        error("No failures expected")
    }
}
