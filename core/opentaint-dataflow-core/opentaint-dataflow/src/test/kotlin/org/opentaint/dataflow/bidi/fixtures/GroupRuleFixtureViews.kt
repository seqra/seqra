package org.opentaint.dataflow.bidi.fixtures

import org.opentaint.dataflow.bidi.ap.*
import org.opentaint.dataflow.bidi.interp.*
import org.opentaint.dataflow.util.Cancellation
import org.opentaint.ir.api.common.cfg.CommonCallExpr
import org.opentaint.ir.api.common.cfg.CommonInst
import java.util.IdentityHashMap

/** Explicit selected provider for finite tests. FULL construction rows remain the scalar
 * oracle's inputs. This projection deliberately retains the original positive DAG/proofs;
 * provider absorption and alias-collapse compatibility are tested by the JVM compiler tests.
 * Only fixtures whose distinct full leaves remain distinct GROUP leaves use this helper. */
internal class GroupRuleFixtureViews(private val marks: MarkTable, private val cancellation: Cancellation) {
    private val predicates = IdentityHashMap<RulePredicate, RulePredicate>()
    private val summaries = IdentityHashMap<StatementSummary, StatementSummary>()
    private val sources = IdentityHashMap<SourceRuleOccurrence, SourceRuleOccurrence>()
    private val sinks = IdentityHashMap<SinkRule, SinkRule>()
    private val rules = IdentityHashMap<RuleStatement, RuleStatement>()
    private val exits = IdentityHashMap<ExitRules, ExitRules>()
    private val calls = IdentityHashMap<CallPlan, CallPlan>()

    private fun pattern(p: Pattern): Pattern {
        val concrete = p.fact.mark as? MarkSlot.Concrete ?: return p
        return p.copy(fact = p.fact.copy(mark = MarkSlot.Concrete(marks.runtimeAtom(concrete.mark, MarkMode.GROUP))))
    }

    private fun predicate(full: RulePredicate): RulePredicate = predicates.getOrPut(full) {
        val selected = full.leaves.map(::pattern)
        require(selected.distinct().size == full.leaves.size) {
            "Alias-collapse fixtures must provide explicit source transfer hints, not this injective fixture view"
        }
        val builder = RulePredicate.Builder(cancellation)
        val nodes = IntArray(full.nodeCount)
        for (node in nodes.indices) nodes[node] = when (full.kind(node)) {
            RulePredicate.Kind.FALSE -> builder.falseNode
            RulePredicate.Kind.TRUE -> builder.trueNode
            RulePredicate.Kind.LEAF -> builder.leaf(selected[full.leafOrdinal(node)])
            RulePredicate.Kind.AND -> builder.and(buildList { full.childrenOf(node) { add(nodes[it]) } })
            RulePredicate.Kind.OR -> builder.or(buildList { full.childrenOf(node) { add(nodes[it]) } })
        }
        builder.build(nodes[full.root]).also { result ->
            for (leaf in full.leaves.indices) {
                val mapped = result.leaves.indexOf(selected[leaf])
                check(mapped >= 0 && full.isUnary(leaf) == result.isUnary(mapped) &&
                    full.isMulti(leaf) == result.isMulti(mapped))
            }
        }
    }

    fun source(full: SourceRuleOccurrence): SourceRuleOccurrence = sources.getOrPut(full) {
        check(!full.groupTransfers && !full.backward)
        // Injectivity above preserves unary/multi transfer modes, including original input
        // geometry. Full actions/occurrences stay identical; only positive admission changes.
        SourceRuleOccurrence(full.id, predicate(full.predicate), full.actions)
    }

    private fun sink(full: SinkRule): SinkRule = sinks.getOrPut(full) {
        val selected = full.predicate?.let(::predicate)
        SinkRule(full.rule, full.alternative, selected?.leaves ?: full.patterns.map(::pattern),
            full.endFacts, full.occurrenceId, selected)
    }

    private fun summary(full: StatementSummary): StatementSummary = summaries.getOrPut(full) {
        StatementSummary(full.touched, full.edges, full.conjunctions.map {
            ConjunctiveEdge(it.literals.map(::pattern), pattern(Pattern(it.target, ExclusionSet.Empty)).fact)
        }, full.typeFilters, full.resultFilters, full.sources.map(::source), full.staticOperations)
    }

    private fun rules(full: RuleStatement): RuleStatement = rules.getOrPut(full) {
        RuleStatement(summary(full.summary), summary(full.endFacts), full.sinks.map(::sink))
    }

    private fun exit(full: ExitRules): ExitRules = exits.getOrPut(full) {
        ExitRules(rules(full.rules), full.globalStateDrop,
            full.entryMarks.mapTo(LinkedHashSet()) { marks.runtimeAtom(it, MarkMode.GROUP) })
    }

    private fun call(full: CallPlan): CallPlan = calls.getOrPut(full) {
        CallPlan(full.touched, full.stages.map { stage -> when (stage) {
            is CallStage.Edges -> stage.copy(summary = summary(stage.summary),
                guard = (stage.guard as? Guard.SinkTriggered)?.let { Guard.SinkTriggered(sink(it.sink)) } ?: stage.guard,
                trigger = stage.trigger?.let(::sink))
            is CallStage.Actions -> stage
            is CallStage.Callees -> stage
        } }, full.sinks.map(::sink), full.entry, full.exit, full.passReads)
    }

    fun forms(full: MethodForms): MethodForms = object : MethodForms by full {
        private fun selected(direction: Direction, view: RuleView): Boolean {
            check(view != RuleView.RUN1_GROUP || direction == Direction.FORWARD)
            return view == RuleView.RUN1_GROUP
        }
        override fun statement(direction: Direction, s: CommonInst, view: RuleView): StatementSummary =
            full.statement(direction, s).let { if (selected(direction, view)) summary(it) else it }
        override fun call(direction: Direction, s: CommonInst, call: CommonCallExpr, view: RuleView): CallPlan =
            full.call(direction, s, call).let { if (selected(direction, view)) this@GroupRuleFixtureViews.call(it) else it }
        override fun entryRules(direction: Direction, view: RuleView): RuleStatement =
            full.entryRules(direction).let { if (selected(direction, view)) rules(it) else it }
        override fun exitRules(direction: Direction, exit: CommonInst, view: RuleView): ExitRules =
            full.exitRules(direction, exit).let { if (selected(direction, view)) this@GroupRuleFixtureViews.exit(it) else it }
        override fun <R> withStatement(direction: Direction, s: CommonInst, view: RuleView, use: (StatementSummary) -> R): R =
            full.withStatement(direction, s) { use(if (selected(direction, view)) summary(it) else it) }
        override fun <R> withCall(direction: Direction, s: CommonInst, call: CommonCallExpr, view: RuleView, use: (CallPlan) -> R): R =
            full.withCall(direction, s, call) { use(if (selected(direction, view)) this@GroupRuleFixtureViews.call(it) else it) }
        override fun <R> withEntryRules(direction: Direction, view: RuleView, use: (RuleStatement) -> R): R =
            full.withEntryRules(direction) { use(if (selected(direction, view)) rules(it) else it) }
        override fun <R> withExitRules(direction: Direction, exit: CommonInst, view: RuleView, use: (ExitRules) -> R): R =
            full.withExitRules(direction, exit) { use(if (selected(direction, view)) this@GroupRuleFixtureViews.exit(it) else it) }
    }
}
