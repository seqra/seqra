package org.opentaint.dataflow.jvm.bidi.interp

import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.ap.ifds.Accessor
import org.opentaint.dataflow.ap.ifds.taint.ExternalMethodTracker
import org.opentaint.dataflow.ap.ifds.access.util.AccessorIdx
import org.opentaint.dataflow.bidi.ap.ApManager
import org.opentaint.dataflow.bidi.ap.Direction
import org.opentaint.dataflow.bidi.ap.MethodKey
import org.opentaint.dataflow.bidi.interp.CallPlan
import org.opentaint.dataflow.bidi.interp.ExitNode
import org.opentaint.dataflow.bidi.interp.Interpreter
import org.opentaint.dataflow.bidi.interp.MethodForms
import org.opentaint.dataflow.bidi.interp.RuleView
import org.opentaint.dataflow.bidi.interp.UnresolvedCallObserver
import org.opentaint.dataflow.jvm.ap.ifds.JIRCallResolver
import org.opentaint.dataflow.jvm.ap.ifds.JIRFactTypeChecker
import org.opentaint.dataflow.jvm.ap.ifds.analysis.JIRMethodEntrypointResolver
import org.opentaint.dataflow.jvm.ap.ifds.analysis.JIRMethodGetDefault
import org.opentaint.dataflow.jvm.ap.ifds.taint.TaintRulesProvider
import org.opentaint.ir.api.common.cfg.CommonCallExpr
import org.opentaint.ir.api.common.cfg.CommonInst
import org.opentaint.ir.api.jvm.cfg.JIRCallExpr

/** Complete JVM forms facade over the reduced rule set and frozen prescan inputs. */
class JIRInterpreter(
    val manager: ApManager,
    val rules: TaintRulesProvider,
    val checker: JIRFactTypeChecker,
    val callResolver: JIRCallResolver,
    val epResolver: JIRMethodEntrypointResolver,
    val entries: JIRMethodEntries,
    val defaultGetModel: JIRMethodGetDefault?,
    val externalMethodTracker: ExternalMethodTracker?,
) : Interpreter, UnresolvedCallObserver {
    internal val errors = RuleErrors()
    internal val typeFilters = JIRTypeFilters(checker, manager)
    internal val enumAliasTargets = EnumAliasTargets(callResolver.cp)
    internal val ruleForms = JIRRuleForms(rules, manager, errors, typeFilters, statistics = entries.formStatistics)
    internal val ruleFormsAtThrow = JIRRuleForms(rules, manager, errors, typeFilters,
        resultBase = AccessPathBase.Exception, statistics = entries.formStatistics)
    private val groupRuleForms = JIRRuleForms(rules, manager, errors, typeFilters,
        statistics = entries.formStatistics, view = RuleView.RUN1_GROUP)
    private val groupRuleFormsAtThrow = JIRRuleForms(rules, manager, errors, typeFilters,
        resultBase = AccessPathBase.Exception, statistics = entries.formStatistics, view = RuleView.RUN1_GROUP)

    internal fun ruleForms(view: RuleView, exceptional: Boolean = false): JIRRuleForms = when (view) {
        RuleView.FULL -> if (exceptional) ruleFormsAtThrow else ruleForms
        RuleView.RUN1_GROUP -> if (exceptional) groupRuleFormsAtThrow else groupRuleForms
    }

    fun idx(accessor: Accessor): AccessorIdx = manager.accessors.index(accessor)
    fun forms(key: MethodKey): MethodForms = entries.forms(this, key)

    override fun entryNode(method: MethodKey): CommonInst = method.statement
    override fun exitNodes(method: MethodKey): List<ExitNode> = entries[method].exitNodes(method)
    override fun entryRules(method: MethodKey) = forms(method).entryRules(Direction.FORWARD)
    override fun exitRules(method: MethodKey, exit: CommonInst) = forms(method).exitRules(Direction.FORWARD, exit)
    override fun statementSummary(method: MethodKey, statement: CommonInst) =
        forms(method).statement(Direction.FORWARD, statement)
    override fun callPlan(caller: MethodKey, statement: CommonInst, call: CommonCallExpr) =
        forms(caller).call(Direction.FORWARD, statement, call)

    override fun isSummaryBase(base: AccessPathBase): Boolean =
        base !is AccessPathBase.LocalVar && base !is AccessPathBase.CleanerTemporary

    /** Building a plan has no tracking effect; run 1 calls this for reached unresolved positions. */
    override fun reached(call: CommonCallExpr, position: AccessPathBase, plan: CallPlan) {
        val tracker = externalMethodTracker ?: return
        val callee = (call as JIRCallExpr).method.method
        if (position == AccessPathBase.ClassStatic || JIRCallResolver.alwaysIgnoreMethod(callee)) return
        tracker.trackExternalMethod("${callee.enclosingClass.name}#${callee.name}", callee.description,
            position.toString(), rulesApplied = position in plan.passReads)
    }
}
