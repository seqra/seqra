package org.opentaint.org.opentaint.semgrep.pattern

import org.opentaint.ir.api.jvm.JIRMethod
import org.opentaint.ir.api.jvm.cfg.JIRExpr
import org.opentaint.ir.api.jvm.cfg.JIRExprVisitor
import org.opentaint.ir.api.jvm.cfg.JIRInst
import org.opentaint.org.opentaint.semgrep.pattern.SemgrepMatchingResult.Companion.noMatch

private class ExprPatternSearcher(
    val pattern: SemgrepJavaPattern,
    val matcher: SemgrepJavaPatternMatcher,
    val position: ExprPosition,
) : JIRExprVisitor.Default<SemgrepMatchingResult> {
    override fun defaultVisitJIRExpr(expr: JIRExpr): SemgrepMatchingResult {
        val curMatches = matcher.match(position, pattern, expr)
        val childMatches = expr.operands.map { it.accept(this) }
        val childMatchesUnited = childMatches.fold(noMatch) { acc, elem -> uniteMatches(acc, elem) }
        return uniteMatches(curMatches, childMatchesUnited)
    }
}

fun searchMatches(matcher: SemgrepJavaPatternMatcher, pattern: SemgrepJavaPattern, inst: JIRInst): SemgrepMatchingResult {
    val curMatches = matcher.match(pattern, inst)
    val position = ExprPosition(inst, isLValue = false)
    val searcher = ExprPatternSearcher(pattern, matcher, position)
    val declaration = inst.isVariableDeclaration()
    val children = inst.operands.filter {
        it != declaration
    }
    val childMatches = children.map { it.accept(searcher) }
    val childMatchesUnited = childMatches.fold(noMatch) { acc, elem -> uniteMatches(acc, elem) }
    return uniteMatches(curMatches, childMatchesUnited)
}

fun searchMatches(
    matcher: SemgrepJavaPatternMatcher,
    pattern: SemgrepJavaPattern,
    patternInfo: PatternInfo,
    method: JIRMethod,
): SemgrepMatchingResult {
    var result = matcher.match(pattern, method)
    if (patternInfo.matchesOnlyMethodDeclaration) {
        return result
    }
    for (i in 0..<method.instList.size) {
        val cur = searchMatches(matcher, pattern, method.instList[i])
        result = uniteMatches(result, cur)
        if (patternInfo.mayMatchInstList) {
            for (j in i + 1..method.instList.size) {
                val curBlock = matcher.matchInstList(pattern, method, from = i, to = j)
                result = uniteMatches(result, curBlock)
            }
        }
    }
    return result
}
