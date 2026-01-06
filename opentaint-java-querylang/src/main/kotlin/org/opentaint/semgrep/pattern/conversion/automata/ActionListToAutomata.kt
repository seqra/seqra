package org.opentaint.semgrep.pattern.conversion.automata

import org.opentaint.semgrep.pattern.conversion.ParamCondition
import org.opentaint.semgrep.pattern.conversion.ParamConstraint
import org.opentaint.semgrep.pattern.conversion.ParamPosition
import org.opentaint.semgrep.pattern.conversion.SemgrepPatternAction
import org.opentaint.semgrep.pattern.conversion.SemgrepPatternAction.SignatureModifier
import org.opentaint.semgrep.pattern.conversion.SemgrepPatternActionList

fun convertActionListToAutomata(
    formulaManager: MethodFormulaManager,
    actionList: SemgrepPatternActionList
): SemgrepRuleAutomata {
    val root = AutomataNode()

    var last = root
    var beforeLast: AutomataNode? = null
    var lastFormula: MethodFormula? = null
    var hasMethodEnter = false
    var loopOccurred = false

    val actions = actionList.actions.toMutableList()
    val signaturePatterns = actions.filterIsInstance<SemgrepPatternAction.MethodSignature>()
    if (signaturePatterns.isNotEmpty()) {
        check(signaturePatterns.size == 1)

        val firstAction = actions.removeFirst()
        check(firstAction is SemgrepPatternAction.MethodSignature)
        check(!actionList.hasEllipsisInTheBeginning) {
            "Ellipsis before signature"
        }

        val edgeFormula = constructSignatureFormula(formulaManager, firstAction)

        val newNode = AutomataNode()

        last.outEdges.add(AutomataEdgeType.MethodEnter(edgeFormula) to newNode)
        beforeLast = last
        lastFormula = edgeFormula
        last = newNode
        hasMethodEnter = true
    }

    if (actionList.hasEllipsisInTheBeginning) {
        last.outEdges.add(AutomataEdgeType.MethodEnter(MethodFormula.True) to last)
        hasMethodEnter = true
    }

    actions.forEach { action ->

        val edgeFormula = constructFormula(formulaManager, action)

        if (last != root || actionList.hasEllipsisInTheBeginning) {
            // always add loop in middle nodes
            val loopFormula = edgeFormula.complement()
            last.outEdges.add(AutomataEdgeType.MethodCall(loopFormula) to last)
            loopOccurred = true
        }

        val newNode = AutomataNode()

        last.outEdges.add(AutomataEdgeType.MethodCall(edgeFormula) to newNode)
        beforeLast = last
        lastFormula = edgeFormula
        last = newNode
    }

    last.accept = true
    if (actionList.hasEllipsisInTheEnd) {
        last.outEdges.add(AutomataEdgeType.MethodCall(MethodFormula.True) to last)
    } else if (lastFormula != null && loopOccurred) {
        last.outEdges.add(AutomataEdgeType.MethodCall(lastFormula!!) to last)
        last.outEdges.add(AutomataEdgeType.MethodCall(lastFormula!!.complement()) to beforeLast!!)
    }

    return SemgrepRuleAutomata(formulaManager, setOf(root), isDeterministic = true, hasMethodEnter, hasEndEdges = false)
}

private fun constructFormula(formulaManager: MethodFormulaManager, action: SemgrepPatternAction): MethodFormula =
    when (action) {
        is SemgrepPatternAction.MethodCall -> constructFormula(formulaManager, action)
        is SemgrepPatternAction.ConstructorCall -> constructFormula(formulaManager, action)
        is SemgrepPatternAction.MethodSignature -> error("Unexpected signature action")
    }

private class MethodFormulaBuilder(
    private val formulaManager: MethodFormulaManager,
) {
    private val params = hashSetOf<Pair<Position, ParamCondition.Atom>>()
    private var signature: MethodSignature? = null
    private var numberOfArgs: NumberOfArgsConstraint? = null
    private val methodModifiers = mutableListOf<SignatureModifier>()
    private val classModifiers = mutableListOf<SignatureModifier>()

    fun addSignature(signature: MethodSignature) {
        this.signature = signature
    }

    fun addNumberOfArgs(numberOfArgsConstraint: Int) {
        this.numberOfArgs = NumberOfArgsConstraint(numberOfArgsConstraint)
    }

    fun addMethodModifier(methodModifiers: List<SignatureModifier>) {
        this.methodModifiers += methodModifiers
    }

    fun addClassModifier(classModifiers: List<SignatureModifier>) {
        this.classModifiers += classModifiers
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
        classModifiers.mapTo(constraints) { ClassModifierConstraint(it) }

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
    builder.addClassModifier(action.enclosingClassModifiers)

    val methodName = MethodName(action.methodName)

    val signature = MethodSignature(
        methodName = methodName,
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
