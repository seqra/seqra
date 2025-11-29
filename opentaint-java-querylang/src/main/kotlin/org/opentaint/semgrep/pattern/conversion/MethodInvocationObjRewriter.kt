package org.opentaint.semgrep.pattern.conversion

import org.opentaint.semgrep.pattern.ConcreteName
import org.opentaint.semgrep.pattern.MethodArguments
import org.opentaint.semgrep.pattern.Name
import org.opentaint.semgrep.pattern.NormalizedSemgrepRule
import org.opentaint.semgrep.pattern.SemgrepJavaPattern
import org.opentaint.semgrep.pattern.TypeName
import org.opentaint.semgrep.pattern.TypedMetavar
import org.opentaint.semgrep.pattern.map

private const val GeneratedObjMetaVarPrefix = "__OBJ#"
private const val GeneratedObjMetaVarSuffix = "__"

fun isGeneratedMethodInvocationObjMetaVar(metaVar: String): Boolean =
    metaVar.startsWith(GeneratedObjMetaVarPrefix)

fun rewriteMethodInvocationObj(rule: NormalizedSemgrepRule): NormalizedSemgrepRule {
    val nameMetaVars = hashMapOf<List<Name>, String>()

    val rewriter = object : PatternRewriter {
        override fun createMethodInvocation(
            methodName: Name,
            obj: SemgrepJavaPattern?,
            args: MethodArguments
        ): SemgrepJavaPattern {
            val objPattern = obj ?: return super.createMethodInvocation(methodName, obj, args)

            val parts = tryExtractPatternDotSeparatedParts(objPattern)?.ifEmpty { null }
                ?: return super.createMethodInvocation(methodName, obj, args)

            val lastPart = parts.last()
            // todo: consider a field access, not type
            if (lastPart is ConcreteName && lastPart.name.firstOrNull()?.isLowerCase() != false) {
                return super.createMethodInvocation(methodName, obj, args)
            }

            val freshMetaVar = nameMetaVars.getOrPut(parts) {
                "$GeneratedObjMetaVarPrefix${nameMetaVars.size}$GeneratedObjMetaVarSuffix"
            }

            val type = TypeName(parts)
            val newObj = TypedMetavar(freshMetaVar, type)
            return super.createMethodInvocation(methodName, newObj, args)
        }
    }

    return rule.map {
        rewriter.safeRewrite(it) { error("No failures expected") }
    }
}

