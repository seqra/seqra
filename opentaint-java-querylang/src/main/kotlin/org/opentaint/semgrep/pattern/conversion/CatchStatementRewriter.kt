package org.opentaint.semgrep.pattern.conversion

import org.opentaint.semgrep.pattern.ConcreteName
import org.opentaint.semgrep.pattern.Ellipsis
import org.opentaint.semgrep.pattern.Metavar
import org.opentaint.semgrep.pattern.MetavarName
import org.opentaint.semgrep.pattern.Name
import org.opentaint.semgrep.pattern.NormalizedSemgrepRule
import org.opentaint.semgrep.pattern.PatternSequence
import org.opentaint.semgrep.pattern.SemgrepJavaPattern
import org.opentaint.semgrep.pattern.TypeName

// todo: for now we rewrite all catch statements as typed assign
fun rewriteCatchStatement(rule: NormalizedSemgrepRule): List<NormalizedSemgrepRule> {
    val rewriter = object : PatternRewriter {
        override fun createCatchStatement(
            exceptionTypes: List<TypeName>,
            exceptionVariable: Name,
            handlerBlock: SemgrepJavaPattern
        ): List<SemgrepJavaPattern> {
            val exceptionMetaVarName = when (exceptionVariable) {
                is ConcreteName -> return super.createCatchStatement(exceptionTypes, exceptionVariable, handlerBlock)
                is MetavarName -> exceptionVariable.metavarName
            }

            val exceptionMetaVar = Metavar(exceptionMetaVarName)

            return exceptionTypes.flatMap { type ->
                super.createVariableAssignment(type, exceptionMetaVar, value = Ellipsis).map { assign ->
                   PatternSequence(assign, handlerBlock)
                }
            }
        }
    }

    return rewriter.safeRewrite(rule) {
        error("No failures expected")
    }
}
