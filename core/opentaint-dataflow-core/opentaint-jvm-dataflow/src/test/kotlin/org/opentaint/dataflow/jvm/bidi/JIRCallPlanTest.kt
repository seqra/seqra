package org.opentaint.dataflow.jvm.bidi

import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.ap.ifds.EmptyMethodContext
import org.opentaint.dataflow.ap.ifds.FieldAccessor
import org.opentaint.dataflow.ap.ifds.MethodContext
import org.opentaint.dataflow.ap.ifds.MethodWithContext
import org.opentaint.dataflow.bidi.ap.*
import org.opentaint.dataflow.bidi.interp.*
import org.opentaint.dataflow.configuration.CommonCondition
import org.opentaint.dataflow.configuration.CommonTaintConfigurationSinkMeta
import org.opentaint.dataflow.configuration.mkFalse
import org.opentaint.dataflow.configuration.mkTrue
import org.opentaint.dataflow.configuration.jvm.*
import org.opentaint.dataflow.configuration.jvm.TaintMark as RuleMark
import org.opentaint.dataflow.configuration.jvm.serialized.UserDefinedRuleInfo
import org.opentaint.dataflow.jvm.BasicTestUtils
import org.opentaint.dataflow.jvm.ap.ifds.JIRCallResolver.MethodResolutionResult
import org.opentaint.dataflow.jvm.ap.ifds.JIRLocalAliasAnalysis.AliasAccessor
import org.opentaint.dataflow.jvm.ap.ifds.JIRLocalAliasAnalysis.AliasApInfo
import org.opentaint.dataflow.jvm.ap.ifds.MethodFlowFunctionUtils.accessPathBase
import org.opentaint.dataflow.jvm.ap.ifds.analysis.JIRMethodGetDefault
import org.opentaint.dataflow.jvm.bidi.fixtures.CallRules
import org.opentaint.dataflow.jvm.bidi.fixtures.JIRCallTestFixtures
import org.opentaint.dataflow.jvm.bidi.interp.JIRCallPlanBuilder
import org.opentaint.ir.api.jvm.cfg.*
import kotlin.test.*

/** Raw binding and stage tables are independent of the call-plan builder. */
class JIRCallPlanTest : BasicTestUtils() {
    private val v by lazy { JIRCallTestFixtures(cp) }
    private val arg0 = AccessPathBase.Argument(0)
    private val arg1 = AccessPathBase.Argument(1)
    private val ret = AccessPathBase.Return
    private val empty = ExclusionSet.Empty
    private val t by lazy { v.manager.marks.mark("T") }
    private val u by lazy { v.manager.marks.mark("U") }
    private val owner = JIRCallTestFixtures.SAMPLE_CLASS
    private fun side(base: AccessPathBase, path: List<Int> = emptyList()) = PathFact(base, path, Tail.STAR, MarkSlot.STAR)
    private fun move(from: AccessPathBase, to: AccessPathBase, path: List<Int> = emptyList()) = PathEdge(side(from), side(to, path), empty)
    private fun fact(base: AccessPathBase, tail: Tail = Tail.EXACT, mark: org.opentaint.dataflow.bidi.ap.TaintMark = t) =
        PathFact(base, emptyList(), tail, MarkSlot.Concrete(mark))
    private fun cleanerValue(c: Cleaner): List<Any?> = listOf(c.base, c.path?.toList().orEmpty(), c.reach, c.mark)
    private fun edgeStages(p: CallPlan, kind: StageKind) = p.stages.filterIsInstance<CallStage.Edges>().filter { it.kind == kind }
    private fun stage(p: CallPlan, kind: StageKind) = edgeStages(p, kind).single()
    private fun actions(p: CallPlan, from: CallPoint, to: CallPoint) =
        p.stages.filterIsInstance<CallStage.Actions>().single { it.from == from && it.to == to }.steps
    private fun any(p: Position) = PositionWithAccess(p, PositionAccessor.AnyFieldAccessor)
    private fun field(p: Position) = PositionWithAccess(p, PositionAccessor.FieldAccessor(owner, "f", "java.lang.Object"))
    private fun condition(p: Position = Argument(0), negated: Boolean = false): Condition =
        CommonCondition.Atom<JirCondition>(ContainsMark(p, RuleMark("T"))).let { if (negated) CommonCondition.Not(it) else it }
    private fun user(vararg marks: String) = object : UserDefinedRuleInfo { override val relevantTaintMarks = marks.toSet() }
    private fun source(actions: List<AssignMark>, condition: Condition = mkTrue(), info: UserDefinedRuleInfo? = null) =
        TaintMethodSource(v.method("target"), condition, actions, info)
    private fun cleaner(actions: List<Action>, condition: Condition = mkTrue(), info: UserDefinedRuleInfo? = null) =
        TaintCleaner(v.method("target"), condition, actions, info)
    private fun pass(actions: List<Action>, condition: Condition = mkTrue()) = TaintPassThrough(v.method("target"), condition, actions, null)
    private fun sink(ends: List<AssignMark>, condition: Condition = mkTrue()) = TaintMethodSink(v.method("target"), condition, ends,
        "call-sink", TaintSinkMeta("test", CommonTaintConfigurationSinkMeta.Severity.Error, null), null)
    private fun localCall(): JIRAssignInst {
        val s = v.call("staticCall") as JIRAssignInst
        val call = v.expr(s) as JIRStaticCallExpr
        val args = call.args.mapIndexed { i, a -> JIRLocalVar(800 + i, "a$i", a.type) }
        return JIRAssignInst(s.location, JIRLocalVar(900, "result", s.lhv.type), call.copy(args = args))
    }

    @Test fun `resolved plan has ordered parallel source path and normal returned action path`(): Unit {
        val plan = v.builder(resolutions = listOf(v.resolved())).build()
        assertEquals(CallPoint.BEFORE, plan.entry)
        assertEquals(CallPoint.AFTER, plan.exit)
        assertEquals(listOf(CallPoint.BEFORE to CallPoint.BOUND, CallPoint.BOUND to CallPoint.REWRITTEN,
            CallPoint.BOUND to CallPoint.ADDED, CallPoint.ADDED to CallPoint.RETURNED,
            CallPoint.RETURNED to CallPoint.REWRITTEN, CallPoint.REWRITTEN to CallPoint.AFTER),
            plan.stages.map { it.from to it.to })
        assertEquals(StageKind.BIND_IN, (plan.stages[0] as CallStage.Edges).kind)
        assertEquals(StageKind.SOURCES, (plan.stages[1] as CallStage.Edges).kind)
        assertTrue(plan.stages[2] is CallStage.Actions)
        assertEquals(listOf(v.key("target")), (plan.stages[3] as CallStage.Callees).callees)
        assertTrue(plan.stages[4] is CallStage.Actions)
        assertEquals(StageKind.BIND_BACK, (plan.stages[5] as CallStage.Edges).kind)
        assertTrue(edgeStages(plan, StageKind.UNRESOLVED).isEmpty())
        assertTrue(plan.sinks.isEmpty())
        assertTrue(plan.passReads.isEmpty())
    }

    @Test fun `binding table keeps static and zero maps receiver arguments and lhs with correct filter sides`(): Unit {
        for (name in listOf("staticCall", "instanceCall", "repeated", "constant", "receiverArgument")) {
            val s = v.call(name)
            val call = v.expr(s)
            val o = (call as? JIRInstanceCallExpr)?.instance?.let(::accessPathBase)
            val args = call.args.map { assertNotNull(accessPathBase(it)) }
            val r = assertNotNull(accessPathBase((s as JIRAssignInst).lhv))
            val p = v.builder(name, resolutions = listOf(v.resolved())).build()
            val expectedIn = mutableListOf(move(STATIC, STATIC), move(AccessPathBase.Zero, AccessPathBase.Zero))
            o?.let { expectedIn += move(it, AccessPathBase.This) }
            args.forEachIndexed { i, a -> expectedIn += move(a, AccessPathBase.Argument(i)) }
            val expectedBack = mutableListOf(move(STATIC, STATIC))
            o?.takeUnless { it is AccessPathBase.Constant }?.let { expectedBack += move(AccessPathBase.This, it) }
            args.forEachIndexed { i, a -> if (a !is AccessPathBase.Constant) expectedBack += move(AccessPathBase.Argument(i), a) }
            expectedBack += move(ret, r)
            val into = stage(p, StageKind.BIND_IN).summary
            val back = stage(p, StageKind.BIND_BACK).summary
            assertEquals(expectedIn, into.edges.map { it.edge }, name)
            assertEquals(expectedBack, back.edges.map { it.edge }, name)
            assertEquals((listOfNotNull(o) + args).toSet(), into.typeFilters.keys, name)
            assertTrue(into.resultFilters.isEmpty())
            assertTrue(back.typeFilters.isEmpty())
            assertEquals((listOfNotNull(o) + args).filterNot { it is AccessPathBase.Constant }.toSet() + r, back.resultFilters.keys, name)
            assertEquals(setOf(STATIC, r) + args + listOfNotNull(o), p.touched, name)
            assertFalse(AccessPathBase.Zero in p.touched)
            assertTrue(into.edges.all { it.edge.from.tail == Tail.STAR && it.edge.to.tail == Tail.STAR })
            assertTrue(back.edges.none { it.edge.from.base == AccessPathBase.Zero || it.edge.from.base == AccessPathBase.Exception || it.edge.from.base is AccessPathBase.LocalVar })
            assertFalse(STATIC in into.typeFilters || AccessPathBase.Zero in into.typeFilters || STATIC in back.resultFilters)
        }
    }

    @Test fun `without lhs there is no Result binding and ordinary zero paths remain separate`(): Unit {
        val original = v.call("staticCall")
        val noLhs = JIRCallInst(original.location, v.expr(original))
        val p = v.builder(noLhs).build()
        assertEquals(listOf(move(STATIC, STATIC), move(arg0, arg0), move(arg1, arg1)), stage(p, StageKind.BIND_BACK).summary.edges.map { it.edge })
        assertEquals(setOf(STATIC, arg0, arg1), p.touched)
        val zeroIn = stage(p, StageKind.BIND_IN).summary.edges.single { it.edge.from.base == AccessPathBase.Zero }
        assertEquals(move(AccessPathBase.Zero, AccessPathBase.Zero), zeroIn.edge)
        assertEquals(listOf(ZERO_KEEP), stage(p, StageKind.SOURCES).summary.edges.map { it.edge })
        for (kind in listOf(StageKind.BIND_BACK, StageKind.UNRESOLVED, StageKind.CONSTRUCTOR, StageKind.ALIASES))
            assertTrue(edgeStages(p, kind).all { st -> st.summary.edges.none { it.edge.to.base == AccessPathBase.Zero } })
    }

    @Test fun `same lhs and argument keeps input argument binding but no independent lhs binding`(): Unit {
        val original = v.call("staticCall") as JIRAssignInst
        val call = v.expr(original)
        val s = JIRAssignInst(original.location, call.args[0], call)
        val p = v.builder(s).build()
        val input = stage(p, StageKind.BIND_IN).summary.edges.map { it.edge }
        assertEquals(1, input.count { it.from.base == arg0 })
        assertEquals(move(arg0, arg0), input.single { it.from.base == arg0 })
        assertEquals(listOf(move(arg0, arg0), move(ret, arg0)), stage(p, StageKind.BIND_BACK).summary.edges
            .map { it.edge }.filter { it.to.base == arg0 })
    }

    @Test fun `unresolved identity and pass rules remain separate parallel stages with distinct filters`(): Unit {
        val rules = CallRules().apply { passes = listOf(pass(listOf(CopyAllMarks(Argument(0), Result)))) }
        val p = v.builder(rules = rules).build()
        val stages = edgeStages(p, StageKind.UNRESOLVED)
        assertEquals(2, stages.size)
        assertTrue(stages.all { it.from == CallPoint.ADDED && it.to == CallPoint.RETURNED })
        assertEquals(listOf(move(STATIC, STATIC), move(arg0, arg0), move(arg1, arg1)), stages[0].summary.edges.map { it.edge })
        assertTrue(stages[0].summary.typeFilters.isEmpty())
        assertTrue(stages[0].summary.resultFilters.isEmpty())
        assertEquals(listOf(move(arg0, arg0), move(arg0, ret)), stages[1].summary.edges.map { it.edge })
        assertEquals(setOf(arg0), stages[1].summary.typeFilters.keys)
        assertEquals(setOf(ret), stages[1].summary.resultFilters.keys)
        assertEquals(setOf(arg0), p.passReads)
    }

    @Test fun `constructor pass over includes statics receiver and arguments bypasses returned rewriter`(): Unit {
        val s = v.call("constructor")
        val call = v.expr(s) as JIRInstanceCallExpr
        val p = v.builder("constructor", resolutions = listOf(v.resolved("<init>"))).build()
        val st = stage(p, StageKind.CONSTRUCTOR)
        assertEquals(CallPoint.ADDED, st.from)
        assertEquals(CallPoint.REWRITTEN, st.to)
        assertEquals(listOf(move(STATIC, STATIC), move(AccessPathBase.This, AccessPathBase.This), move(arg0, arg0)), st.summary.edges.map { it.edge })
        assertTrue(st.summary.typeFilters.isEmpty() && st.summary.resultFilters.isEmpty())
        assertTrue(st.summary.edges.none { it.edge.from.base == AccessPathBase.Zero })
        assertTrue(accessPathBase(call.instance) in p.touched)
        assertTrue(p.stages.any { it is CallStage.Callees })
        assertTrue(p.stages.filterIsInstance<CallStage.Actions>().any { it.from == CallPoint.RETURNED })
    }

    @Test fun `resolution table drops empty targets keeps mixed targets and unions independent failures`(): Unit {
        data class Row(val resolutions: List<MethodResolutionResult>, val expected: List<MethodKey>, val unresolved: Boolean)
        val nonempty = v.resolved()
        val emptyMethod = v.resolved("external")
        val other = v.resolved("lambdaImpl")
        val failure = MethodResolutionResult.MethodResolutionFailed
        val rows = listOf(
            Row(emptyList(), emptyList(), true), Row(listOf(failure), emptyList(), true),
            Row(listOf(emptyMethod), emptyList(), true), Row(listOf(nonempty), listOf(v.key("target")), false),
            Row(listOf(emptyMethod, nonempty), listOf(v.key("target")), false),
            Row(listOf(nonempty, failure), listOf(v.key("target")), true),
            Row(listOf(nonempty, other, nonempty), listOf(v.key("target"), v.key("lambdaImpl")), false),
        )
        for (row in rows) {
            val p = v.builder(resolutions = row.resolutions).build()
            assertEquals(row.expected, p.stages.filterIsInstance<CallStage.Callees>().flatMap { it.callees }, row.toString())
            assertEquals(if (row.unresolved) 2 else 0, edgeStages(p, StageKind.UNRESOLVED).size, row.toString())
        }
    }

    @Test fun `callee contexts and resolver inputs retain the caller method key and statement`(): Unit {
        val context = object : MethodContext {}
        val caller = v.key(context = context)
        val s = v.call("staticCall")
        val call = v.expr(s)
        var invoked = 0
        val builder = JIRCallPlanBuilder(v.manager, v.forms(), v.checker, v.typeFilters, caller, s, call,
            { actualCall, actualStatement, ctx ->
                invoked++
                assertSame(call, actualCall)
                assertSame(s, actualStatement)
                assertSame(caller, ctx.methodEntryPoint)
                assertNull(ctx.aliasAnalysis)
                listOf(v.resolved(context = context), v.resolved(context = EmptyMethodContext))
            }, v.epResolver, emptyMap())
        assertEquals(listOf(v.key("target", context), v.key("target")), builder.build().stages
            .filterIsInstance<CallStage.Callees>().single().callees)
        assertEquals(1, invoked)
    }

    @Test fun `lambda resolution uses only frozen implementations and keeps separate resolver failures`(): Unit {
        val s = v.call("lambdaCall")
        val expr = v.expr(s) as JIRVirtualCallExpr
        val lambda = MethodResolutionResult.Lambda(expr, expr.method.method)
        val index = s.location.index
        val known = linkedSetOf(v.method("lambdaImpl"), v.method("lambdaOther"))
        for (failure in listOf(false, true)) {
            val results = if (failure) listOf(lambda, MethodResolutionResult.MethodResolutionFailed) else listOf(lambda)
            val p = v.builder("lambdaCall", resolutions = results, lambdas = mapOf(index to known)).build()
            assertEquals(listOf(v.key("lambdaImpl"), v.key("lambdaOther")), p.stages.filterIsInstance<CallStage.Callees>().single().callees)
            assertEquals(if (failure) 2 else 0, edgeStages(p, StageKind.UNRESOLVED).size)
        }
        for (snapshot in listOf<Map<Int, Set<org.opentaint.ir.api.jvm.JIRMethod>>>(emptyMap(), mapOf(index to emptySet()))) {
            val p = v.builder("lambdaCall", resolutions = listOf(lambda, v.resolved()), lambdas = snapshot).build()
            assertEquals(listOf(v.key("target")), p.stages.filterIsInstance<CallStage.Callees>().single().callees)
            assertEquals(2, edgeStages(p, StageKind.UNRESOLVED).size, "unknown lambda is an independent failure")
        }
    }

    @Test fun `all expanded empty lambda targets are unresolved but empty siblings add no failure`(): Unit {
        val s = v.call("lambdaCall")
        val expr = v.expr(s) as JIRVirtualCallExpr
        val lambda = MethodResolutionResult.Lambda(expr, expr.method.method)
        val index = s.location.index
        val emptyImpl = v.method("emptyLambda")
        val validImpl = v.method("lambdaImpl")
        val allEmpty = v.builder("lambdaCall", resolutions = listOf(lambda), lambdas = mapOf(index to setOf(emptyImpl))).build()
        assertTrue(allEmpty.stages.none { it is CallStage.Callees })
        assertEquals(2, edgeStages(allEmpty, StageKind.UNRESOLVED).size)
        val mixed = v.builder("lambdaCall", resolutions = listOf(lambda), lambdas = mapOf(index to linkedSetOf(emptyImpl, validImpl))).build()
        assertEquals(listOf(v.key("lambdaImpl")), mixed.stages.filterIsInstance<CallStage.Callees>().single().callees)
        assertTrue(edgeStages(mixed, StageKind.UNRESOLVED).isEmpty())
        val sibling = v.builder("lambdaCall", resolutions = listOf(lambda, v.resolved()), lambdas = mapOf(index to setOf(emptyImpl))).build()
        assertEquals(listOf(v.key("target")), sibling.stages.filterIsInstance<CallStage.Callees>().single().callees)
        assertTrue(edgeStages(sibling, StageKind.UNRESOLVED).isEmpty())
    }

    @Test fun `lambda input map and implementation sets are copied before later caller mutations`(): Unit {
        val s = v.call("lambdaCall")
        val expr = v.expr(s) as JIRVirtualCallExpr
        val implementations = linkedSetOf(v.method("lambdaImpl"))
        val snapshot = linkedMapOf(s.location.index to implementations as Set<org.opentaint.ir.api.jvm.JIRMethod>)
        val builder = v.builder("lambdaCall", resolutions = listOf(MethodResolutionResult.Lambda(expr, expr.method.method)), lambdas = snapshot)
        implementations.clear()
        snapshot.clear()
        assertEquals(listOf(v.key("lambdaImpl")), builder.build().stages.filterIsInstance<CallStage.Callees>().single().callees)
    }

    @Test fun `all call rule queries use the named callee null fact and correct all-relevant selection`(): Unit {
        val rules = CallRules()
        v.builder(rules = rules, resolutions = listOf(v.resolved("lambdaImpl"), MethodResolutionResult.MethodResolutionFailed)).build()
        assertEquals(setOf("sink" to false, "source" to false, "cleaner" to false, "pass" to false,
            "source" to true, "cleaner" to true), rules.queries.map { it.kind to it.allRelevant }.toSet())
        assertEquals(6, rules.queries.size)
        assertTrue(rules.queries.all { it.method == v.method("target") && it.statement === v.call("staticCall") && it.fact == null })
    }

    @Test fun `positive and negative pass mark conditions apply both copy actions without conjunction`(): Unit {
        for (negated in listOf(false, true)) {
            val rules = CallRules().apply {
                passes = listOf(pass(listOf(CopyAllMarks(Argument(0), any(Result)), CopyMark(RuleMark("T"), Argument(0), any(Result))), condition(negated = negated)))
            }
            val p = v.builder(rules = rules).build()
            val summary = edgeStages(p, StageKind.UNRESOLVED)[1].summary
            assertEquals(listOf(move(arg0, arg0), PathEdge(side(arg0), side(ret).copy(tail = Tail.ANY), empty),
                PathEdge(fact(arg0), fact(ret, Tail.ANY), empty)), summary.edges.map { it.edge })
            assertTrue(summary.conjunctions.isEmpty())
            assertTrue(summary.edges.drop(1).all { it.may })
            assertEquals(setOf(arg0), p.passReads)
        }
        val falseRules = CallRules().apply { passes = listOf(pass(listOf(CopyAllMarks(Argument(0), Result)), mkFalse())) }
        val falsePlan = v.builder(rules = falseRules).build()
        assertTrue(edgeStages(falsePlan, StageKind.UNRESOLVED)[1].summary.edges.isEmpty())
        assertTrue(falsePlan.passReads.isEmpty())
    }

    @Test fun `default getter rules join filtered pass stage and stay outside passReads`(): Unit {
        val model = JIRMethodGetDefault(object : JIRMethodGetDefault.Configuration {
            override fun enableDefaultPropagationForClass(cls: org.opentaint.ir.api.jvm.JIRClassOrInterface) = true
        })
        val p = v.builder("getter", defaultGetModel = model).build()
        val summaries = edgeStages(p, StageKind.UNRESOLVED).map { it.summary }
        val element = v.manager.accessors.index(org.opentaint.dataflow.ap.ifds.ElementAccessor)
        assertEquals(listOf(move(STATIC, STATIC), move(AccessPathBase.This, AccessPathBase.This)), summaries[0].edges.map { it.edge })
        assertTrue(summaries[0].typeFilters.isEmpty() && summaries[0].resultFilters.isEmpty())
        assertEquals(listOf(move(AccessPathBase.This, AccessPathBase.This), move(AccessPathBase.This, ret),
            move(AccessPathBase.This, ret, listOf(element))), summaries[1].edges.map { it.edge })
        assertEquals(setOf(AccessPathBase.This), summaries[1].typeFilters.keys)
        assertEquals(setOf(ret), summaries[1].resultFilters.keys)
        assertTrue(p.passReads.isEmpty())
        assertTrue(edgeStages(v.builder("getter").build(), StageKind.UNRESOLVED)[1].summary.edges.isEmpty())
    }

    @Test fun `invalid whole rules give no partial sources passes cleaners sinks or all-relevant rewrites`(): Unit {
        val bad = field(any(Argument(0)))
        val info = user("R")
        val invalidSource = source(listOf(AssignMark(RuleMark("T"), Result), AssignMark(RuleMark("T"), bad)), mkFalse(), info)
        val invalidPass = pass(listOf(CopyAllMarks(Argument(0), Result), AssignMark(RuleMark("T"), bad)), mkFalse())
        val invalidCleaner = cleaner(listOf(RemoveMark(RuleMark("T"), Argument(0)), CopyAllMarks(bad, Result)), mkFalse(), info)
        val rules = CallRules().apply {
            sources = listOf(invalidSource, source(listOf(AssignMark(RuleMark("U"), Result))))
            passes = listOf(invalidPass)
            cleaners = listOf(invalidCleaner)
            sinks = listOf(sink(listOf(AssignMark(RuleMark("T"), Result), AssignMark(RuleMark("T"), bad)), mkFalse()))
        }
        val p = v.builder(rules = rules).build()
        assertEquals(listOf(ZERO_KEEP, PathEdge(ZERO_FACT, fact(ret, mark = u), empty)), stage(p, StageKind.SOURCES).summary.edges.map { it.edge })
        assertTrue(edgeStages(p, StageKind.UNRESOLVED)[1].summary.edges.isEmpty())
        assertTrue(actions(p, CallPoint.BOUND, CallPoint.ADDED).isEmpty())
        assertTrue(actions(p, CallPoint.RETURNED, CallPoint.REWRITTEN).isEmpty())
        assertTrue(p.sinks.isEmpty())
    }

    @Test fun `rewriter selection retains nonfalse user sources and only true user selected cleaners`(): Unit {
        val r = v.manager.marks.mark("R")
        val s = v.manager.marks.mark("S")
        val infoR = user("R")
        val rules = CallRules().apply {
            allSources = listOf(
                source(listOf(AssignMark(RuleMark("T"), Result))),
                source(listOf(AssignMark(RuleMark("T"), Result)), mkFalse(), infoR),
                source(listOf(AssignMark(RuleMark("T"), Result)), mkTrue(), infoR),
                source(listOf(AssignMark(RuleMark("T"), any(Argument(0)))), condition(), infoR),
                source(listOf(AssignMark(RuleMark("T"), any(Argument(1)))), condition(negated = true), infoR),
            )
            allCleaners = listOf(
                cleaner(listOf(RemoveMark(RuleMark("T"), Argument(0))), mkTrue(), infoR),
                cleaner(listOf(RemoveMark(RuleMark("T"), any(Argument(1)))), mkTrue(), user("S")),
                cleaner(listOf(RemoveMark(RuleMark("T"), Result)), condition(), infoR),
                cleaner(listOf(RemoveMark(RuleMark("T"), Result)), condition(negated = true), infoR),
                cleaner(listOf(RemoveMark(RuleMark("T"), Result)), mkFalse(), infoR),
                cleaner(listOf(RemoveAllMarks(Result)), mkTrue(), infoR),
            )
        }
        val p = v.builder(rules = rules).build()
        val expected = listOf(Cleaner(ret, null, CleanReach.EXACT, r), Cleaner(arg0, null, CleanReach.AT_AND_BELOW, r),
            Cleaner(arg1, null, CleanReach.AT_AND_BELOW, r), Cleaner(arg0, null, CleanReach.EXACT, r), Cleaner(arg1, null, CleanReach.BELOW, s))
        assertEquals(expected.map(::cleanerValue), actions(p, CallPoint.RETURNED, CallPoint.REWRITTEN)
            .map { cleanerValue((it as CleanStep.Clean).cleaner) })
        assertEquals(listOf(ZERO_KEEP), stage(p, StageKind.SOURCES).summary.edges.map { it.edge })
        assertTrue(actions(p, CallPoint.BOUND, CallPoint.ADDED).isEmpty())
    }

    @Test fun `sink end facts and source forms bypass returned actions through the rewritten point`(): Unit {
        val rules = CallRules().apply {
            sources = listOf(source(listOf(AssignMark(RuleMark("T"), any(Result)))))
            sinks = listOf(sink(listOf(AssignMark(RuleMark("U"), Result))))
            allSources = listOf(source(listOf(AssignMark(RuleMark("T"), any(Result))), mkTrue(), user("T")))
        }
        val p = v.builder(rules = rules, resolutions = listOf(v.resolved())).build()
        val sk = p.sinks.single()
        val ends = stage(p, StageKind.END_FACTS)
        assertEquals(CallPoint.BOUND, ends.from)
        assertEquals(CallPoint.REWRITTEN, ends.to)
        assertSame(sk, (ends.guard as Guard.SinkTriggered).sink)
        assertSame(sk.endFacts.single().edge, ends.summary.edges.single().edge)
        assertEquals(listOf(PathEdge(ZERO_FACT, fact(ret, mark = u), empty)), ends.summary.edges.map { it.edge })
        val sources = stage(p, StageKind.SOURCES)
        assertEquals(CallPoint.REWRITTEN, sources.to)
        assertEquals(listOf(ZERO_KEEP, PathEdge(ZERO_FACT, fact(ret, Tail.ANY_TAINT), empty)), sources.summary.edges.map { it.edge })
        assertEquals(CleanReach.AT_AND_BELOW, (actions(p, CallPoint.RETURNED, CallPoint.REWRITTEN).single() as CleanStep.Clean).cleaner.reach)
        assertEquals(listOf(StageKind.BIND_IN, StageKind.END_FACTS, StageKind.SOURCES, StageKind.BIND_BACK), p.stages.filterIsInstance<CallStage.Edges>().map { it.kind })
    }

    @Test fun `sink array alternatives use caller static argument types and retain trigger identity`(): Unit {
        val rules = CallRules().apply { sinks = listOf(sink(emptyList(), condition())) }
        val p = v.builder(rules = rules).build()
        val element = v.manager.accessors.index(org.opentaint.dataflow.ap.ifds.ElementAccessor)
        assertEquals(listOf(0, 1), p.sinks.map { it.alternative })
        assertEquals(listOf(listOf(Pattern(fact(arg0), empty)), listOf(Pattern(fact(arg0).copy(path = listOf(element)), empty))), p.sinks.map { it.patterns })
        assertTrue(edgeStages(p, StageKind.END_FACTS).isEmpty())
    }

    @Test fun `cleaner rule actions keep declared list order before returned rewriter actions`(): Unit {
        val rules = CallRules().apply {
            cleaners = listOf(cleaner(listOf(RemoveMark(RuleMark("T"), Argument(0)), RemoveAllMarks(Argument(1)))),
                cleaner(listOf(RemoveMark(RuleMark("U"), Result))))
        }
        val p = v.builder(rules = rules).build()
        assertEquals(listOf(Cleaner(arg0, null, CleanReach.EXACT, t), Cleaner(arg1, null, CleanReach.AT_AND_BELOW, null),
            Cleaner(ret, null, CleanReach.EXACT, u)).map(::cleanerValue), actions(p, CallPoint.BOUND, CallPoint.ADDED)
            .map { cleanerValue((it as CleanStep.Clean).cleaner) })
        assertTrue(actions(p, CallPoint.RETURNED, CallPoint.REWRITTEN).isEmpty())
    }

    @Test fun `local bind-back aliases are sibling mappings guarded by memory effect and full paths`(): Unit {
        val inst = localCall()
        val requests = arrayListOf<AccessPathBase.LocalVar>()
        val q = listOf(AliasAccessor.Field(owner, "next", owner), AliasAccessor.Array, AliasAccessor.Field(owner, "f", "java.lang.Object"))
        val target = AccessPathBase.LocalVar(901)
        val p = v.builder(inst, persistedAliases = { local, at ->
            assertSame(inst, at)
            requests += local
            if (local.idx == 900) listOf(AliasApInfo(local, emptyList()), AliasApInfo(local, q), AliasApInfo(target, q)) else emptyList()
        }).build()
        assertEquals(listOf(AccessPathBase.LocalVar(800), AccessPathBase.LocalVar(801), AccessPathBase.LocalVar(900)), requests)
        val next = v.manager.accessors.index(FieldAccessor(owner, "next", owner))
        val element = v.manager.accessors.index(org.opentaint.dataflow.ap.ifds.ElementAccessor)
        val f = v.manager.accessors.index(FieldAccessor(owner, "f", "java.lang.Object"))
        val aliases = stage(p, StageKind.ALIASES)
        assertEquals(CallPoint.REWRITTEN, aliases.from)
        assertEquals(CallPoint.AFTER, aliases.to)
        assertSame(Guard.MemoryEffect, aliases.guard)
        assertEquals(listOf(move(ret, AccessPathBase.LocalVar(900), listOf(next, element, f)), move(ret, target, listOf(next, element, f))), aliases.summary.edges.map { it.edge })
        assertTrue(aliases.summary.typeFilters.isEmpty() && aliases.summary.resultFilters.isEmpty())
        assertFalse(target in p.touched)
        assertTrue(stage(p, StageKind.BIND_BACK).summary.edges.any { it.edge == move(ret, AccessPathBase.LocalVar(900)) })
    }

    @Test fun `aliases callback is not called for nonlocal or constant bind-back targets`(): Unit {
        for (name in listOf("constant", "receiverArgument")) {
            val queries = arrayListOf<AccessPathBase.LocalVar>()
            val p = v.builder(name, persistedAliases = { local, _ -> queries += local; emptyList() }).build()
            val expected = stage(p, StageKind.BIND_BACK).summary.edges.map { it.edge.to.base }.filterIsInstance<AccessPathBase.LocalVar>()
            assertEquals(expected, queries)
            assertTrue(edgeStages(p, StageKind.ALIASES).isEmpty())
        }
    }

    @Test fun `deep alias paths are uncut owned snapshots and reversed alias bases gain pass over`(): Unit {
        val inst = localCall()
        val aliasBase = AccessPathBase.LocalVar(901)
        val accessor = AliasAccessor.Field(owner, "next", owner)
        val path = MutableList<AliasAccessor>(32768) { accessor }
        val aliases = arrayListOf(AliasApInfo(aliasBase, path))
        val p = v.builder(inst, persistedAliases = { local, _ -> if (local.idx == 900) aliases else emptyList() }).build()
        path.clear(); aliases.clear()
        val me = stage(p, StageKind.ALIASES).summary.edges.single()
        val next = v.manager.accessors.index(FieldAccessor(owner, "next", owner))
        assertEquals(32768, me.edge.to.path.size)
        assertTrue(me.edge.to.path.all { it == next })
        assertEquals(move(ret, aliasBase, List(32768) { next }), me.edge)
        assertFailsWith<UnsupportedOperationException> { (me.edge.to.path as MutableList<Int>).clear() }
        val back = p.reversed()
        assertTrue(aliasBase in back.touched)
        assertEquals(listOf(move(aliasBase, aliasBase)), stage(back, StageKind.PASS_OVER).summary.edges.map { it.edge })
        val reversedAlias = stage(back, StageKind.ALIASES)
        assertNull(reversedAlias.guard)
        assertSame(me.edge, reversedAlias.summary.edges.single().forward)
        assertEquals(me.edge.to, reversedAlias.summary.edges.single().edge.from)
    }

    @Test fun `reversed end facts keep sink triggers and reverse action order while bindings lose filters`(): Unit {
        val rules = CallRules().apply {
            sinks = listOf(sink(listOf(AssignMark(RuleMark("U"), Result))))
            cleaners = listOf(cleaner(listOf(RemoveMark(RuleMark("T"), Argument(0)), RemoveMark(RuleMark("U"), Argument(1)))))
        }
        val p = v.builder(rules = rules).build()
        val reversed = p.reversed()
        val ends = stage(reversed, StageKind.END_FACTS)
        assertSame(p.sinks.single(), ends.trigger)
        assertNull(ends.guard)
        assertEquals(listOf(arg1, arg0), actions(reversed, CallPoint.ADDED, CallPoint.BOUND).map { (it as CleanStep.Clean).cleaner.base })
        for (st in reversed.stages.filterIsInstance<CallStage.Edges>()) {
            assertTrue(st.summary.typeFilters.isEmpty())
            assertTrue(st.summary.resultFilters.isEmpty())
        }
    }

    @Test fun `repeated argument filters are conjoined independently on both binding directions`(): Unit {
        val p = v.builder("repeated").build()
        val accessor = FieldAccessor(owner, "f", "java.lang.Object")
        for (filter in listOf(stage(p, StageKind.BIND_IN).summary.typeFilters.getValue(arg0),
            stage(p, StageKind.BIND_BACK).summary.resultFilters.getValue(arg0))) {
            val before = v.checker.localFactsTotal.sum()
            assertEquals(org.opentaint.dataflow.ap.ifds.FactTypeChecker.FilterResult.Accept, filter.may.check(accessor))
            assertEquals(2L, v.checker.localFactsTotal.sum() - before)
        }
    }

    @Test fun `conditional source cubes give independent plain alternatives and a must conjunction`(): Unit {
        val expr: Condition = CommonCondition.Or(listOf(condition(), CommonCondition.And(listOf(condition(), condition(Argument(1))))))
        val rules = CallRules().apply { sources = listOf(source(listOf(AssignMark(RuleMark("U"), any(Result))), expr)) }
        val summary = stage(v.builder(rules = rules).build(), StageKind.SOURCES).summary
        assertEquals(listOf(ZERO_KEEP, PathEdge(fact(arg0), fact(ret, Tail.ANY_TAINT, u), empty)), summary.edges.map { it.edge })
        val conjunction = summary.conjunctions.single()
        assertEquals(setOf(Pattern(fact(arg0), empty), Pattern(fact(arg1), empty)), conjunction.literals.toSet())
        assertEquals(fact(ret, Tail.ANY_TAINT, u), conjunction.target)
        assertEquals(setOf(AccessPathBase.Zero, arg0, arg1, ret), summary.touched)
    }

    @Test fun `static class sources field sources and kills keep complete class field geometry`(): Unit {
        val classPosition = ClassStatic(owner)
        val staticField = PositionWithAccess(classPosition, PositionAccessor.FieldAccessor(owner, "sField", "java.lang.Object"))
        val cls = v.manager.accessors.index(org.opentaint.dataflow.ap.ifds.ClassStaticAccessor(owner))
        val sf = v.manager.accessors.index(FieldAccessor(owner, "sField", "java.lang.Object"))
        val rules = CallRules().apply {
            sources = listOf(source(listOf(AssignMark(RuleMark("T"), classPosition), AssignMark(RuleMark("U"), any(staticField)))))
            cleaners = listOf(cleaner(listOf(RemoveAllMarks(staticField))))
            passes = listOf(pass(listOf(CopyAllMarks(staticField, Result))))
        }
        val p = v.builder(rules = rules).build()
        assertEquals(listOf(ZERO_KEEP, PathEdge(ZERO_FACT, fact(STATIC).copy(path = listOf(cls)), empty),
            PathEdge(ZERO_FACT, fact(STATIC, Tail.ANY_TAINT, u).copy(path = listOf(cls, sf)), empty)), stage(p, StageKind.SOURCES).summary.edges.map { it.edge })
        val kill = actions(p, CallPoint.BOUND, CallPoint.ADDED).single() as CleanStep.Kill
        assertEquals(setOf(STATIC), kill.keepEdges.touched)
        assertEquals(listOf(PathEdge(side(STATIC), side(STATIC), ExclusionSet.of(cls)),
            PathEdge(side(STATIC, listOf(cls)), side(STATIC, listOf(cls)), ExclusionSet.of(sf))), kill.keepEdges.edges.map { it.edge })
        val passes = edgeStages(p, StageKind.UNRESOLVED)[1].summary
        assertEquals(listOf(move(STATIC, STATIC), PathEdge(side(STATIC, listOf(cls, sf)), side(ret), empty)), passes.edges.map { it.edge })
        assertFalse(STATIC in passes.typeFilters)
        assertEquals(setOf(STATIC), p.passReads)
    }

    @Test fun `direct generated method cleaners follow user cleaner steps and bypass configuration positions`(): Unit {
        val original = v.call("staticCall") as JIRAssignInst
        val call = v.expr(original) as JIRStaticCallExpr
        val direct = Cleaner(STATIC, null, CleanReach.AT_AND_BELOW, null)
        val named = object : org.opentaint.ir.api.jvm.JIRMethod by call.method.method, CallCleaners {
            override val callCleaners = listOf(direct)
        }
        val typed = object : org.opentaint.ir.api.jvm.JIRTypedMethod by call.method { override val method = named }
        val reference = object : TypedMethodRef {
            override val name = typed.name
            override val method = typed
        }
        val s = JIRAssignInst(original.location, original.lhv, JIRStaticCallExpr(reference, call.args))
        val rules = CallRules().apply { cleaners = listOf(cleaner(listOf(RemoveMark(RuleMark("T"), Argument(0))))) }
        val p = v.builder(s, rules).build()
        assertEquals(listOf(Cleaner(arg0, null, CleanReach.EXACT, t), direct).map(::cleanerValue),
            actions(p, CallPoint.BOUND, CallPoint.ADDED).map { cleanerValue((it as CleanStep.Clean).cleaner) })
        assertSame(direct, (actions(p, CallPoint.BOUND, CallPoint.ADDED).last() as CleanStep.Clean).cleaner)
    }
}
