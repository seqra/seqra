package org.opentaint.semgrep.pattern.conversion

import org.opentaint.semgrep.pattern.conversion.generateMethodInvocation
import org.opentaint.semgrep.pattern.Ellipsis
import org.opentaint.semgrep.pattern.NormalizedSemgrepRule
import org.opentaint.semgrep.pattern.SemgrepJavaPattern
import org.opentaint.semgrep.pattern.TypeName

const val generatedAnyValueGeneratorMethodName = "__anyValue__"

fun rewriteAssignEllipsis(rule: NormalizedSemgrepRule): List<NormalizedSemgrepRule> {
    val rewriter = object : PatternRewriter {
        override fun createVariableAssignment(
            type: TypeName?,
            variable: SemgrepJavaPattern,
            value: SemgrepJavaPattern?
        ): List<SemgrepJavaPattern> {
            if (value !is Ellipsis) {
                return super.createVariableAssignment(type, variable, value)
            }

            return super.createVariableAssignment(type, variable, anyValueCall)
        }
    }

    return rewriter.safeRewrite(rule) {
        error("No failures expected")
    }
}

private val anyValueCall by lazy {
    generateMethodInvocation(generatedAnyValueGeneratorMethodName, emptyList())
}