package org.opentaint.semgrep.pattern.conversion.automata

import org.opentaint.semgrep.pattern.conversion.ParamCondition
import org.opentaint.semgrep.pattern.conversion.ParamConstraint
import org.opentaint.semgrep.pattern.conversion.ParamPosition
import org.opentaint.semgrep.pattern.conversion.SemgrepPatternAction
import org.opentaint.semgrep.pattern.conversion.SemgrepPatternAction.ClassConstraint
import org.opentaint.semgrep.pattern.conversion.SemgrepPatternAction.SignatureModifier
import org.opentaint.semgrep.pattern.conversion.SemgrepPatternActionList

private class AutomataGenerationState(val formulaManager: MethodFormulaManager) {
    var root = AutomataNode()
    var last = root
    var hasMethodEnter = false
    var hasMethodExit = false
}

fun convertActionListToAutomata(
    formulaManager: MethodFormulaManager,
    actionList: SemgrepPatternActionList
): SemgrepRuleAutomata {
    val actions = actionList.actions.toMutableList()
    val signaturePatterns = actions.filterIsInstance<SemgrepPatternAction.MethodSignature>()
    val exitPatterns = actions.filterIsInstance<SemgrepPatternAction.MethodExit>()

    val signaturePattern = if (signaturePatterns.isEmpty()) null else {
        check(signaturePatterns.size == 1)

        val firstAction = actions.removeFirst()
        check(firstAction is SemgrepPatternAction.MethodSignature)
        check(!actionList.hasEllipsisInTheBeginning) {
            "Ellipsis before signature"
        }

        firstAction
    }

    val exitPattern = if (exitPatterns.isEmpty()) null else {
        check(exitPatterns.size == 1)

        val lastAction = actions.removeLast()
        check(lastAction is SemgrepPatternAction.MethodExit)
        check(!actionList.hasEllipsisInTheEnd) {
            "Ellipsis after exit"
        }

        lastAction
    }

    val generationState = AutomataGenerationState(formulaManager)
    generationState.generateCallActions(
        actions,
        loopBeforeCalls = actionList.hasEllipsisInTheBeginning || signaturePattern != null,
        loopAfterCalls = actionList.hasEllipsisInTheEnd || exitPattern != null,
    )

    generationState.generateMethodEnter(signaturePattern, actionList.hasEllipsisInTheBeginning)
    generationState.generateMethodExitAndMarkAccept(exitPattern, actionList.hasEllipsisInTheEnd)

    with(generationState) {
        val params = SemgrepRuleAutomata.Params(
            isDeterministic = true,
            hasMethodEnter = hasMethodEnter,
            hasMethodExit = hasMethodExit,
            hasEndEdges = false
        )
        return SemgrepRuleAutomata(formulaManager, setOf(root), params)
    }
}

private fun AutomataGenerationState.generateMethodExitAndMarkAccept(
    exitPattern: SemgrepPatternAction.MethodExit?,
    hasEllipsisInTheEnd: Boolean,
) {
    if (exitPattern != null) {
        val newNode = AutomataNode()
        val edgeFormula = constructExitFormula(formulaManager, exitPattern)
        last.outEdges.add(AutomataEdgeType.MethodExit(edgeFormula) to newNode)
        hasMethodExit = true
        last = newNode
    } else if (hasEllipsisInTheEnd) {
        val newNode = AutomataNode()
        last.outEdges.add(AutomataEdgeType.MethodExit(MethodFormula.True) to newNode)
        last.accept = true
        hasMethodExit = true
        last = newNode
    }

    last.accept = true
}

private fun AutomataGenerationState.generateMethodEnter(
    signaturePattern: SemgrepPatternAction.MethodSignature?,
    hasEllipsisInTheBeginning: Boolean
) {
    if (signaturePattern != null) {
        val newRoot = AutomataNode()
        val edgeFormula = constructSignatureFormula(formulaManager, signaturePattern)
        newRoot.outEdges.add(AutomataEdgeType.MethodEnter(edgeFormula) to root)
        hasMethodEnter = true
        root = newRoot
    } else if (hasEllipsisInTheBeginning) {
        val newRoot = AutomataNode()
        newRoot.outEdges.add(AutomataEdgeType.MethodEnter(MethodFormula.True) to root)
        newRoot.outEdges.addAll(root.outEdges)
        hasMethodEnter = true
        root = newRoot
    }
}

private fun AutomataGenerationState.generateCallActions(
    actions: List<SemgrepPatternAction>,
    loopBeforeCalls: Boolean,
    loopAfterCalls: Boolean,
) {
    if (actions.isEmpty()) {
        if (loopBeforeCalls || loopAfterCalls) {
            last.outEdges.add(AutomataEdgeType.MethodCall(MethodFormula.True) to last)
        }
        return
    }

    var loopOccurred = false
    var stateBeforeLast: AutomataNode? = null
    var lastFormula: MethodFormula? = null

    actions.forEach { action ->
        val edgeFormula = constructFormula(formulaManager, action)

        if (last != root || loopBeforeCalls) {
            // always add loop in middle nodes
            val loopFormula = edgeFormula.complement()
            last.outEdges.add(AutomataEdgeType.MethodCall(loopFormula) to last)
            loopOccurred = true
        }

        val newNode = AutomataNode()

        last.outEdges.add(AutomataEdgeType.MethodCall(edgeFormula) to newNode)
        stateBeforeLast = last
        lastFormula = edgeFormula
        last = newNode
    }

    if (loopAfterCalls) {
        last.outEdges.add(AutomataEdgeType.MethodCall(MethodFormula.True) to last)
    } else if (lastFormula != null && loopOccurred) {
        last.outEdges.add(AutomataEdgeType.MethodCall(lastFormula!!) to last)
        last.outEdges.add(AutomataEdgeType.MethodCall(lastFormula!!.complement()) to stateBeforeLast!!)
    }
}

private fun constructFormula(formulaManager: MethodFormulaManager, action: SemgrepPatternAction): MethodFormula =
    when (action) {
        is SemgrepPatternAction.MethodCall -> constructFormula(formulaManager, action)
        is SemgrepPatternAction.ConstructorCall -> constructFormula(formulaManager, action)
        is SemgrepPatternAction.MethodSignature -> error("Unexpected signature action")
        is SemgrepPatternAction.MethodExit -> error("Unexpected exit action")
    }

private class MethodFormulaBuilder(
    private val formulaManager: MethodFormulaManager,
) {
    private val params = hashSetOf<Pair<Position, ParamCondition.Atom>>()
    private var signature: MethodSignature? = null
    private var numberOfArgs: NumberOfArgsConstraint? = null
    private val methodModifiers = mutableListOf<SignatureModifier>()
    private val classConstraints = mutableListOf<ClassConstraint>()

    fun addSignature(signature: MethodSignature) {
        this.signature = signature
    }

    fun addNumberOfArgs(numberOfArgsConstraint: Int) {
        this.numberOfArgs = NumberOfArgsConstraint(numberOfArgsConstraint)
    }

    fun addMethodModifier(methodModifiers: List<SignatureModifier>) {
        this.methodModifiers += methodModifiers
    }

    fun addClassConstraints(classConstraints: List<ClassConstraint>) {
        this.classConstraints += classConstraints
    }

    fun addParamConstraint(position: Position, condition: ParamCondition) {
        val unprocessed = mutableListOf(condition)
        while (unprocessed.isNotEmpty()) {
            val cond = unprocessed.removeLast()
            when (cond) {
                is ParamCondition.And -> unprocessed.addAll(cond.conditions)
                ParamCondition.True -> continue
                is ParamCondition.Atom -> {
                    params.add(position to cond)
                }
            }
        }
    }

    fun build(): MethodFormula {
        val signature = this.signature ?: error("Signature required")

        val constraints = mutableListOf<MethodConstraint>()
        params.mapTo(constraints) { ParamConstraint(it.first, it.second) }
        numberOfArgs?.let { constraints.add(it) }
        methodModifiers.mapTo(constraints) { MethodModifierConstraint(it) }
        classConstraints.mapTo(constraints) { ClassModifierConstraint(it) }

        if (constraints.isEmpty()) {
            val predicate = Predicate(signature, constraint = null)
            return MethodFormula.Literal(formulaManager.predicateId(predicate), negated = false)
        }

        val literals = constraints.map { constraint ->
            val predicate = Predicate(signature, constraint)
            MethodFormula.Literal(formulaManager.predicateId(predicate), negated = false)
        }

        return formulaManager.mkAnd(literals)
    }
}

private fun constructFormula(
    formulaManager: MethodFormulaManager,
    action: SemgrepPatternAction.MethodCall
): MethodFormula {
    val builder = MethodFormulaBuilder(formulaManager)
    collectParameterConstraints(builder, action.params)

    if (action.obj != null) {
        builder.addParamConstraint(Position.Object, action.obj)
    }
    if (action.result != null) {
        builder.addParamConstraint(Position.Result, action.result)
    }

    val className = if (action.enclosingClassName != null) {
        MethodEnclosingClassName(action.enclosingClassName)
    } else {
        MethodEnclosingClassName.anyClassName
    }

    val signature = MethodSignature(
        methodName = MethodName(action.methodName),
        enclosingClassName = className,
    )
    builder.addSignature(signature)

    return builder.build()
}

private fun constructFormula(
    formulaManager: MethodFormulaManager,
    action: SemgrepPatternAction.ConstructorCall
): MethodFormula {
    val builder = MethodFormulaBuilder(formulaManager)
    collectParameterConstraints(builder, action.params)

    if (action.result != null) {
        builder.addParamConstraint(Position.Object, action.result)
    }

    val signature = MethodSignature(
        methodName = MethodName(SemgrepPatternAction.SignatureName.Concrete("<init>")),
        enclosingClassName = MethodEnclosingClassName(action.className),
    )
    builder.addSignature(signature)

    return builder.build()
}

private fun constructSignatureFormula(
    formulaManager: MethodFormulaManager,
    action: SemgrepPatternAction.MethodSignature
): MethodFormula {
    val builder = MethodFormulaBuilder(formulaManager)
    collectParameterConstraints(builder, action.params)

    builder.addMethodModifier(action.modifiers)
    builder.addClassConstraints(action.enclosingClassConstraints)

    val methodName = MethodName(action.methodName)

    val signature = MethodSignature(
        methodName = methodName,
        enclosingClassName = MethodEnclosingClassName.anyClassName,
    )
    builder.addSignature(signature)

    return builder.build()
}

private fun constructExitFormula(
    formulaManager: MethodFormulaManager,
    action: SemgrepPatternAction.MethodExit
): MethodFormula {
    val builder = MethodFormulaBuilder(formulaManager)
    val idx = Position.ArgumentIndex.Concrete(0)
    builder.addParamConstraint(Position.Argument(idx), action.retVal)

    val signature = MethodSignature(
        methodName = MethodName(SemgrepPatternAction.SignatureName.AnyName),
        enclosingClassName = MethodEnclosingClassName.anyClassName,
    )
    builder.addSignature(signature)

    return builder.build()
}

private fun collectParameterConstraints(
    builder: MethodFormulaBuilder,
    params: ParamConstraint,
) {
    when (params) {
        is ParamConstraint.Concrete -> {
            builder.addNumberOfArgs(params.params.size)

            params.params.forEachIndexed { index, cond ->
                val idx = Position.ArgumentIndex.Concrete(index)
                builder.addParamConstraint(Position.Argument(idx), cond)
            }
        }

        is ParamConstraint.Partial -> {
            params.params.forEach { pattern ->
                val argIdx = when (val pos = pattern.position) {
                    is ParamPosition.Any -> Position.ArgumentIndex.Any(pos.paramClassifier)
                    is ParamPosition.Concrete -> Position.ArgumentIndex.Concrete(pos.idx)
                }
                builder.addParamConstraint(Position.Argument(argIdx), pattern.condition)
            }
        }
    }
}
