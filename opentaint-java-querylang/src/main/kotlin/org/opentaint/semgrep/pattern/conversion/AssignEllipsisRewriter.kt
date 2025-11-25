package org.opentaint.org.opentaint.semgrep.pattern.conversion

import org.opentaint.org.opentaint.semgrep.pattern.ConcreteName
import org.opentaint.org.opentaint.semgrep.pattern.Ellipsis
import org.opentaint.org.opentaint.semgrep.pattern.MethodInvocation
import org.opentaint.org.opentaint.semgrep.pattern.NoArgs
import org.opentaint.org.opentaint.semgrep.pattern.NormalizedSemgrepRule
import org.opentaint.org.opentaint.semgrep.pattern.SemgrepJavaPattern
import org.opentaint.org.opentaint.semgrep.pattern.TypeName
import org.opentaint.org.opentaint.semgrep.pattern.map

const val opentaintAnyValueGeneratorMethodName = "__opentaintAnyValue__"

fun rewriteAssignEllipsis(rule: NormalizedSemgrepRule): NormalizedSemgrepRule =
    rule.map { rewritePatternAssignEllipsis(it) }

private val anyValueCall by lazy {
    MethodInvocation(ConcreteName(opentaintAnyValueGeneratorMethodName), obj = null, NoArgs)
}

private fun rewritePatternAssignEllipsis(pattern: SemgrepJavaPattern): SemgrepJavaPattern {
    val rewriter = object : PatternRewriter {
        override fun createVariableAssignment(
            type: TypeName?,
            variable: SemgrepJavaPattern,
            value: SemgrepJavaPattern?
        ): SemgrepJavaPattern {
            if (value !is Ellipsis) {
                return super.createVariableAssignment(type, variable, value)
            }

            return super.createVariableAssignment(type, variable, anyValueCall)
        }
    }

    return rewriter.safeRewrite(pattern) {
        error("No failures expected")
    }
}
