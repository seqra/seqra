package org.opentaint.org.opentaint.semgrep.pattern.conversion

import org.opentaint.org.opentaint.semgrep.pattern.ConcreteName
import org.opentaint.org.opentaint.semgrep.pattern.MethodInvocation
import org.opentaint.org.opentaint.semgrep.pattern.Name
import org.opentaint.org.opentaint.semgrep.pattern.NormalizedSemgrepRule
import org.opentaint.org.opentaint.semgrep.pattern.SemgrepJavaPattern
import org.opentaint.org.opentaint.semgrep.pattern.TypeName
import org.opentaint.org.opentaint.semgrep.pattern.TypedMetavar
import org.opentaint.org.opentaint.semgrep.pattern.map

private const val GeneratedObjMetaVarPrefix = "__OBJ#"
private const val GeneratedObjMetaVarSuffix = "__"

fun isGeneratedMethodInvocationObjMetaVar(metaVar: String): Boolean =
    metaVar.startsWith(GeneratedObjMetaVarPrefix)

fun rewriteMethodInvocationObj(rule: NormalizedSemgrepRule): NormalizedSemgrepRule {
    val nameMetaVars = hashMapOf<List<Name>, String>()

    val rewriter = object : PatternRewriter {
        override fun rewriteMethodInvocation(mi: MethodInvocation): SemgrepJavaPattern {
            val objPattern = mi.obj ?: return super.rewriteMethodInvocation(mi)

            val parts = tryExtractPatternDotSeparatedParts(objPattern)?.ifEmpty { null }
                ?: return super.rewriteMethodInvocation(mi)

            val lastPart = parts.last()
            // todo: consider a field access, not type
            if (lastPart is ConcreteName && lastPart.name.firstOrNull()?.isLowerCase() != false) {
                return super.rewriteMethodInvocation(mi)
            }

            val freshMetaVar = nameMetaVars.getOrPut(parts) {
                "$GeneratedObjMetaVarPrefix${nameMetaVars.size}$GeneratedObjMetaVarSuffix"
            }

            val type = TypeName(parts)
            val newObj = TypedMetavar(freshMetaVar, type)
            return super.rewriteMethodInvocation(mi.copy(obj = newObj))
        }
    }

    return rule.map {
        rewriter.safeRewrite(it) { error("No failures expected") }
    }
}

