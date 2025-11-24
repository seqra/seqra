package org.opentaint.org.opentaint.semgrep.pattern.conversion

import org.opentaint.org.opentaint.semgrep.pattern.ConcreteName
import org.opentaint.org.opentaint.semgrep.pattern.FieldAccess
import org.opentaint.org.opentaint.semgrep.pattern.Identifier
import org.opentaint.org.opentaint.semgrep.pattern.SemgrepJavaPattern

fun tryExtractPatternDotSeparatedParts(pattern: SemgrepJavaPattern): List<String>? {
    return when (pattern) {
        is Identifier -> {
            listOf(pattern.name)
        }

        is FieldAccess -> {
            val field = (pattern.fieldName as? ConcreteName)?.name
                ?: return null
            val objPattern = (pattern.obj as? FieldAccess.ObjectPattern)?.pattern
                ?: return null

            tryExtractPatternDotSeparatedParts(objPattern)?.let { it + field }
        }

        else -> {
            null
        }
    }
}

fun patternFromDotSeparatedParts(dotSeparatedParts: List<String>): SemgrepJavaPattern {
    check(dotSeparatedParts.isNotEmpty())

    if (dotSeparatedParts.size == 1) {
        return Identifier(dotSeparatedParts.single())
    }

    val firstIdentifier = Identifier(dotSeparatedParts.first())
    val names = dotSeparatedParts.drop(1).map { ConcreteName(it) }
    return names.fold(firstIdentifier as SemgrepJavaPattern) { result, name ->
        FieldAccess(name, FieldAccess.ObjectPattern(result))
    }
}
