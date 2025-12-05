package org.opentaint.semgrep.pattern.conversion

import org.opentaint.org.opentaint.semgrep.pattern.conversion.PatternRewriter
import org.opentaint.org.opentaint.semgrep.pattern.conversion.safeRewrite
import org.opentaint.semgrep.pattern.ConcreteName
import org.opentaint.semgrep.pattern.MethodArguments
import org.opentaint.semgrep.pattern.Name
import org.opentaint.semgrep.pattern.NormalizedSemgrepRule
import org.opentaint.semgrep.pattern.SemgrepJavaPattern
import org.opentaint.semgrep.pattern.TypeName
import org.opentaint.semgrep.pattern.TypedMetavar

private const val GeneratedObjMetaVarPrefix = "__OBJ#"
private const val GeneratedObjMetaVarSuffix = "__"

fun isGeneratedMethodInvocationObjMetaVar(metaVar: String): Boolean =
    metaVar.startsWith(GeneratedObjMetaVarPrefix)

fun rewriteMethodInvocationObj(rule: NormalizedSemgrepRule): List<NormalizedSemgrepRule> {
    val nameMetaVars = hashMapOf<List<Name>, String>()

    val rewriter = object : PatternRewriter {
        override fun createMethodInvocation(
            methodName: Name,
            obj: SemgrepJavaPattern?,
            args: MethodArguments
        ): List<SemgrepJavaPattern> {
            val newObj = obj?.let(::overwriteObj)
            return super.createMethodInvocation(methodName, newObj, args)
        }

        override fun createEllipsisMethodInvocations(obj: SemgrepJavaPattern): List<SemgrepJavaPattern> {
            val newObj = overwriteObj(obj)
            return super.createEllipsisMethodInvocations(newObj)
        }

        private fun overwriteObj(obj: SemgrepJavaPattern): SemgrepJavaPattern {
            val parts = tryExtractPatternDotSeparatedParts(obj)?.ifEmpty { null }
                ?: return obj

            val lastPart = parts.last()
            // todo: consider a field access, not type
            if (lastPart is ConcreteName && lastPart.name.firstOrNull()?.isLowerCase() != false) {
                return obj
            }

            val freshMetaVar = nameMetaVars.getOrPut(parts) {
                "$GeneratedObjMetaVarPrefix${nameMetaVars.size}$GeneratedObjMetaVarSuffix"
            }

            val type = TypeName(parts)
            val newObj = TypedMetavar(freshMetaVar, type)
            return newObj
        }
    }

    return rewriter.safeRewrite(rule) { error("No failures expected") }
}