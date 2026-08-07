package org.opentaint.semgrep.go.pattern.conversion

import org.opentaint.semgrep.go.pattern.ArgPrefix
import org.opentaint.semgrep.go.pattern.AssignStmt
import org.opentaint.semgrep.go.pattern.BlockStmt
import org.opentaint.semgrep.go.pattern.CallArgs
import org.opentaint.semgrep.go.pattern.CallExpr
import org.opentaint.semgrep.go.pattern.CompositeLit
import org.opentaint.semgrep.go.pattern.ConcreteName
import org.opentaint.semgrep.go.pattern.DeferStmt
import org.opentaint.semgrep.go.pattern.Ellipsis
import org.opentaint.semgrep.go.pattern.EllipsisArgPrefix
import org.opentaint.semgrep.go.pattern.EllipsisElem
import org.opentaint.semgrep.go.pattern.EllipsisMetavarParam
import org.opentaint.semgrep.go.pattern.EllipsisParam
import org.opentaint.semgrep.go.pattern.EllipsisStmt
import org.opentaint.semgrep.go.pattern.ExprStmt
import org.opentaint.semgrep.go.pattern.FuncDecl
import org.opentaint.semgrep.go.pattern.FuncType
import org.opentaint.semgrep.go.pattern.GoStmt
import org.opentaint.semgrep.go.pattern.Identifier
import org.opentaint.semgrep.go.pattern.IndexExpr
import org.opentaint.semgrep.go.pattern.IntLiteral
import org.opentaint.semgrep.go.pattern.KeyedElem
import org.opentaint.semgrep.go.pattern.MapType
import org.opentaint.semgrep.go.pattern.Metavar
import org.opentaint.semgrep.go.pattern.MetavarName
import org.opentaint.semgrep.go.pattern.MetavarParam
import org.opentaint.semgrep.go.pattern.MetavarType
import org.opentaint.semgrep.go.pattern.MethodDecl
import org.opentaint.semgrep.go.pattern.Name
import org.opentaint.semgrep.go.pattern.NamedParam
import org.opentaint.semgrep.go.pattern.NamedType
import org.opentaint.semgrep.go.pattern.NilLiteral
import org.opentaint.semgrep.go.pattern.NoArgs
import org.opentaint.semgrep.go.pattern.PointerType
import org.opentaint.semgrep.go.pattern.QualifiedType
import org.opentaint.semgrep.go.pattern.RemovedImportDecl
import org.opentaint.semgrep.go.pattern.ReturnStmt
import org.opentaint.semgrep.go.pattern.SelectorExpr
import org.opentaint.semgrep.go.pattern.SemgrepGoPattern
import org.opentaint.semgrep.go.pattern.ShortVarDecl
import org.opentaint.semgrep.go.pattern.SliceType
import org.opentaint.semgrep.go.pattern.StringEllipsis
import org.opentaint.semgrep.go.pattern.StringLiteral
import org.opentaint.semgrep.go.pattern.TopList
import org.opentaint.semgrep.go.pattern.TypeName
import org.opentaint.semgrep.go.pattern.TypedMetavar
import org.opentaint.semgrep.go.pattern.UnaryExpr
import org.opentaint.semgrep.go.pattern.VarDecl
import org.opentaint.semgrep.go.pattern.conversion.GoLanguageStrategy.Companion.FIELD_AUX_MODIFIER
import org.opentaint.semgrep.go.pattern.conversion.GoLanguageStrategy.Companion.FIELD_READ_AUX_CLASS
import org.opentaint.semgrep.go.pattern.conversion.GoLanguageStrategy.Companion.INDEX_AUX_FIELD_NAME
import org.opentaint.semgrep.go.pattern.conversion.go.dropFieldResultModifier
import org.opentaint.semgrep.pattern.SemgrepRuleLoadStepTrace
import org.opentaint.semgrep.pattern.conversion.ActionListBuilder
import org.opentaint.semgrep.pattern.conversion.IsMetavar
import org.opentaint.semgrep.pattern.conversion.MetavarAtom
import org.opentaint.semgrep.pattern.conversion.ParamCondition
import org.opentaint.semgrep.pattern.conversion.ParamConstraint
import org.opentaint.semgrep.pattern.conversion.ParamPattern
import org.opentaint.semgrep.pattern.conversion.ParamPosition
import org.opentaint.semgrep.pattern.conversion.SemgrepPatternAction
import org.opentaint.semgrep.pattern.conversion.SemgrepPatternAction.ConstructorCall
import org.opentaint.semgrep.pattern.conversion.SemgrepPatternAction.MethodCall
import org.opentaint.semgrep.pattern.conversion.SemgrepPatternAction.MethodExit
import org.opentaint.semgrep.pattern.conversion.SemgrepPatternAction.MethodSignature
import org.opentaint.semgrep.pattern.conversion.SemgrepPatternAction.SignatureModifier
import org.opentaint.semgrep.pattern.conversion.SemgrepPatternAction.SignatureModifierValue
import org.opentaint.semgrep.pattern.conversion.SemgrepPatternAction.SignatureName
import org.opentaint.semgrep.pattern.conversion.SemgrepPatternActionList
import org.opentaint.semgrep.pattern.conversion.SpecificBoolValue
import org.opentaint.semgrep.pattern.conversion.SpecificIntValue
import org.opentaint.semgrep.pattern.conversion.SpecificNullValue
import org.opentaint.semgrep.pattern.conversion.SpecificStringValue
import org.opentaint.semgrep.pattern.conversion.TypeConstraint
import org.opentaint.semgrep.pattern.conversion.collectMetavarTo
import org.opentaint.semgrep.pattern.conversion.mkAnd

class GoPatternToActionListConverter : ActionListBuilder<SemgrepGoPattern> {
    val failedTransformations = mutableMapOf<String, Int>()

    private var nextArtificialId = 0
    private fun provideArtificialMetavar(): MetavarAtom =
        MetavarAtom.createArtificial("${nextArtificialId++}")

    private class TransformationFailed(override val message: String) : Exception(message)
    private fun transformationFailed(reason: String): Nothing = throw TransformationFailed(reason)

    override fun createActionList(pattern: SemgrepGoPattern, semgrepTrace: SemgrepRuleLoadStepTrace): SemgrepPatternActionList? = try {
        transformPatternToActionList(pattern, isRoot = true)
    } catch (ex: TransformationFailed) {
        val reason = ex.message
        failedTransformations[reason] = (failedTransformations[reason] ?: 0) + 1
        null
    }

    // Go functions may have multiple return values; v1 collapses them into a single TypeConstraint
    // (a single return keeps its type, multiple returns lose precision to TypeConstraint.Any).
    private fun collapseReturns(list: List<TypeConstraint>): TypeConstraint? = when {
        list.isEmpty() -> null
        list.size == 1 -> list[0]
        else -> TypeConstraint.Any
    }

    private fun transformPatternToActionList(
        pattern: SemgrepGoPattern,
        isRoot: Boolean = false,
    ): SemgrepPatternActionList = when (pattern) {
        is TopList -> transformSequence(pattern.items)
        is ExprStmt -> transformPatternToActionList(pattern.expr)
        is CallExpr -> transformMethodInvocation(pattern)
        is Ellipsis, is EllipsisStmt ->
            SemgrepPatternActionList(emptyList(), hasEllipsisInTheBeginning = true, hasEllipsisInTheEnd = true)
        is DeferStmt -> transformPatternToActionList(pattern.call)
        is GoStmt -> transformPatternToActionList(pattern.call)
        is AssignStmt -> {
            if (pattern.op != "=") transformationFailed("Assignment_op_${pattern.op}")
            if (pattern.rhs.size != 1) {
                transformationFailed("multi-RHS assignment")
            }
            transformAssignment(pattern.lhs, value = pattern.rhs.single())
        }
        is ShortVarDecl -> {
            if (pattern.rhs.size != 1) transformationFailed("multi-RHS assignment")
            transformAssignment(pattern.lhs, value = pattern.rhs.single())
        }
        is VarDecl -> {
            val spec = pattern.specs.singleOrNull() ?: transformationFailed("VarDecl_multiple_specs")
            if (spec.names.size != 1 || spec.values.size != 1) transformationFailed("multi-LHS assignment")
            transformAssignment(targetName = spec.names.single(), declType = spec.type, value = spec.values.single())
        }
        is CompositeLit -> transformObjectCreation(pattern)
        is UnaryExpr ->
            if (pattern.op == "&" && pattern.operand is CompositeLit) transformObjectCreation(pattern.operand)
            else transformationFailed("UnaryExpr_${pattern.op}")
        is FuncDecl -> transformFuncDecl(pattern.name, pattern.signature, pattern.body, receiverType = null)
        is MethodDecl -> {
            val recvType = (pattern.receiver as? NamedParam)?.type?.let { transformType(it) }
            transformFuncDecl(pattern.name, pattern.signature, pattern.body, receiverType = recvType)
        }

        is IndexExpr -> transformIndexRead(pattern)
        is SelectorExpr -> transformFieldReadOrFail(pattern)

        is BlockStmt -> transformSequence(pattern.stmts)
        is ReturnStmt -> {
            val allActions = mutableListOf<SemgrepPatternAction>()
            val retVals = pattern.values.map { v ->
                val (actions, cond) = transformPatternIntoParamConditionWithActions(v)
                allActions += actions
                cond ?: ParamCondition.True
            }
            SemgrepPatternActionList(
                allActions + MethodExit(retVals),
                hasEllipsisInTheBeginning = false,
                hasEllipsisInTheEnd = false,
            )
        }

        is RemovedImportDecl -> transformSequence(emptyList())

        // Cases added in later tasks.
        else -> {
            val prefix = if (isRoot) "Root pattern is: " else ""
            transformationFailed("$prefix${pattern::class.simpleName}")
        }
    }

    private fun transformSequence(items: List<SemgrepGoPattern>): SemgrepPatternActionList {
        if (items.isEmpty()) {
            return SemgrepPatternActionList(emptyList(), hasEllipsisInTheBeginning = false, hasEllipsisInTheEnd = false)
        }
        return items
            .map { transformPatternToActionList(it) }
            .reduce { acc, next -> concatActionLists(acc, next) }
    }

    private fun concatActionLists(
        first: SemgrepPatternActionList,
        second: SemgrepPatternActionList,
    ): SemgrepPatternActionList {
        var endEllipsis = second.hasEllipsisInTheEnd
        if (endEllipsis && second.actions.isEmpty() && first.actions.lastOrNull() is MethodExit) {
            endEllipsis = false
        }
        // A leading "..." (empty list, both flags) contributes only the beginning flag.
        val beginEllipsis = first.hasEllipsisInTheBeginning
        return SemgrepPatternActionList(
            first.actions + second.actions,
            hasEllipsisInTheBeginning = beginEllipsis,
            hasEllipsisInTheEnd = endEllipsis,
        )
    }

    private fun signatureName(name: Name): SignatureName = when (name) {
        is ConcreteName -> SignatureName.Concrete(name.name)
        is MetavarName -> SignatureName.MetaVar(name.name)
    }

    private fun transformMethodInvocation(call: CallExpr): SemgrepPatternActionList {
        val actions = mutableListOf<SemgrepPatternAction>()
        val methodName: SignatureName
        var obj: ParamCondition? = null
        var enclosing: TypeConstraint? = null

        when (val fn = call.fn) {
            is Identifier -> methodName = signatureName(fn.name)
            is Metavar -> methodName = SignatureName.MetaVar(fn.name)
            is SelectorExpr -> {
                methodName = signatureName(fn.sel)
                // A package-qualified call `pkg.Func(...)` arrives here too
                val (recvActions, recvObj, recvType) = decomposeReceiver(fn.obj)
                actions += recvActions
                obj = recvObj
                enclosing = recvType
            }
            else -> transformationFailed("MethodInvocation_fn: ${fn::class.simpleName}")
        }

        // TODO: CallExpr.hasEllipsis (Go variadic spread, e.g. f(xs...)) is not yet modeled.
        val (argActions, params) = generateParamConditions(call.args)
        actions += argActions
        actions += MethodCall(methodName = methodName, result = null, params = params, obj = obj, enclosingClassName = enclosing)
        return SemgrepPatternActionList(actions, hasEllipsisInTheBeginning = false, hasEllipsisInTheEnd = false)
    }

    private fun transformIndexRead(expr: IndexExpr): SemgrepPatternActionList {
        val (recvActions, _, recvType) = decomposeReceiver(expr.obj)

        if (recvType != null) {
            transformationFailed("IndexExpr_obj_with_type_constraint")
        }

        if (recvActions.isEmpty()) {
            transformationFailed("IndexExpr_obj_is_not_defined")
        }

        return joinFieldModifiers(recvActions, INDEX_AUX_FIELD_NAME)
    }

    private fun joinFieldModifiers(recvActions: List<SemgrepPatternAction>, fieldName: String): SemgrepPatternActionList {
        val lastActionResult = recvActions.last().result
        check(lastActionResult != null) { "Receiver action has no meta-var" }

        val currentFieldModifiers = mutableListOf<String>()
        val resultWithoutModifier = lastActionResult.extractAndRemoveFieldSignatureModifier(currentFieldModifiers)

        check(currentFieldModifiers.size <= 1) {
            "Filed modifier normalization failed"
        }

        val updatedModifier = createFieldModifier(currentFieldModifiers.firstOrNull(), fieldName)

        val resultActions = recvActions.toMutableList()
        val updatedAction = resultActions.last()
            .setResultCondition(mkAnd(setOfNotNull(resultWithoutModifier, updatedModifier)))
        resultActions[resultActions.lastIndex] = updatedAction

        return SemgrepPatternActionList(resultActions, hasEllipsisInTheEnd = false, hasEllipsisInTheBeginning = false)
    }

    private fun ParamCondition.extractAndRemoveFieldSignatureModifier(
        modifier: MutableList<String>
    ): ParamCondition? {
        return when (this) {
            is ParamCondition.And ->
                mkAnd(conditions.mapNotNullTo(hashSetOf()) { it.extractAndRemoveFieldSignatureModifier(modifier) })

            is ParamCondition.ParamModifier -> dropFieldResultModifier {
                modifier += it
            }

            else -> this
        }
    }

    private fun createFieldModifier(prevModifier: String?, currentModifier: String): ParamCondition {
        val chainedModifiers = prevModifier?.let {
            GoLanguageStrategy.joinFieldNames(it, currentModifier)
        } ?: currentModifier

        val modifierValue = SignatureModifierValue.StringValue(FIELD_AUX_MODIFIER, chainedModifiers)
        val sigModifier = SignatureModifier(TypeConstraint.Any, modifierValue)
        return ParamCondition.ParamModifier(sigModifier)
    }

    private fun transformFieldReadOrFail(sel: SelectorExpr): SemgrepPatternActionList {
        val fieldName = (sel.sel as? ConcreteName)?.name
            ?: transformationFailed("SelectorExpr_field_not_concrete")

        val pkg = sel.obj as? Identifier
        val pkgName = (pkg?.name as? ConcreteName)?.name
        if (pkgName != null) {
            return mkGlobalReadActionList(pkgName, fieldName)
        }

        val (recvActions, recvObj, recvType) = decomposeReceiver(sel.obj)

        if (recvType != null) {
            transformationFailed("SelectorExpr_obj_with_type_constraint")
        }

        if (recvActions.isEmpty()) {
            return mkFieldReadActionList(fieldName, recvObj ?: ParamCondition.True)
        }

        return joinFieldModifiers(recvActions, fieldName)
    }

    private fun mkFieldReadActionList(
        fieldName: String,
        obj: ParamCondition
    ): SemgrepPatternActionList {
        val methodName = SignatureName.Concrete(GoLanguageStrategy.fieldReadAuxFnName(fieldName))
        return SemgrepPatternActionList(
            listOf(
                MethodCall(
                    methodName = methodName,
                    result = null,
                    params = ParamConstraint.Concrete(emptyList()),
                    obj = obj,
                    enclosingClassName = goNamed(FIELD_READ_AUX_CLASS),
                )
            ),
            hasEllipsisInTheBeginning = false,
            hasEllipsisInTheEnd = false,
        )
    }

    private fun mkGlobalReadActionList(pkgName: String, fieldName: String): SemgrepPatternActionList {
        val methodName = SignatureName.Concrete(GoLanguageStrategy.globalReadAuxFnName(fieldName))
        return SemgrepPatternActionList(
            listOf(
                MethodCall(
                    methodName = methodName,
                    result = null,
                    params = ParamConstraint.Concrete(emptyList()),
                    obj = ParamCondition.True,
                    enclosingClassName = goNamed(pkgName),
                )
            ),
            hasEllipsisInTheBeginning = false,
            hasEllipsisInTheEnd = false,
        )
    }

    private fun decomposeReceiver(
        recv: SemgrepGoPattern,
    ): Triple<List<SemgrepPatternAction>, ParamCondition?, TypeConstraint?> = when (recv) {
        is Identifier -> when (val n = recv.name) {
            is ConcreteName -> Triple(emptyList(), ParamCondition.True, goNamed(n.name))
            is MetavarName -> Triple(emptyList(), IsMetavar(MetavarAtom.create(n.name)), null)
        }

        is Metavar -> Triple(emptyList(), IsMetavar(MetavarAtom.create(recv.name), star = recv.star), null)
        is TypedMetavar -> {
            val t = transformType(recv.type)
            Triple(
                emptyList(),
                ParamCondition.And(
                    listOf(
                        IsMetavar(MetavarAtom.create(recv.name), star = recv.star),
                        ParamCondition.TypeIs(t),
                    ),
                ),
                null,
            )
        }

        else -> {
            val (actions, cond) = transformPatternIntoParamConditionWithActions(recv)
            Triple(actions, cond, null)
        }
    }

    private fun flattenArgs(args: CallArgs): List<SemgrepGoPattern?> {
        val out = mutableListOf<SemgrepGoPattern?>()
        var cur: CallArgs = args
        while (true) {
            when (val c = cur) {
                is NoArgs -> return out
                is EllipsisArgPrefix -> { out.add(null); cur = c.rest }
                is ArgPrefix -> { out.add(c.arg); cur = c.rest }
            }
        }
    }

    private fun generateParamConditions(
        args: CallArgs,
    ): Pair<List<SemgrepPatternAction>, ParamConstraint> {
        val flat = flattenArgs(args)
        val allActions = mutableListOf<SemgrepPatternAction>()
        val patterns = mutableListOf<ParamPattern>()
        var idxConcrete = true
        for ((i, arg) in flat.withIndex()) {
            if (arg == null) { idxConcrete = false; continue }
            val (actions, cond) = transformPatternIntoParamConditionWithActions(arg)
            allActions += actions
            val position: ParamPosition =
                if (idxConcrete) ParamPosition.Concrete(i) else ParamPosition.Any(argClassifier(arg, i))
            val condition = cond ?: ParamCondition.True
            if (condition is ParamCondition.True && position is ParamPosition.Any) continue
            patterns += ParamPattern(position, condition)
        }
        if (idxConcrete) {
            return allActions to ParamConstraint.Concrete(patterns.map { it.condition })
        }
        if (patterns.count { it.position is ParamPosition.Any } > 1) transformationFailed("Multiple any params")
        return allActions to ParamConstraint.Partial(patterns)
    }

    private fun argClassifier(arg: SemgrepGoPattern, i: Int): String = when (arg) {
        is Metavar -> arg.name
        is TypedMetavar -> arg.name
        else -> "*->$i"
    }

    /** Returns (prefix actions, condition?). A simple value yields (emptyList, cond). A complex
     *  sub-expression yields its actions with the last result bound to a fresh artificial metavar. */
    private fun transformPatternIntoParamConditionWithActions(
        pattern: SemgrepGoPattern,
    ): Pair<List<SemgrepPatternAction>, ParamCondition?> {
        transformPatternIntoParamCondition(pattern)?.let { return emptyList<SemgrepPatternAction>() to it }
        val actionList = transformPatternToActionList(pattern)
        if (actionList.actions.isEmpty()) return emptyList<SemgrepPatternAction>() to null
        val (result, metavar) = actionList.actions.ensureLastActionMetaVar()
        return result to IsMetavar(metavar)
    }

    private fun List<SemgrepPatternAction>.ensureLastActionMetaVar(): Pair<List<SemgrepPatternAction>, MetavarAtom> {
        val lastAction = last()
        val lastResult = lastAction.result
        if (lastResult != null) {
            val metaVars = hashSetOf<MetavarAtom>()
            lastResult.collectMetavarTo(metaVars)
            val metaVar = metaVars.singleOrNull()
                ?: error("Last action result has no or multiple meta-var")
            return this to metaVar
        }

        val result = toMutableList()
        val metavar = provideArtificialMetavar()
        val newLastAction = lastAction.setResultCondition(IsMetavar(metavar))
        result[result.lastIndex] = newLastAction
        return result to metavar
    }

    private fun transformPatternIntoParamCondition(pattern: SemgrepGoPattern): ParamCondition? = when (pattern) {
        is IntLiteral -> pattern.text.toIntOrNull()?.let { SpecificIntValue(it) }
            ?: transformationFailed("IntLiteral_not_int: ${pattern.text}")
        is NilLiteral -> SpecificNullValue
        // Go's `true`/`false` are predeclared identifiers, not literals, so the parser
        // emits Identifier here (never BoolConstant). Other bare identifiers are not
        // simple values and fall through to null (deferred, like Java).
        is Identifier -> when ((pattern.name as? ConcreteName)?.name) {
            "true" -> SpecificBoolValue(true)
            "false" -> SpecificBoolValue(false)
            else -> null
        }
        is StringLiteral -> when (val c = pattern.content) {
            is ConcreteName -> SpecificStringValue(c.name)
            is MetavarName -> ParamCondition.StringValueMetaVar(MetavarAtom.create(c.name))
        }
        is StringEllipsis -> ParamCondition.AnyStringLiteral
        is Metavar -> IsMetavar(MetavarAtom.create(pattern.name), star = pattern.star)
        is TypedMetavar -> ParamCondition.And(
            listOf(
                IsMetavar(MetavarAtom.create(pattern.name), star = pattern.star),
                ParamCondition.TypeIs(transformType(pattern.type)),
            ),
        )
        else -> null
    }

    private fun transformAssignment(
        targets: List<SemgrepGoPattern>,
        value: SemgrepGoPattern,
    ): SemgrepPatternActionList {
        if (targets.isEmpty()) {
            transformationFailed("Assignment without targets")
        }

        val conditions = mutableListOf<ParamCondition>()
        val names = targets.map { it.assignmentTargetName(conditions) }

        if (names.size == 1) {
            val name = names.first()
            if (name != null) {
                conditions += IsMetavar(MetavarAtom.create(name.name), star = name.star)
            }

            return transformAssignmentValue(conditions, value)
        }

        if (names.count { it != null } > 1) {
            transformationFailed("Assignment into multiple named locations")
        }

        val assignedNameIdx = names.indexOfFirst { it != null }
        if (assignedNameIdx == -1) {
            return transformAssignmentValue(conditions, value)
        }

        val assignedName = names[assignedNameIdx]!!
        conditions += IsMetavar(MetavarAtom.create(assignedName.name), star = assignedName.star)
        conditions += createFieldModifier(prevModifier = null, "tuple$$assignedNameIdx")

        return transformAssignmentValue(conditions, value)
    }

    // Name + star of an assignment target. A bare `Metavar` (`$*X`) or a typed metavar (`($*X : T)`)
    // can be starred; other target shapes carry star = false. Threading star lets `$*X = src()` (or
    // its typed form) taint every nested field.
    private data class AssignmentTarget(val name: String, val star: Boolean)

    private fun SemgrepGoPattern.assignmentTargetName(
        conditions: MutableList<ParamCondition>
    ): AssignmentTarget? = when {
        this is Metavar -> AssignmentTarget(name, star)
        this is TypedMetavar -> {
            conditions += ParamCondition.TypeIs(transformType(type))
            AssignmentTarget(name, star = star)
        }

        this is Identifier && name is MetavarName -> AssignmentTarget(name.name, star = false)
        this is Identifier && name is ConcreteName && name.name == "_" -> null

        else -> transformationFailed("Assignment_target_not_metavar")
    }

    private fun transformAssignment(
        declType: TypeName?,
        targetName: Name,
        value: SemgrepGoPattern,
    ): SemgrepPatternActionList {
        val name: String = (targetName as? MetavarName)?.name
            ?: transformationFailed("Assignment_target_not_metavar")

        val conditions = mutableListOf<ParamCondition>()
        declType?.let { conditions += ParamCondition.TypeIs(transformType(it)) }
        conditions += IsMetavar(MetavarAtom.create(name))
        return transformAssignmentValue(conditions, value)
    }

    private fun transformAssignmentValue(
        conditions: List<ParamCondition>,
        value: SemgrepGoPattern,
    ): SemgrepPatternActionList {
        val actionList = transformPatternToActionList(value)
        if (actionList.actions.isEmpty()) transformationFailed("Assignment_nothing_to_assign")
        val last = actionList.actions.last()
        val newLast = last.setResultCondition(mkAnd(conditions.toSet()))
        return SemgrepPatternActionList(
            actionList.actions.dropLast(1) + newLast,
            hasEllipsisInTheBeginning = false,
            hasEllipsisInTheEnd = false,
        )
    }

    private fun transformType(type: TypeName): TypeConstraint = when (type) {
        is NamedType -> when (val n = type.name) {
            is ConcreteName -> goNamed(n.name)
            is MetavarName -> TypeConstraint.MetaVar(n.name)
        }
        is QualifiedType -> {
            val pkg = (type.pkg as? ConcreteName)?.name ?: transformationFailed("QualifiedType_pkg_not_concrete")
            val name = (type.name as? ConcreteName)?.name ?: transformationFailed("QualifiedType_name_not_concrete")
            goQualified(pkg, name)
        }
        is MetavarType -> TypeConstraint.MetaVar(type.name)
        is PointerType -> TypeConstraint.Concrete(GoConcreteType.Pointer(transformType(type.elem)))
        is SliceType -> TypeConstraint.Concrete(GoConcreteType.Slice(transformType(type.elem)))
        is MapType -> TypeConstraint.Concrete(GoConcreteType.MapType(transformType(type.key), transformType(type.value)))
        else -> transformationFailed("Type_unsupported: ${type::class.simpleName}")
    }

    private fun transformObjectCreation(lit: CompositeLit): SemgrepPatternActionList {
        val className = lit.type?.let { transformType(it) } ?: transformationFailed("CompositeLit_no_type")
        val allActions = mutableListOf<SemgrepPatternAction>()
        val patterns = mutableListOf<ParamPattern>()
        var positional = true
        var idx = 0
        for (elem in lit.elements) {
            when (elem) {
                is EllipsisElem -> positional = false
                is KeyedElem -> {
                    // `...` inside a composite literal is parsed as KeyedElem(null, Ellipsis) by the grammar.
                    // Treat it the same as EllipsisElem: it marks the struct literal as open/partial.
                    if (elem.key == null && elem.value is Ellipsis) { positional = false; continue }
                    val (actions, cond) = transformPatternIntoParamConditionWithActions(elem.value)
                    allActions += actions
                    val condition = cond ?: ParamCondition.True
                    val key = elem.key
                    val position: ParamPosition = when {
                        key != null -> ParamPosition.Named(
                            fieldName(key) ?: transformationFailed("CompositeLit_key_not_field"),
                        )
                        positional -> ParamPosition.Concrete(idx)
                        else -> ParamPosition.Any("*->$idx")
                    }
                    if (!(condition is ParamCondition.True && position is ParamPosition.Any)) {
                        patterns += ParamPattern(position, condition)
                    }
                    idx++
                }
            }
        }
        val allConcrete = positional && patterns.all { it.position is ParamPosition.Concrete }
        val params: ParamConstraint = if (allConcrete) {
            ParamConstraint.Concrete(patterns.map { it.condition })
        } else {
            if (patterns.count { it.position is ParamPosition.Any } > 1) transformationFailed("Multiple any params")
            ParamConstraint.Partial(patterns)
        }
        return SemgrepPatternActionList(
            allActions + ConstructorCall(className = className, result = null, params = params),
            hasEllipsisInTheBeginning = false,
            hasEllipsisInTheEnd = false,
        )
    }

    private fun fieldName(key: SemgrepGoPattern): String? = when (key) {
        is Identifier -> (key.name as? ConcreteName)?.name
        else -> null
    }

    private fun transformFuncDecl(
        name: Name,
        signature: FuncType,
        body: BlockStmt?,
        receiverType: TypeConstraint?,
    ): SemgrepPatternActionList {
        val methodName = signatureName(name)
        val paramPatterns = mutableListOf<ParamPattern>()
        var positional = true
        var idx = 0
        for (p in signature.params) {
            when (p) {
                is EllipsisParam, is EllipsisMetavarParam -> positional = false
                is MetavarParam -> {
                    val position = if (positional) ParamPosition.Concrete(idx) else ParamPosition.Any(p.name)
                    paramPatterns += ParamPattern(position, IsMetavar(MetavarAtom.create(p.name)))
                    idx++
                }
                is NamedParam -> {
                    for (nm in p.names) {
                        val mv = (nm as? MetavarName)?.name
                            ?: transformationFailed("MethodDecl_param_name_not_metavar")
                        val position = if (positional) ParamPosition.Concrete(idx) else ParamPosition.Any(mv)
                        paramPatterns += ParamPattern(position, IsMetavar(MetavarAtom.create(mv)))
                        paramPatterns += ParamPattern(position, ParamCondition.TypeIs(transformType(p.type)))
                        idx++
                    }
                }
            }
        }
        val returnTypes = signature.results.mapNotNull { (it as? NamedParam)?.type?.let { t -> transformType(t) } }
        val sig = MethodSignature(
            methodName = methodName,
            params = ParamConstraint.Partial(paramPatterns),
            returnType = collapseReturns(returnTypes),
            modifiers = emptyList(),
            enclosingClassMetavar = (receiverType as? TypeConstraint.MetaVar)?.metaVar,
            enclosingClassConstraints = if (receiverType is TypeConstraint.Concrete) {
                listOf(SemgrepPatternAction.ClassConstraint.SuperType(receiverType))
            } else {
                emptyList()
            },
        )
        val bodyList = body?.let { transformPatternToActionList(it) }
        return SemgrepPatternActionList(
            listOf(sig) + (bodyList?.actions ?: emptyList()),
            hasEllipsisInTheBeginning = false,
            hasEllipsisInTheEnd = bodyList?.hasEllipsisInTheEnd ?: false,
        )
    }
}
