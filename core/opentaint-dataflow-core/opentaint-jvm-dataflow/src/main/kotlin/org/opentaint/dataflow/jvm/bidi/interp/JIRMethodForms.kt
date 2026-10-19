package org.opentaint.dataflow.jvm.bidi.interp

import org.opentaint.dataflow.bidi.ap.Direction
import org.opentaint.dataflow.bidi.ap.MethodKey
import org.opentaint.dataflow.bidi.interp.FormKey
import org.opentaint.dataflow.bidi.interp.CallPlan
import org.opentaint.dataflow.bidi.interp.FormsCache
import org.opentaint.dataflow.bidi.interp.MethodForms
import org.opentaint.dataflow.bidi.interp.RuleStatement
import org.opentaint.dataflow.bidi.interp.RuleView
import org.opentaint.dataflow.bidi.interp.StatementSummary
import org.opentaint.dataflow.bidi.interp.ExitRules
import org.opentaint.dataflow.jvm.ap.ifds.analysis.forEachAliasPathAtStatement
import org.opentaint.ir.api.common.cfg.CommonCallExpr
import org.opentaint.ir.api.common.cfg.CommonInst
import org.opentaint.ir.api.jvm.cfg.JIRCallExpr
import org.opentaint.ir.api.jvm.cfg.JIRInst

/** Context-sensitive forms use managed payloads and stable logical rule/action keys. */
class JIRMethodForms internal constructor(
    private val interp: JIRInterpreter,
    private val entry: JIRMethodEntry,
    private val key: MethodKey,
) : MethodForms {
    private val plans = FormsCache<CallPlan>(entry.size, entry.shared.formReferences, entry.shared.formStatistics) { it.reversed() }
    private val initial = FormsCache<RuleStatement>(1, entry.shared.formReferences, entry.shared.formStatistics) { it.reversed() }

    override fun statement(direction: Direction, s: CommonInst) = withStatement(direction, s) { it }

    override fun <R> withStatement(direction: Direction, s: CommonInst, use: (StatementSummary) -> R): R =
        withStatement(direction, s, RuleView.FULL, use)

    override fun statement(direction: Direction, s: CommonInst, view: RuleView) = withStatement(direction, s, view) { it }

    override fun <R> withStatement(direction: Direction, s: CommonInst, view: RuleView, use: (StatementSummary) -> R): R =
        entry.statements.withForm(entry.index(s), direction, view, {
            JIRStatementForms(interp.manager, interp.typeFilters,
                aliasPaths = { at, base -> buildList {
                    entry.aliasAnalysis?.forEachAliasPathAtStatement(at, base) { alias, path ->
                        add(alias to path.map(interp::idx))
                    }
                } },
                readSources = { builder, at -> interp.ruleForms(view).readSources(builder, at,
                    FormKey(entry.shared.formOwner, key.method, null, at)) },
                formOwner = entry.shared.formOwner).build(s as JIRInst)
        }, use)

    override fun call(direction: Direction, s: CommonInst, call: CommonCallExpr) = withCall(direction, s, call) { it }

    override fun <R> withCall(direction: Direction, s: CommonInst, call: CommonCallExpr, use: (CallPlan) -> R): R =
        withCall(direction, s, call, RuleView.FULL, use)

    override fun call(direction: Direction, s: CommonInst, call: CommonCallExpr, view: RuleView) = withCall(direction, s, call, view) { it }

    override fun <R> withCall(direction: Direction, s: CommonInst, call: CommonCallExpr, view: RuleView, use: (CallPlan) -> R): R =
        plans.withForm(entry.index(s), direction, view, {
            JIRCallPlanBuilder(interp.manager, interp.ruleForms(view), interp.checker, interp.typeFilters,
                key, s as JIRInst, call as JIRCallExpr, interp.callResolver::resolve, interp.epResolver,
                entry.lambdas, aliasAnalysis = entry.aliasAnalysis,
                defaultGetModel = interp.defaultGetModel, formOwner = entry.shared.formOwner,
                requireCatalogTarget = entry.shared::requireRun1CatalogTarget).build()
        }, use)

    override fun entryRules(direction: Direction) = withEntryRules(direction) { it }

    override fun <R> withEntryRules(direction: Direction, use: (RuleStatement) -> R): R = withEntryRules(direction, RuleView.FULL, use)

    override fun entryRules(direction: Direction, view: RuleView) = withEntryRules(direction, view) { it }

    override fun <R> withEntryRules(direction: Direction, view: RuleView, use: (RuleStatement) -> R): R =
        initial.withForm(0, direction, view, { JIRBoundaryForms.entryRules(interp, entry, key, view = view) }, use)

    override fun exitRules(direction: Direction, exit: CommonInst) = withExitRules(direction, exit) { it }

    override fun <R> withExitRules(direction: Direction, exit: CommonInst, use: (ExitRules) -> R): R =
        withExitRules(direction, exit, RuleView.FULL, use)

    override fun exitRules(direction: Direction, exit: CommonInst, view: RuleView) = withExitRules(direction, exit, view) { it }

    override fun <R> withExitRules(direction: Direction, exit: CommonInst, view: RuleView, use: (ExitRules) -> R): R =
        entry.exits.withForm(entry.index(exit), direction, view, {
            JIRBoundaryForms.exitRules(interp, entry, key, exit, entryRules(Direction.FORWARD, view), view = view)
        }, use)
}
