package org.opentaint.dataflow.jvm.bidi.interp

import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.ap.ifds.EmptyMethodContext
import org.opentaint.dataflow.ap.ifds.MethodEntryPoint
import org.opentaint.dataflow.ap.ifds.MethodWithContext
import org.opentaint.dataflow.ap.ifds.analysis.MethodEntrypointResolver
import org.opentaint.dataflow.bidi.ap.ApManager
import org.opentaint.dataflow.bidi.ap.ExclusionSet
import org.opentaint.dataflow.bidi.ap.MethodKey
import org.opentaint.dataflow.bidi.ap.PathFact
import org.opentaint.dataflow.bidi.ap.Pattern
import org.opentaint.dataflow.bidi.interp.CallCleaners
import org.opentaint.dataflow.bidi.interp.ActionId
import org.opentaint.dataflow.bidi.interp.ActionRole
import org.opentaint.dataflow.bidi.interp.FormKey
import org.opentaint.dataflow.bidi.interp.OperationId
import org.opentaint.dataflow.bidi.interp.StaticOperation
import org.opentaint.dataflow.bidi.interp.FormOwner
import org.opentaint.dataflow.bidi.interp.RuleKind
import org.opentaint.dataflow.bidi.interp.RuleOccurrenceId
import org.opentaint.dataflow.bidi.interp.TemporaryKey
import org.opentaint.dataflow.bidi.interp.CallPlan
import org.opentaint.dataflow.bidi.interp.CallPoint.*
import org.opentaint.dataflow.bidi.interp.CallStage.Actions
import org.opentaint.dataflow.bidi.interp.CallStage.Callees
import org.opentaint.dataflow.bidi.interp.CallStage.Edges
import org.opentaint.dataflow.bidi.interp.CleanStep
import org.opentaint.dataflow.bidi.interp.Guard
import org.opentaint.dataflow.bidi.interp.MicroEdgeBuilder
import org.opentaint.dataflow.bidi.interp.StageKind.*
import org.opentaint.dataflow.bidi.interp.StatementSummary
import org.opentaint.dataflow.bidi.interp.keepEdge
import org.opentaint.dataflow.bidi.interp.starEdge
import org.opentaint.dataflow.configuration.TaintCleanReach
import org.opentaint.dataflow.configuration.jvm.Position
import org.opentaint.dataflow.configuration.jvm.PositionWithAccess
import org.opentaint.dataflow.configuration.jvm.RemoveMark
import org.opentaint.dataflow.configuration.jvm.TaintConfigurationItem
import org.opentaint.dataflow.configuration.jvm.TaintMark as RuleMark
import org.opentaint.dataflow.configuration.jvm.serialized.UserDefinedRuleInfo
import org.opentaint.dataflow.jvm.ap.ifds.CallPositionToJIRValueResolver
import org.opentaint.dataflow.jvm.ap.ifds.JIRCallResolutionContext
import org.opentaint.dataflow.jvm.ap.ifds.JIRCallResolver
import org.opentaint.dataflow.jvm.ap.ifds.JIRFactTypeChecker
import org.opentaint.dataflow.jvm.ap.ifds.JIRLocalAliasAnalysis
import org.opentaint.dataflow.jvm.ap.ifds.JIRLocalAliasAnalysis.AliasApInfo
import org.opentaint.dataflow.jvm.ap.ifds.JIRMarkAwareConditionRewriter
import org.opentaint.dataflow.jvm.ap.ifds.JIRMethodPositionBaseTypeResolver
import org.opentaint.dataflow.jvm.ap.ifds.MethodFlowFunctionUtils.accessPathBase
import org.opentaint.dataflow.jvm.ap.ifds.analysis.JIRMethodGetDefault
import org.opentaint.dataflow.jvm.ap.ifds.analysis.aliasesPersistedThroughCall
import org.opentaint.dataflow.jvm.ap.ifds.analysis.apAccessor
import org.opentaint.dataflow.taint.PositionAccess
import org.opentaint.ir.api.jvm.JIRMethod
import org.opentaint.ir.api.jvm.JIRType
import org.opentaint.ir.api.jvm.cfg.JIRAssignInst
import org.opentaint.ir.api.jvm.cfg.JIRCallExpr
import org.opentaint.ir.api.jvm.cfg.JIRImmediate
import org.opentaint.ir.api.jvm.cfg.JIRInst
import org.opentaint.ir.api.jvm.cfg.JIRInstanceCallExpr
import org.opentaint.ir.api.jvm.ext.toType
import java.util.Collections

/** One forward call plan for a caller method key, with the actual resolution and entry-point services. */
internal class JIRCallPlanBuilder(
    private val manager: ApManager,
    private val forms: JIRRuleForms,
    private val checker: JIRFactTypeChecker,
    private val typeFilters: JIRTypeFilters,
    private val caller: MethodKey,
    private val s: JIRInst,
    private val call: JIRCallExpr,
    private val resolveCall: (JIRCallExpr, JIRInst, JIRCallResolutionContext) -> List<JIRCallResolver.MethodResolutionResult>,
    private val epResolver: MethodEntrypointResolver,
    lambdas: Map<Int, Set<JIRMethod>>,
    private val aliasAnalysis: JIRLocalAliasAnalysis? = null,
    private val defaultGetModel: JIRMethodGetDefault? = null,
    private val persistedAliases: (AccessPathBase.LocalVar, JIRInst) -> List<AliasApInfo> = { base, at ->
        aliasAnalysis?.aliasesPersistedThroughCall(base, at).orEmpty()
    },
    private val formOwner: FormOwner = FormOwner(),
    potentialUnknownAliases: Boolean = false,
    private val requireCatalogTarget: (org.opentaint.ir.api.common.CommonMethod, org.opentaint.ir.api.common.CommonMethod) -> Unit = { _, _ -> },
) {
    private val lambdas: Map<Int, Set<JIRMethod>> = Collections.unmodifiableMap(lambdas.mapValues { (_, methods) ->
        Collections.unmodifiableSet(LinkedHashSet(methods))
    })
    private val callee: JIRMethod = call.method.method
    private val returnValue: JIRImmediate? = (s as? JIRAssignInst)?.lhv?.let {
        it as? JIRImmediate ?: error("Non simple return value: $s")
    }
    private val r: AccessPathBase? = returnValue?.let(::accessPathBase)
    private val o: AccessPathBase? = (call as? JIRInstanceCallExpr)?.instance?.let(::accessPathBase)
    private val args: List<AccessPathBase?> = call.args.map(::accessPathBase)
    private val rewriter = JIRMarkAwareConditionRewriter(CallPositionToJIRValueResolver(call, returnValue),
        checker, aliasAnalysis, s, potentialUnknownAliases)
    private val types = JIRMethodPositionBaseTypeResolver(callee)
    private fun filter(t: JIRType?) = typeFilters.of(t)
    private val passReads = LinkedHashSet<AccessPathBase>()
    private val formKey = FormKey(formOwner, caller.method, caller, s)
    private fun occurrence(kind: RuleKind, ordinal: Int) = RuleOccurrenceId(formKey, kind, ordinal)

    fun build(): CallPlan {
        val sinks = sinks()
        val (callees, unresolved) = resolve()
        val back = bindBack()
        val stages = buildList {
            add(Edges(BEFORE, BOUND, BIND_IN, bindIn()))
            for (sink in sinks) if (sink.endFacts.isNotEmpty())
                add(Edges(BOUND, REWRITTEN, END_FACTS, MicroEdgeBuilder().apply {
                    sink.endFacts.forEach { edge(it.edge) }
                }.buildStage(), Guard.SinkTriggered(sink)))
            add(Edges(BOUND, REWRITTEN, SOURCES, sources(sinks)))
            add(Actions(BOUND, ADDED, cleanSteps()))
            if (callees.isNotEmpty()) add(Callees(ADDED, RETURNED, callees))
            if (unresolved) {
                add(Edges(ADDED, RETURNED, UNRESOLVED, StatementSummary.identities(boundPositions())))
                add(Edges(ADDED, RETURNED, UNRESOLVED, passRules()))
            }
            if (callee.isConstructor)
                add(Edges(ADDED, REWRITTEN, CONSTRUCTOR, StatementSummary.identities(boundPositions())))
            add(Actions(RETURNED, REWRITTEN, rewriterSteps()))
            add(Edges(REWRITTEN, AFTER, BIND_BACK, back))
            aliases(back)?.let { add(Edges(REWRITTEN, AFTER, ALIASES, it, Guard.MemoryEffect)) }
        }
        val staged = stages.mapIndexed { ordinal, stage ->
            if (stage !is Edges) stage else stage.copy(summary = stage.summary.withStaticOperations(
                stage.summary.staticOperations.map { operation ->
                    val id = operation.id.copy(stageOrdinal = ordinal)
                    when (operation) {
                        is StaticOperation.Read -> operation.copy(id = id)
                        is StaticOperation.Write -> operation.copy(id = id)
                        is StaticOperation.MarkInput -> operation.copy(id = id)
                    }
                }))
        }
        return CallPlan(touched(), staged, sinks, entry = BEFORE, exit = AFTER, passReads = passReads)
    }

    private fun sinks(): List<org.opentaint.dataflow.bidi.interp.SinkRule> {
        val mayBeArray = { i: Int -> checker.callArgumentMayBeArray(call, AccessPathBase.Argument(i)) }
        return forms.rules.sinkRulesForMethod(callee, s, fact = null).withIndex()
            .flatMap { (ordinal, rule) ->
                if (!forms.valid(rule)) emptyList() else forms.sinks(rule,
                    forms.rewriteSourceOrSink(rule, rewriter),
                    occurrence(RuleKind.CALL_SINK, ordinal), mayBeArray)
            }
    }

    private fun touched(): Set<AccessPathBase> = buildSet {
        add(AccessPathBase.ClassStatic)
        o?.let(::add)
        args.filterNotNullTo(this)
        r?.let(::add)
    }

    private fun boundPositions(): List<AccessPathBase> = buildList {
        add(AccessPathBase.ClassStatic)
        if (o != null) add(AccessPathBase.This)
        args.forEachIndexed { i, a -> if (a != null) add(AccessPathBase.Argument(i)) }
    }

    /** Caller operand filters act before bindings; S and zero remain unfiltered. */
    private fun bindIn(): StatementSummary = MicroEdgeBuilder().apply {
        edge(keepEdge(AccessPathBase.ClassStatic))
        edge(starEdge(AccessPathBase.Zero, emptyList(), AccessPathBase.Zero, emptyList()))
        o?.let {
            edge(starEdge(it, emptyList(), AccessPathBase.This, emptyList()))
            operandFilter(it, filter(callee.enclosingClass.toType()))
        }
        args.forEachIndexed { i, a ->
            if (a == null) return@forEachIndexed
            edge(starEdge(a, emptyList(), AccessPathBase.Argument(i), emptyList()))
            operandFilter(a, filter(call.args[i].type))
        }
    }.buildStage()

    /** Caller result filters act after bindings; no constant, local, exception or zero returns. */
    private fun bindBack(): StatementSummary = MicroEdgeBuilder().apply {
        edge(keepEdge(AccessPathBase.ClassStatic))
        if (o != null && o !is AccessPathBase.Constant) {
            edge(starEdge(AccessPathBase.This, emptyList(), o, emptyList()))
            resultFilter(o, filter((call as JIRInstanceCallExpr).instance.type))
        }
        args.forEachIndexed { i, a ->
            if (a == null || a is AccessPathBase.Constant) return@forEachIndexed
            edge(starEdge(AccessPathBase.Argument(i), emptyList(), a, emptyList()))
            resultFilter(a, filter(call.args[i].type))
        }
        if (r != null) {
            edge(starEdge(AccessPathBase.Return, emptyList(), r, emptyList()))
            resultFilter(r, filter(returnValue!!.type))
        }
    }.buildStage()

    private fun sources(sinks: List<org.opentaint.dataflow.bidi.interp.SinkRule> = emptyList()): StatementSummary = MicroEdgeBuilder().apply {
        keepZero()
        forms.sinkInputs(sinks).forEach(::staticOperation)
        for ((ordinal, rule) in forms.rules.sourceRulesForMethod(callee, s, fact = null).withIndex()) {
            if (!forms.valid(rule)) continue
            forms.source(this, rule, occurrence(RuleKind.CALL_SOURCE, ordinal),
                forms.rewriteSourceOrSink(rule, rewriter))
        }
    }.buildStage()

    private fun cleanSteps(): List<CleanStep> = buildList {
        var ordinal = 0
        for (rule in forms.rules.cleanerRulesForMethod(callee, s, fact = null)) {
            val id = occurrence(RuleKind.CLEANER, ordinal++)
            if (forms.valid(rule)) addAll(forms.cleanSteps(rule, rewriter.rewrite(rule.condition), types, id))
        }
        for (cleaner in (callee as? CallCleaners)?.callCleaners.orEmpty()) {
            val id = ActionId(occurrence(RuleKind.CLEANER, ordinal++), ActionRole.GENERATED_CLEANER, 0)
            add(forms.lowerCleaner(cleaner, types, TemporaryKey(id)))
        }
    }

    private fun rewriterSteps(): List<CleanStep> {
        val out = ArrayList<CleanStep>()
        fun add(rule: TaintConfigurationItem, id: RuleOccurrenceId,
                positions: List<Pair<Int, Position>>, source: Boolean) {
            val info = rule.info as? UserDefinedRuleInfo ?: return
            for ((actionOrdinal, p) in positions) for ((markOrdinal, m) in info.relevantTaintMarks.withIndex()) {
                val anyTarget = source && forms.pos(rule, p)?.any == true
                val action = if (anyTarget)
                    RemoveMark(RuleMark(m), (p as PositionWithAccess).base, TaintCleanReach.ExactAndAnyField)
                else RemoveMark(RuleMark(m), p, TaintCleanReach.Exact)
                out += forms.unconditional(rule, action, types,
                    ActionId(id, ActionRole.RETURN_REWRITER, actionOrdinal, markOrdinal))
            }
        }
        var ordinal = 0
        for (rule in forms.rules.sourceRulesForMethod(callee, s, fact = null, allRelevant = true)) {
            val id = occurrence(RuleKind.REWRITER, ordinal++)
            if (forms.valid(rule) && !forms.rewriteSourceOrSink(rule, rewriter, projectNegatives = false).isFalse)
                add(rule, id, rule.actionsAfter.mapIndexed { i, action -> i to action.position }, source = true)
        }
        for (rule in forms.rules.cleanerRulesForMethod(callee, s, fact = null, allRelevant = true)) {
            val id = occurrence(RuleKind.REWRITER, ordinal++)
            if (forms.valid(rule) && rewriter.rewrite(rule.condition).isTrue)
                add(rule, id, rule.actionsAfter.mapIndexedNotNull { i, action ->
                    (action as? RemoveMark)?.let { i to it.position }
                }, source = false)
        }
        return out
    }

    /** Expand frozen lambda targets before the common empty-target check. */
    private fun resolve(): Pair<List<MethodKey>, Boolean> {
        val context = object : JIRCallResolutionContext {
            override val methodEntryPoint = caller
            override val aliasAnalysis = this@JIRCallPlanBuilder.aliasAnalysis
        }
        val results = resolveCall(call, s, context)
        if (results.isEmpty()) return emptyList<MethodKey>() to true
        val callees = LinkedHashSet<MethodKey>()
        var unresolved = false
        var hasNonemptyTarget = false
        fun add(method: MethodWithContext) {
            if ((method.method as JIRMethod).instList.size == 0) return
            requireCatalogTarget(caller.method, method.method)
            hasNonemptyTarget = true
            callees += keys(method)
        }
        for (result in results) when (result) {
            JIRCallResolver.MethodResolutionResult.MethodResolutionFailed -> unresolved = true
            is JIRCallResolver.MethodResolutionResult.ConcreteMethod -> add(result.method)
            is JIRCallResolver.MethodResolutionResult.Lambda -> {
                val implementations = lambdas[s.location.index]
                if (implementations.isNullOrEmpty()) unresolved = true
                else implementations.forEach { add(MethodWithContext(it, EmptyMethodContext)) }
            }
        }
        if (!hasNonemptyTarget) unresolved = true
        return callees.toList() to unresolved
    }

    private fun keys(method: MethodWithContext): List<MethodKey> =
        epResolver.resolveEntryPoints(method.method, method.ctx).map { MethodEntryPoint(method.ctx, it) }

    /** The default identity is in its own unfiltered stage. */
    private fun passRules(): StatementSummary = MicroEdgeBuilder().apply {
        val rules = forms.rules.passTroughRulesForMethod(callee, s, fact = null).filter(forms::valid)
            .map { Triple(it, rewriter.rewrite(it.condition), true) } +
            defaultGetModel?.defaultPropagationRules(callee).orEmpty().filter { forms.valid(it.rule) }
                .map { Triple(it.rule, it.condition, false) }
        val read = LinkedHashSet<AccessPathBase>()
        val written = LinkedHashSet<AccessPathBase>()
        for ((ruleOrdinal, item) in rules.withIndex()) {
            val (rule, cond, ofRuleSet) = item
            if (cond.isFalse) continue
            if (!cond.isTrue)
                forms.errors.applied(rule, "pass rule with a mark literal (interpreter.md §4.2, D24): applied without it")
            for ((actionOrdinal, a) in rule.actionsAfter.withIndex()) {
                val actionId = ActionId(occurrence(RuleKind.PASS_THROUGH, ruleOrdinal),
                    ActionRole.PASS_THROUGH, actionOrdinal)
                val (from, to) = forms.pass(this, rule, a, OperationId(formKey, action = actionId)) ?: continue
                read += from
                written += to
                if (ofRuleSet) passReads += from
            }
        }
        fun declared(base: AccessPathBase) = filter(types.resolve(PositionAccess.Simple(base)) as? JIRType)
        read.forEach { operandFilter(it, declared(it)) }
        written.forEach { resultFilter(it, declared(it)) }
    }.buildStage()

    /** Only local bind-back targets have persisted aliases. Helper-created edges own every full alias path. */
    private fun aliases(back: StatementSummary): StatementSummary? {
        val builder = MicroEdgeBuilder()
        for (e in back.edges) {
            val local = e.edge.to.base as? AccessPathBase.LocalVar ?: continue
            for (alias in persistedAliases(local, s)) {
                if (alias.base == local && alias.accessors.isEmpty()) continue
                val transfer = starEdge(e.edge.from.base, emptyList(), alias.base,
                    alias.accessors.map { manager.accessors.index(it.apAccessor()) })
                builder.edge(transfer)
                if (alias.base == AccessPathBase.ClassStatic)
                    builder.staticOperation(StaticOperation.Write(OperationId(formKey),
                        Pattern(transfer.to, ExclusionSet.Empty), transfer))
            }
        }
        return builder.buildStage().takeIf { it.edges.isNotEmpty() }
    }
}
