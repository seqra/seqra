package org.opentaint.semgrep.pattern.conversion

import org.opentaint.semgrep.pattern.AnonymousName
import org.opentaint.semgrep.pattern.ConcreteName
import org.opentaint.semgrep.pattern.Ellipsis
import org.opentaint.semgrep.pattern.Metavar
import org.opentaint.semgrep.pattern.MetavarName
import org.opentaint.semgrep.pattern.Name
import org.opentaint.semgrep.pattern.NormalizedSemgrepRule
import org.opentaint.semgrep.pattern.PatternSequence
import org.opentaint.semgrep.pattern.SemgrepJavaPattern
import org.opentaint.semgrep.pattern.StarMetavarName
import org.opentaint.semgrep.pattern.TypeName

// todo: for now we rewrite all catch statements as typed assign
fun rewriteCatchStatement(rule: NormalizedSemgrepRule<SemgrepJavaPattern>): List<NormalizedSemgrepRule<SemgrepJavaPattern>> {
    val rewriter = object : PatternRewriter {
        override fun createCatchStatement(
            exceptionTypes: List<TypeName>,
            exceptionVariable: Name,
            handlerBlock: SemgrepJavaPattern
        ): List<SemgrepJavaPattern> {
            val exceptionMetaVar = when (exceptionVariable) {
                is ConcreteName,
                is AnonymousName -> return super.createCatchStatement(exceptionTypes, exceptionVariable, handlerBlock)

                is MetavarName -> Metavar(exceptionVariable.metavarName, star = false)
                is StarMetavarName -> Metavar(exceptionVariable.metavarName, star = true)
            }

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
