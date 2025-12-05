package org.opentaint.semgrep.pattern

import kotlinx.coroutines.runBlocking
import org.opentaint.ir.api.jvm.JIRClasspath
import org.opentaint.ir.api.jvm.JIRMethod
import org.opentaint.ir.api.jvm.cfg.JIRAddExpr
import org.opentaint.ir.api.jvm.cfg.JIRArgument
import org.opentaint.ir.api.jvm.cfg.JIRAssignInst
import org.opentaint.ir.api.jvm.cfg.JIRBool
import org.opentaint.ir.api.jvm.cfg.JIRCallExpr
import org.opentaint.ir.api.jvm.cfg.JIRCallInst
import org.opentaint.ir.api.jvm.cfg.JIRExpr
import org.opentaint.ir.api.jvm.cfg.JIRFieldRef
import org.opentaint.ir.api.jvm.cfg.JIRInst
import org.opentaint.ir.api.jvm.cfg.JIRInstanceCallExpr
import org.opentaint.ir.api.jvm.cfg.JIRInt
import org.opentaint.ir.api.jvm.cfg.JIRLocalVar
import org.opentaint.ir.api.jvm.cfg.JIRNewExpr
import org.opentaint.ir.api.jvm.cfg.JIRReturnInst
import org.opentaint.ir.api.jvm.cfg.JIRStringConstant
import org.opentaint.ir.api.jvm.cfg.JIRThis
import org.opentaint.ir.impl.features.SyncUsagesExtension
import org.opentaint.ir.impl.features.hierarchyExt
import org.opentaint.jvm.graph.JApplicationGraphImpl
import org.opentaint.semgrep.pattern.SemgrepMatchingResult.Companion.noMatch
import org.opentaint.semgrep.pattern.SemgrepMatchingResult.Companion.single
import org.opentaint.semgrep.pattern.SemgrepMatchingResult.Companion.singleEmptyMatch

class SemgrepJavaPatternMatcher(
    private val cp: JIRClasspath,
    private val strategy: LocalVarStrategy = LocalVarStrategy.MAY,
) {
    private val constructorMethodName = "<init>"
    private val stringBuilderFullName = "java.lang.StringBuilder"
    private val stringBuilderFullNameAsTypeName = TypeName(
        stringBuilderFullName.split('.').map { ConcreteName(it) }
    )
    private val stringBuilderAppend = "append"
    private val stringFullName = "java.lang.String"
    private val toStringMethodName = "toString"

    private sealed interface MatchingKey
    private data class ExprMatchingKey(val expr: JIRExpr, val position: ExprPosition?) : MatchingKey
    private data class InstMatchingKey(val inst: JIRInst) : MatchingKey
    private data class ArgListPartition(val source: ArgumentProvider, val from: Int, val to: Int) : MatchingKey
    private data class MethodPartition(val method: JIRMethod, val from: Int, val to: Int) : MatchingKey
    private data class MethodMatchingKey(val method: JIRMethod) : MatchingKey
    private data class StringBuilderMatchingKey(val expr: JIRExpr, val position: ExprPosition) : MatchingKey

    private sealed interface ArgumentProvider {
        fun getArgumentByIndex(index: Int, cp: JIRClasspath): JIRExpr
    }

    private data class Invocation(val callExpr: JIRCallExpr) : ArgumentProvider {
        override fun getArgumentByIndex(index: Int, cp: JIRClasspath): JIRExpr = callExpr.args[index]
    }

    private data class Declaration(val method: JIRMethod) : ArgumentProvider {
        override fun getArgumentByIndex(index: Int, cp: JIRClasspath): JIRExpr {
            val parameter = method.parameters[index]
            val type = cp.findTypeOrNull(parameter.type.typeName)
                ?: error("Cannot find type ${parameter.type}")
            return JIRArgument.of(index = index, type = type, name = parameter.name)
        }
    }

    private val matchingCache = hashMapOf<MatchingKey, MutableMap<SemgrepJavaPattern, SemgrepMatchingResult>>()

    private val graph = JApplicationGraphImpl(cp, SyncUsagesExtension(runBlocking { cp.hierarchyExt() }, cp))

    private fun matchWithCache(
        key: MatchingKey,
        pattern: SemgrepJavaPattern,
        calculate: () -> SemgrepMatchingResult,
    ): SemgrepMatchingResult {
        val map = matchingCache.getOrPut(key) { hashMapOf() }
        val cachedValue = map[pattern]
        if (cachedValue != null) {
            return cachedValue
        }

        val result = calculate()

        map[pattern] = result
        return result
    }

    fun cleanCache() {
        matchingCache.clear()
    }

    fun match(
        position: ExprPosition?,
        pattern: SemgrepJavaPattern,
        expr: JIRExpr,
    ): SemgrepMatchingResult =
        matchWithCache(ExprMatchingKey(expr, position), pattern) {
            when (pattern) {
                Ellipsis -> singleEmptyMatch
                is Metavar -> single(matchMetavar(position, pattern, expr))
                ThisExpr -> matchThis(position, expr)
                is FieldAccess -> matchFieldAccess(position, pattern, expr)
                is AddExpr -> matchAddExpr(position, pattern, expr)
                is FormalArgument -> matchFormalArgument(position, pattern, expr)
                is TypedMetavar -> matchTypedMetavar(position, pattern, expr)
                is MethodInvocation -> matchMethodInvocation(position, pattern, expr)
                is EllipsisMethodInvocations -> matchEllipsisMethodInvocations(position, pattern, expr)
                is StringLiteral -> matchStringLiteral(position, pattern, expr)
                is StringEllipsis -> matchStringEllipsis(position, expr)
                is BoolConstant -> matchBoolConstant(position, pattern, expr)
                is ObjectCreation -> matchObjectCreation(position, pattern, expr)
                is PatternSequence,
                is MethodArguments,
                is ReturnStmt,
                is VariableAssignment,
                is MethodDeclaration,
                is EmptyPatternSequence,
                is Annotation,
                is ClassDeclaration,
                is NamedValue -> noMatch
                // we don't want to match concrete variable names
                // identifiers should be treated as class references (for example, for static fields)
                is Identifier -> noMatch
                else -> TODO()
            }
        }

    private fun matchStringBuilder(
        position: ExprPosition,
        pattern: SemgrepJavaPattern,
        expr: JIRExpr,
    ): SemgrepMatchingResult =
        matchWithCache(StringBuilderMatchingKey(expr, position), pattern) {
            check(expr.type.typeName == stringBuilderFullName) {
                "Unexpected expr in matchStringBuilder: $expr"
            }
            when (pattern) {
                is AddExpr -> {
                    matchStringBuilderAddExpr(position, pattern, expr)
                }
                else -> {
                    // return to normal match
                    val emptyStringBuilder = ObjectCreation(stringBuilderFullNameAsTypeName, NoArgs)
                    val newPattern = MethodInvocation(
                        methodName = ConcreteName(stringBuilderAppend),
                        obj = emptyStringBuilder,
                        args = PatternArgumentPrefix(pattern, rest = NoArgs)
                    )
                    match(position, newPattern, expr)
                }
            }
        }

    fun match(pattern: SemgrepJavaPattern, inst: JIRInst): SemgrepMatchingResult =
        matchWithCache(InstMatchingKey(inst), pattern) {
            when (pattern) {
                Ellipsis -> singleEmptyMatch
                is EllipsisMethodInvocations -> matchEllipsisMethodInvocations(pattern, inst)
                is ReturnStmt -> matchReturnStmt(pattern, inst)
                is VariableAssignment -> matchVariableAssignment(pattern, inst)
                is MethodInvocation -> matchMethodInvocation(pattern, inst)
                is PatternSequence,
                is Metavar,
                ThisExpr,
                is FieldAccess,
                is Identifier,
                is TypedMetavar,
                is AddExpr,
                is MethodArguments,
                is StringLiteral,
                is StringEllipsis,
                is ObjectCreation,
                is MethodDeclaration,
                is FormalArgument,
                is EmptyPatternSequence,
                is BoolConstant,
                is Annotation,
                is ClassDeclaration,
                is NamedValue -> noMatch
                else -> TODO()
            }
        }

    private fun matchArgList(
        position: ExprPosition?,
        pattern: MethodArguments,
        source: ArgumentProvider,
        from: Int,
        to: Int
    ): SemgrepMatchingResult = matchWithCache(ArgListPartition(source, from, to), pattern) {
        when (pattern) {
            is NoArgs -> matchNoArgs(from, to)
            is PatternArgumentPrefix -> matchPatternArgumentPrefix(position, pattern, source, from, to)
            is EllipsisArgumentPrefix -> matchEllipsisArguments(position, pattern, source, from, to)
        }
    }

    fun matchInstList(pattern: SemgrepJavaPattern, method: JIRMethod, from: Int, to: Int): SemgrepMatchingResult =
        matchWithCache(MethodPartition(method, from, to), pattern) {
            var result = when (pattern) {
                Ellipsis -> singleEmptyMatch
                is PatternSequence -> matchPatternSequence(pattern, method, from, to)
                is EmptyPatternSequence -> if (from >= to) singleEmptyMatch else noMatch
                is ObjectCreation,
                is Identifier,
                is Metavar,
                is MethodInvocation,
                is MethodArguments,
                is ReturnStmt,
                is AddExpr,
                is EllipsisMethodInvocations,
                is FieldAccess,
                is TypedMetavar,
                ThisExpr,
                is VariableAssignment,
                is StringLiteral,
                is StringEllipsis,
                is MethodDeclaration,
                is FormalArgument,
                is BoolConstant,
                is Annotation,
                is ClassDeclaration,
                is NamedValue -> noMatch
                else -> TODO()
            }

            if (to - from == 1) {
                val inst = method.instList[from]
                val instMatch = match(pattern, inst)
                result = uniteMatches(result, instMatch)
            }

            result
        }

    fun match(pattern: SemgrepJavaPattern, method: JIRMethod): SemgrepMatchingResult =
        matchWithCache(MethodMatchingKey(method), pattern) {
            when (pattern) {
                Ellipsis -> singleEmptyMatch
                is MethodDeclaration -> matchMethodDeclaration(pattern, method)
                is AddExpr,
                is EllipsisMethodInvocations,
                EmptyPatternSequence,
                is FieldAccess,
                is FormalArgument,
                is Identifier,
                is Metavar,
                is EllipsisArgumentPrefix,
                NoArgs,
                is PatternArgumentPrefix,
                is MethodInvocation,
                is ObjectCreation,
                is PatternSequence,
                is ReturnStmt,
                StringEllipsis,
                is StringLiteral,
                ThisExpr,
                is TypedMetavar,
                is VariableAssignment,
                is BoolConstant,
                is Annotation,
                is ClassDeclaration,
                is NamedValue -> noMatch
                else -> TODO()
            }
        }

    private fun matchStringBuilderAddExpr(position: ExprPosition, pattern: AddExpr, expr: JIRExpr): SemgrepMatchingResult {
        if (expr !is JIRInstanceCallExpr) {
            val exprs = expandLocalVar(position, expr)
            val variants = exprs.map { matchStringBuilder(it.second, pattern, it.first) }
            return mergeLocalVarVariantMatches(strategy, variants)
        }
        if (expr.method.name != stringBuilderAppend || expr.args.size != 1) {
            return noMatch
        }

        val arg = expr.args.single()
        val argMatches = match(position, pattern.right, arg)
        if (!argMatches.isMatch) {
            return noMatch
        }

        val objMatches = matchStringBuilder(position, pattern.left, expr.instance)
        return mergeMatchesForPartsOfPattern(graph, strategy, argMatches, objMatches)
    }

    private fun matchFormalArgument(
        position: ExprPosition?,
        pattern: FormalArgument,
        expr: JIRExpr
    ): SemgrepMatchingResult {
        if (expr !is JIRArgument || position != null) {
            return noMatch
        }

        val nameMatch = matchName(pattern.name, expr.name)
        val typeMatch = matchTypeName(pattern.type, expr.typeName)
        return mergeMatchesForPartsOfPattern(graph, strategy, nameMatch, typeMatch)
    }

    private fun matchMethodDeclaration(pattern: MethodDeclaration, method: JIRMethod): SemgrepMatchingResult {
        if (pattern.modifiers.isNotEmpty()) {
            TODO()
        }

        val nameMatch = matchName(pattern.name, method.name)
        if (!nameMatch.isMatch) {
            return noMatch
        }

        val typeMatch = if (pattern.returnType != null) {
            val typeMatch = matchTypeName(pattern.returnType, method.returnType.typeName)
            if (!typeMatch.isMatch) {
                return noMatch
            }
            typeMatch
        } else {
            singleEmptyMatch
        }

        var curMatches = mergeMatchesForPartsOfPattern(graph, strategy, nameMatch, typeMatch)

        val argMatches = matchArgList(
            position = null,
            pattern.args,
            Declaration(method),
            from = 0,
            to = method.parameters.size
        )
        if (!argMatches.isMatch) {
            return noMatch
        }

        curMatches = mergeMatchesForPartsOfPattern(graph, strategy, curMatches, argMatches)

        val bodyMatches = matchInstList(pattern.body, method, from = 0, to = method.instList.size)
        if (!bodyMatches.isMatch) {
            return noMatch
        }

        curMatches = mergeMatchesForPartsOfPattern(graph, strategy, curMatches, bodyMatches)
        return curMatches
    }

    private fun matchBoolConstant(position: ExprPosition?, pattern: BoolConstant, expr: JIRExpr): SemgrepMatchingResult {
        if (expr is JIRLocalVar) {
            val exprs = expandLocalVar(position, expr)
            val variants = exprs.map { match(it.second, pattern, it.first) }
            return mergeLocalVarVariantMatches(strategy, variants)
        }

        return when (expr) {
            is JIRBool -> if (expr.value == pattern.value) {
                singleEmptyMatch
            } else {
                noMatch
            }

            is JIRInt -> if (expr.value == 0 && !pattern.value || expr.value == 1 && pattern.value) {
                singleEmptyMatch
            } else {
                noMatch
            }

            else -> {
                noMatch
            }
        }
    }

    private fun matchObjectCreation(
        position: ExprPosition?,
        pattern: ObjectCreation,
        expr: JIRExpr,
    ): SemgrepMatchingResult {
        if (expr !is JIRNewExpr) {
            val exprs = expandLocalVar(position, expr)
            val variants = exprs.map { match(it.second, pattern, it.first) }
            return mergeLocalVarVariantMatches(strategy, variants)
        }

        check(position != null && position.inst is JIRAssignInst) {
            "Unexpected ExprPosition while matching object creation: $position"
        }

        val nextInsts = position.inst.location.method.flowGraph().successors(position.inst)
        if (nextInsts.size != 1) {
            return noMatch
        }
        val nextInst = nextInsts.single()

        if (((nextInst as? JIRCallInst)?.callExpr as? JIRInstanceCallExpr)?.instance != position.inst.lhv) {
            return noMatch
        }

        val callPattern = MethodInvocation(
            methodName = ConcreteName(constructorMethodName),
            obj = Ellipsis,
            args = pattern.args
        )
        return match(callPattern, nextInst)
    }

    private fun matchTypeName(patternName: TypeName, typeName: String): SemgrepMatchingResult {
        val metavarGroups = mutableMapOf<String, Int>()
        val regexPattern = "(?:.*\\.)?" + patternName.dotSeparatedParts.fold("") { acc, name ->
            val cur = when (name) {
                is ConcreteName -> {
                    name.name
                }
                is MetavarName -> {
                    var pattern = "(.*)"
                    val group = metavarGroups[name.metavarName]
                    if (group != null) {
                        pattern = "\\" + group
                    } else {
                        metavarGroups[name.metavarName] = metavarGroups.size + 1
                    }
                    pattern
                }
            }
            if (acc.isEmpty()) cur else "$acc\\.$cur"
        }

        val regex = regexPattern.toRegex()
        val match = regex.matchEntire(typeName)
            ?: return noMatch

        return single(
            SemgrepMatch(
                exprMetavars = emptyMap(),
                strMetavars = metavarGroups.entries.associate { (name, group) -> name to match.groupValues[group] }
            )
        )
    }

    private fun matchStringEllipsis(position: ExprPosition?, expr: JIRExpr): SemgrepMatchingResult {
        if (expr !is JIRStringConstant) {
            val exprs = expandLocalVar(position, expr)
            val variants = exprs.map { match(it.second, StringEllipsis, it.first) }
            return mergeLocalVarVariantMatches(strategy, variants)
        }

        return singleEmptyMatch
    }

    private fun matchStringLiteral(
        position: ExprPosition?,
        pattern: StringLiteral,
        expr: JIRExpr
    ): SemgrepMatchingResult {
        if (expr !is JIRStringConstant) {
            val exprs = expandLocalVar(position, expr)
            val variants = exprs.map { match(it.second, pattern, it.first) }
            return mergeLocalVarVariantMatches(strategy, variants)
        }

        return matchName(pattern.content, expr.value)
    }

    private fun matchVariableAssignment(pattern: VariableAssignment, inst: JIRInst): SemgrepMatchingResult {
        if (inst !is JIRAssignInst) {
            return noMatch
        }

        val variableMatches = match(ExprPosition(inst, isLValue = true), pattern.variable, inst.lhv)
        if (!variableMatches.isMatch) {
            return noMatch
        }

        var curMatches = if (pattern.type != null) {
            val typeMatches = matchTypeName(pattern.type, inst.lhv.typeName)
            mergeMatchesForPartsOfPattern(graph, strategy, variableMatches, typeMatches)
        } else {
            variableMatches
        }

        val valueMatches = match(ExprPosition(inst, isLValue = false), pattern.value ?: TODO(), inst.rhv)
        curMatches = mergeMatchesForPartsOfPattern(graph, strategy, curMatches, valueMatches)

        return curMatches
    }

    private fun matchPatternSequence(
        pattern: PatternSequence,
        method: JIRMethod,
        from: Int,
        to: Int
    ): SemgrepMatchingResult {
        var result = noMatch

        for (mid in from..to) {
            val first = matchInstList(pattern.first, method, from, mid)
            if (!first.isMatch) {
                continue
            }
            val second = matchInstList(pattern.second, method, mid, to)
            val cur = mergeMatchesForPartsOfPattern(graph, strategy, first, second)
            result = uniteMatches(result, cur)
        }

        return result
    }

    private fun matchNoArgs(from: Int, to: Int): SemgrepMatchingResult {
        return if (from >= to) {
            singleEmptyMatch
        } else {
            noMatch
        }
    }

    private fun matchPatternArgumentPrefix(
        position: ExprPosition?,
        pattern: PatternArgumentPrefix,
        source: ArgumentProvider,
        from: Int,
        to: Int,
    ): SemgrepMatchingResult {
        if (from >= to) {
            return noMatch
        }
        val curArg = source.getArgumentByIndex(from, cp)
        val argMatches = match(position, pattern.argument, curArg)
        return if (argMatches.isMatch) {
            val restMatches = matchArgList(position, pattern.rest, source, from + 1, to)
            mergeMatchesForPartsOfPattern(graph, strategy, argMatches, restMatches)
        } else {
            noMatch
        }
    }

    private fun matchEllipsisArguments(
        position: ExprPosition?,
        pattern: EllipsisArgumentPrefix,
        source: ArgumentProvider,
        from: Int,
        to: Int,
    ): SemgrepMatchingResult {
        var result = noMatch
        for (mid in from..to) {
            val curMatches = matchArgList(position, pattern.rest, source, mid, to)
            result = uniteMatches(result, curMatches)
        }
        return result
    }

    private fun expandLocalVar(position: ExprPosition?, expr: JIRExpr): Set<Pair<JIRExpr, ExprPosition>> {
        if (expr !is JIRLocalVar) {
            return emptySet()
        }
        check(position != null) {
            "ExprPosition must not be null for JIRLocalVar"
        }
        return expandLocalVar(position, expr, mutableSetOf())
    }

    private fun expandLocalVar(
        position: ExprPosition,
        expr: JIRLocalVar,
        visited: MutableSet<Pair<ExprPosition, JIRLocalVar>>,
    ): Set<Pair<JIRExpr, ExprPosition>> {
        val key = position to expr
        if (key in visited) {
            return emptySet()
        }
        visited.add(key)
        val curInst = position.inst
        if (curInst is JIRAssignInst && curInst.lhv == expr && position.isLValue) {
            val value = curInst.rhv
            return if (value !is JIRLocalVar) {
                setOf(value to ExprPosition(curInst, isLValue = false))
            } else {
                expandLocalVar(ExprPosition(curInst, isLValue = false), value, visited)
            }
        }
        val prevInsts = graph.predecessors(curInst)
        return prevInsts.flatMap { expandLocalVar(ExprPosition(it, isLValue = true), expr, visited) }.toSet()
    }

    private fun matchMetavar(position: ExprPosition?, pattern: Metavar, expr: JIRExpr): SemgrepMatch {
        val content = pattern.name to (expr to position)
        return SemgrepMatch(
            exprMetavars = mapOf(content),
            strMetavars = emptyMap(),
        )
    }

    private fun matchTypedMetavar(position: ExprPosition?, pattern: TypedMetavar, expr: JIRExpr): SemgrepMatchingResult {
        val typeMatch = matchTypeName(pattern.type, expr.type.typeName)

        val content = pattern.name to (expr to position)
        val match = SemgrepMatch(
            exprMetavars = mapOf(content),
            strMetavars = emptyMap(),
        )
        val metavarMatch = single(match)

        return mergeMatchesForPartsOfPattern(graph, strategy, typeMatch, metavarMatch)
    }

    private fun matchThis(position: ExprPosition?, expr: JIRExpr): SemgrepMatchingResult {
        if (expr is JIRThis) {
            return singleEmptyMatch
        }
        val exprs = expandLocalVar(position, expr)
        val variants = exprs.map { match(it.second, ThisExpr, it.first) }
        return mergeLocalVarVariantMatches(strategy, variants)
    }

    private fun convertFieldObjectPatternIntoTypeName(pattern: SemgrepJavaPattern): TypeName? =
        when (pattern) {
            is Identifier -> {
                TypeName(listOf(ConcreteName(pattern.name)))
            }
            is Metavar -> {
                TypeName(listOf(MetavarName(pattern.name)))
            }
            is FieldAccess -> {
                val name = pattern.fieldName
                (pattern.obj as? FieldAccess.ObjectPattern)?.let {
                    convertFieldObjectPatternIntoTypeName(it.pattern)?.dotSeparatedParts
                }?.let { prefix ->
                    TypeName(prefix + name)
                }
            }
            else -> {
                null
            }
        }

    private fun matchFieldAccess(position: ExprPosition?, pattern: FieldAccess, expr: JIRExpr): SemgrepMatchingResult {
        if (expr !is JIRFieldRef) {
            val exprs = expandLocalVar(position, expr)
            val variants = exprs.map { match(it.second, pattern, it.first) }
            return mergeLocalVarVariantMatches(strategy, variants)
        }

        val nameMatch = matchName(pattern.fieldName, expr.field.name)
        if (!nameMatch.isMatch) {
            return noMatch
        }

        val objMatch = when (pattern.obj) {
            is FieldAccess.ObjectPattern -> {
                val obj = expr.instance
                if (obj != null) {
                    match(position, pattern.obj.pattern, obj)
                } else {
                    val typePattern = convertFieldObjectPatternIntoTypeName(pattern.obj.pattern)
                        ?: return noMatch
                    matchTypeName(typePattern, expr.field.enclosingType.typeName)
                }
            }

            FieldAccess.SuperObject -> TODO()
        }

        return mergeMatchesForPartsOfPattern(graph, strategy, nameMatch, objMatch)
    }

    private fun matchAddExpr(position: ExprPosition?, pattern: AddExpr, expr: JIRExpr): SemgrepMatchingResult {
        if (expr.typeName == stringFullName) {
            return matchAddExprForString(position, pattern, expr)
        }

        if (expr !is JIRAddExpr) {
            val exprs = expandLocalVar(position, expr)
            val variants = exprs.map { match(it.second, pattern, it.first) }
            return mergeLocalVarVariantMatches(strategy, variants)
        }

        val leftMatches = match(position, pattern.left, expr.lhv)
        val rightMatches = match(position, pattern.right, expr.rhv)

        return mergeMatchesForPartsOfPattern(graph, strategy, leftMatches, rightMatches)
    }

    private fun matchAddExprForString(position: ExprPosition?, pattern: AddExpr, expr: JIRExpr): SemgrepMatchingResult {
        if (expr !is JIRInstanceCallExpr) {
            val exprs = expandLocalVar(position, expr)
            val variants = exprs.map { match(it.second, pattern, it.first) }
            return mergeLocalVarVariantMatches(strategy, variants)
        }

        check(position != null) {
            "Expected ExprPosition to be non-null in matchAddExprForString"
        }

        if (expr.method.name != toStringMethodName || expr.args.isNotEmpty() || expr.instance.typeName != stringBuilderFullName) {
            return noMatch
        }

        return matchStringBuilder(position, pattern, expr.instance)
    }

    private fun matchMethodInvocation(
        position: ExprPosition?,
        pattern: MethodInvocation,
        expr: JIRExpr,
    ): SemgrepMatchingResult {
        if (expr !is JIRCallExpr) {
            val exprs = expandLocalVar(position, expr)
            val variants = exprs.map { match(it.second, pattern, it.first) }
            return mergeLocalVarVariantMatches(strategy, variants)
        }

        val nameMatches = matchName(pattern.methodName, expr.method.name)
        if (!nameMatches.isMatch) {
            return noMatch
        }

        val argMatches = matchArgList(position, pattern.args, Invocation(expr), from = 0, to = expr.args.size)
        val nameAndArgMatches = mergeMatchesForPartsOfPattern(graph, strategy, nameMatches, argMatches)

        return if (expr is JIRInstanceCallExpr) {
            val objPattern = pattern.obj ?: ThisExpr
            val objMatches = match(position, objPattern, expr.instance)
            mergeMatchesForPartsOfPattern(graph, strategy, objMatches, nameAndArgMatches)
        } else if (pattern.obj == null) {
            nameAndArgMatches
        } else {
            convertFieldObjectPatternIntoTypeName(pattern.obj)?.let { typeName ->
                val typeMatch = matchTypeName(typeName, expr.method.enclosingType.typeName)
                mergeMatchesForPartsOfPattern(graph, strategy, typeMatch, nameAndArgMatches)
            } ?: noMatch
        }
    }

    private fun matchMethodInvocation(pattern: MethodInvocation, inst: JIRInst): SemgrepMatchingResult {
        if (inst !is JIRCallInst) {
            return noMatch
        }
        return match(ExprPosition(inst, isLValue = false), pattern, inst.callExpr)
    }

    // (exprPattern). ...
    private fun matchEllipsisMethodInvocations(
        position: ExprPosition?,
        pattern: EllipsisMethodInvocations,
        expr: JIRExpr
    ): SemgrepMatchingResult {
        if (expr is JIRLocalVar) {
            val exprs = expandLocalVar(position, expr)
            val variants = exprs.map { match(it.second, pattern, it.first) }
            return mergeLocalVarVariantMatches(strategy, variants)
        }

        var result = noMatch
        result = uniteMatches(result, match(position, pattern.obj, expr))

        if (expr is JIRInstanceCallExpr) {
            result = uniteMatches(result, match(position, pattern, expr.instance))
        }

        if (expr is JIRFieldRef) {
            val further = expr.instance?.let { match(position, pattern, it) } ?: noMatch
            result = uniteMatches(result, further)
        }

        return result
    }

    private fun matchEllipsisMethodInvocations(
        pattern: EllipsisMethodInvocations,
        inst: JIRInst
    ): SemgrepMatchingResult {
        if (inst !is JIRCallInst) {
            return noMatch
        }
        return match(ExprPosition(inst, isLValue = false), pattern, inst.callExpr)
    }

    private fun matchReturnStmt(pattern: ReturnStmt, inst: JIRInst): SemgrepMatchingResult {
        if (inst !is JIRReturnInst) {
            return noMatch
        }
        if ((pattern.value == null) != (inst.returnValue == null)) {
            return noMatch
        }
        if (pattern.value != null) {
            return match(ExprPosition(inst, isLValue = false), pattern.value, inst.returnValue!!)
        }
        return singleEmptyMatch
    }

    private fun matchName(name: Name, concreteName: String): SemgrepMatchingResult =
        when (name) {
            is ConcreteName -> {
                if (concreteName == name.name) singleEmptyMatch else noMatch
            }

            is MetavarName -> {
                single(
                    SemgrepMatch(
                        strMetavars = mapOf(name.metavarName to concreteName),
                        exprMetavars = emptyMap(),
                    )
                )
            }
        }
}
