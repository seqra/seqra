package org.opentaint.semgrep.pattern.conversion

import org.opentaint.semgrep.pattern.ConcreteName
import org.opentaint.semgrep.pattern.FieldAccess
import org.opentaint.semgrep.pattern.Name
import org.opentaint.semgrep.pattern.NormalizedSemgrepRule
import org.opentaint.semgrep.pattern.SemgrepJavaPattern
import org.opentaint.semgrep.pattern.StaticFieldAccess
import org.opentaint.semgrep.pattern.TypeName
import org.opentaint.semgrep.pattern.map

fun rewriteStaticFieldAccess(rule: NormalizedSemgrepRule): NormalizedSemgrepRule {
    val rewriter = object : PatternRewriter {
        override fun FieldAccess.rewriteFieldAccess(): SemgrepJavaPattern {
            val objPattern = when (obj) {
                is FieldAccess.ObjectPattern -> obj.pattern
                FieldAccess.SuperObject -> return this
            }

            val objPatternParts = tryExtractPatternDotSeparatedParts(objPattern)
                ?: return this

            if (!probablyStaticField(fieldName, objPatternParts)) {
                return this
            }

            return StaticFieldAccess(fieldName, TypeName(objPatternParts))
        }

        private fun probablyStaticField(fieldName: Name, obj: List<Name>): Boolean {
            if (fieldName is ConcreteName) {
                val name = fieldName.name
                if (name.all { !it.isLetter() || it.isUpperCase() }) return true
            }

            val classNameCandidate = obj.lastOrNull()
            if (classNameCandidate is ConcreteName) {
                val name = classNameCandidate.name
                if (name.firstOrNull()?.isUpperCase() == true) return true
            }

            return false
        }
    }

    return rule.map {
        rewriter.safeRewrite(it) { error("No failures expected") }
    }
}
