package org.opentaint.dataflow.jvm.bidi.interp

import org.opentaint.dataflow.bidi.ap.Direction
import org.opentaint.dataflow.bidi.ap.MethodKey
import org.opentaint.dataflow.bidi.interp.CallPlan
import org.opentaint.dataflow.bidi.interp.FormsCache
import org.opentaint.dataflow.bidi.interp.MethodForms
import org.opentaint.dataflow.bidi.interp.RuleStatement
import org.opentaint.dataflow.jvm.ap.ifds.analysis.forEachAliasPathAtStatement
import org.opentaint.ir.api.common.cfg.CommonCallExpr
import org.opentaint.ir.api.common.cfg.CommonInst
import org.opentaint.ir.api.jvm.cfg.JIRCallExpr
import org.opentaint.ir.api.jvm.cfg.JIRInst

/** Context-sensitive entry and call forms keep strong identities for standing conjunction joins. */
class JIRMethodForms internal constructor(
    private val interp: JIRInterpreter,
    private val entry: JIRMethodEntry,
    private val key: MethodKey,
) : MethodForms {
    private val plans = FormsCache<CallPlan>(entry.size) { it.reversed() }
    private val initial = FormsCache<RuleStatement>(1) { it.reversed() }

    override fun statement(direction: Direction, s: CommonInst) =
        entry.statements.get(entry.index(s), direction) {
            JIRStatementForms(interp.manager, interp.typeFilters,
                aliasPaths = { at, base -> buildList {
                    entry.aliasAnalysis?.forEachAliasPathAtStatement(at, base) { alias, path ->
                        add(alias to path.map(interp::idx))
                    }
                } },
                readSources = interp.ruleForms::readSources).build(s as JIRInst)
        }

    override fun call(direction: Direction, s: CommonInst, call: CommonCallExpr) =
        plans.get(entry.index(s), direction) {
            JIRCallPlanBuilder(interp.manager, interp.ruleForms, interp.checker, interp.typeFilters,
                key, s as JIRInst, call as JIRCallExpr, interp.callResolver::resolve, interp.epResolver,
                entry.lambdas, aliasAnalysis = entry.aliasAnalysis,
                defaultGetModel = interp.defaultGetModel).build()
        }

    override fun entryRules(direction: Direction) = initial.get(0, direction) {
        JIRBoundaryForms.entryRules(interp, entry, key)
    }

    override fun exitRules(direction: Direction, exit: CommonInst) =
        entry.exits.get(entry.index(exit), direction) {
            JIRBoundaryForms.exitRules(interp, entry, key, exit, entryRules(Direction.FORWARD))
        }
}
