package org.opentaint.dataflow.jvm.bidi

import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.ap.ifds.ClassStaticAccessor
import org.opentaint.dataflow.ap.ifds.ElementAccessor
import org.opentaint.dataflow.ap.ifds.FieldAccessor
import org.opentaint.dataflow.ap.ifds.access.FactAp
import org.opentaint.dataflow.ap.ifds.access.InitialFactAp
import org.opentaint.dataflow.bidi.ap.*
import org.opentaint.dataflow.bidi.interp.*
import org.opentaint.dataflow.configuration.CommonCondition
import org.opentaint.dataflow.configuration.CommonTaintConfigurationSinkMeta
import org.opentaint.dataflow.configuration.mkTrue
import org.opentaint.dataflow.configuration.jvm.*
import org.opentaint.dataflow.configuration.jvm.TaintMark as RuleMark
import org.opentaint.dataflow.configuration.jvm.serialized.UserDefinedRuleInfo
import org.opentaint.dataflow.jvm.ap.ifds.LambdaAnonymousClassFeature
import org.opentaint.dataflow.jvm.ap.ifds.LambdaExpressionToAnonymousClassTransformerFeature
import org.opentaint.dataflow.jvm.ap.ifds.taint.TaintRulesProvider
import org.opentaint.dataflow.jvm.bidi.fixtures.NoTaintRules
import org.opentaint.dataflow.jvm.bidi.interp.JIRInterpreter
import org.opentaint.ir.api.common.CommonMethod
import org.opentaint.ir.api.common.cfg.CommonInst
import org.opentaint.ir.api.jvm.JIRInstExtFeature
import org.opentaint.ir.api.jvm.JIRMethod
import org.opentaint.ir.api.jvm.cfg.*
import org.opentaint.ir.api.jvm.ext.cfg.callExpr
import org.opentaint.jvm.graph.JMethodBoundaryInstFeature
import org.opentaint.jvm.graph.JMethodExitExceptionalInst
import java.util.Collections
import java.util.IdentityHashMap
import kotlin.test.*

/** Construction checks use public forms, separately from the raw and tree transfer oracles. */
class JIRFormsContractTest : BidiJIRTestUtils() {
    override val transformStringConcat = true
    private val stat = AccessPathBase.ClassStatic
    private val zero = AccessPathBase.Zero
    private enum class Role { STATEMENT, STAGE, BINDING, GEN }

    private fun accessor(id: Int) {
        val a = manager.accessors.accessor(id)
        assertTrue(a is FieldAccessor || a is ElementAccessor || a is ClassStaticAccessor, "$a")
    }
    private fun path(f: PathFact) {
        f.path.forEach(::accessor)
        if (f.tail == Tail.EXACT) assertIs<MarkSlot.Concrete>(f.mark)
    }
    private fun exclusion(e: ExclusionSet) {
        if (e is ExclusionSet.Concrete) {
            val ids = e.ids.toList()
            assertEquals(ids.sorted().distinct(), ids)
            ids.forEach(::accessor)
        }
    }
    private fun summary(s: StatementSummary, backward: Boolean, role: Role = Role.STATEMENT,
                        sourceTargets: Boolean = false) {
        val readsTouched = role != Role.GEN
        assertTrue(zero !in s.typeFilters && zero !in s.resultFilters)
        assertTrue(s.typeFilters.keys.all { it in s.touched })
        assertTrue(s.resultFilters.keys.all { it in s.touched })
        if (role == Role.GEN) {
            assertTrue(s.typeFilters.isEmpty() && s.resultFilters.isEmpty())
            assertTrue(s.conjunctions.isEmpty())
        }
        if (role == Role.BINDING) assertTrue(s.conjunctions.isEmpty())
        if (!backward && zero in s.touched &&
            (role == Role.STATEMENT || (role == Role.STAGE && sourceTargets))) {
            assertTrue(s.edges.any { it.edge == ZERO_KEEP })
        }
        if (backward) {
            assertTrue(s.typeFilters.isEmpty() && s.resultFilters.isEmpty())
            assertTrue(s.conjunctions.isEmpty())
        }
        for (me in s.edges) {
            val e = me.edge
            path(e.from); path(e.to)
            exclusion(e.exclusion); exclusion(e.fromExclusion); exclusion(e.toExclusion)
            if (readsTouched) assertTrue(e.from.base in s.touched, "$role reads ${e.from.base}")
            val fromMark = e.from.mark
            if (fromMark is MarkSlot.Star) assertTrue(fromMark.excluded.isEmpty)
            if (e.to.mark is MarkSlot.Concrete) assertIs<MarkSlot.Concrete>(e.from.mark)
            if (e.from.tail == Tail.EXACT) {
                assertIs<MarkSlot.Concrete>(e.from.mark)
                assertTrue(e.to.tail != Tail.STAR)
            }
            assertEquals(ExclusionSet.Empty, e.fromExclusion)
            assertEquals(ExclusionSet.Empty, e.toExclusion)
            if (e.exclusion != ExclusionSet.Empty) {
                assertEquals(Tail.STAR, e.from.tail)
                assertEquals(Tail.STAR, e.to.tail)
                assertEquals(e.from, e.to, "Only a same-path keep carries an exclusion")
            }
            if (role == Role.BINDING) {
                assertEquals(Tail.STAR, e.from.tail)
                assertEquals(Tail.STAR, e.to.tail)
                assertEquals(MarkSlot.STAR, e.from.mark)
                assertEquals(MarkSlot.STAR, e.to.mark)
                assertTrue(e.from.path.isEmpty() && e.to.path.isEmpty())
                assertEquals(ExclusionSet.Empty, e.exclusion)
            }
            assertTrue(e.from.tail != Tail.ANY_TAINT)
            if (backward) assertTrue(e.to.tail != Tail.ANY_TAINT)
            else {
                assertSame(e, me.forward)
                if (e.to.base == zero) {
                    val zeroBinding = role == Role.BINDING && e == keepEdge(zero)
                    val zeroKeep = e == ZERO_KEEP &&
                        (role == Role.STATEMENT || (role == Role.STAGE && sourceTargets))
                    assertTrue(zeroBinding || zeroKeep, "No forward zero generation: $e")
                }
                if (e.to.tail == Tail.ANY_TAINT) {
                    assertTrue(sourceTargets || role == Role.GEN ||
                        (role == Role.STATEMENT && me.isSource), "Must target outside source/end facts: $e")
                    assertIs<MarkSlot.Concrete>(e.from.mark)
                    assertIs<MarkSlot.Concrete>(e.to.mark)
                    assertEquals(ExclusionSet.Empty, e.exclusion)
                    assertEquals(ExclusionSet.Empty, e.toExclusion)
                }
            }
            if (me.conjunctive) assertTrue(backward && me.may)
        }
        for (c in s.conjunctions) {
            assertFalse(backward)
            path(c.target)
            assertIs<MarkSlot.Concrete>(c.target.mark)
            assertTrue(c.target.base != zero)
            assertTrue(c.target.tail == Tail.EXACT || c.target.tail == Tail.ANY_TAINT)
            if (c.target.tail == Tail.ANY_TAINT) assertTrue(sourceTargets)
            c.literals.forEach {
                path(it.fact); exclusion(it.exclusion)
                if (readsTouched) assertTrue(it.fact.base in s.touched)
                assertIs<MarkSlot.Concrete>(it.fact.mark)
                assertTrue(it.fact.tail == Tail.EXACT || it.fact.tail == Tail.ANY)
                assertEquals(ExclusionSet.Empty, it.exclusion)
            }
        }
        for (source in s.sources) {
            assertEquals(backward, source.backward)
            assertFalse(source.predicate.isFalse || source.predicate.isTrue)
            assertTrue(source.actions.isNotEmpty())
            assertTrue(role != Role.GEN && role != Role.BINDING)
            source.predicate.leaves.forEach {
                path(it.fact); exclusion(it.exclusion)
                assertIs<MarkSlot.Concrete>(it.fact.mark)
                assertTrue(it.fact.tail == Tail.EXACT || it.fact.tail == Tail.ANY)
                assertEquals(ExclusionSet.Empty, it.exclusion)
                if (readsTouched) assertTrue(it.fact.base in s.touched)
            }
            source.actions.forEach {
                path(it.target)
                assertIs<MarkSlot.Concrete>(it.target.mark)
                assertTrue(it.target.tail == Tail.EXACT || it.target.tail == Tail.ANY_TAINT)
                assertTrue(it.target.base != zero)
                if (backward) assertTrue(it.target.base in s.touched)
            }
        }
    }
    private fun reversedSummary(f: StatementSummary, b: StatementSummary, role: Role = Role.STATEMENT,
                                sourceTargets: Boolean = false) {
        summary(f, false, role, sourceTargets); summary(b, true, role, sourceTargets)
        for (me in f.edges) assertTrue(b.edges.any { it.forward === me.forward && it.edge == revEdge(me.edge) })
        for (c in f.conjunctions) for (lit in c.literals) {
            val edge = PathEdge(lit.fact, c.target, ExclusionSet.Empty)
            assertTrue(b.edges.any { it.conjunctive && it.may && it.forward == edge && it.edge == revEdge(edge) })
        }
        for (source in f.sources) {
            val reversed = b.sources.single { it.id == source.id }
            assertSame(source.predicate, reversed.predicate)
            assertSame(source.actions, reversed.actions)
            for ((ordinal, literal) in source.predicate.leaves.withIndex()) for (action in source.actions) {
                val forward = PathEdge(literal.fact, action.target, ExclusionSet.Empty)
                if (source.predicate.isUnary(ordinal)) {
                    val edge = reversed.transfer(literal, action)
                    assertEquals(forward, edge.forward)
                    assertEquals(revEdge(forward), edge.edge)
                    assertFalse(edge.conjunctive || edge.may)
                }
                if (source.predicate.isMulti(ordinal)) {
                    val edge = reversed.transfer(literal, action, multi = true)
                    assertEquals(forward, edge.forward)
                    assertEquals(revEdge(forward), edge.edge)
                    assertTrue(edge.conjunctive && edge.may)
                }
            }
        }
        for (base in f.targets - f.touched) assertTrue(b.edges.any { it.edge == keepEdge(base) })
    }
    private fun sink(s: SinkRule) {
        assertTrue(s.patterns.isNotEmpty())
        for (p in s.patterns) {
            path(p.fact); exclusion(p.exclusion)
            assertIs<MarkSlot.Concrete>(p.fact.mark)
            assertTrue(p.fact.tail == Tail.EXACT || p.fact.tail == Tail.ANY)
        }
        for (e in s.endFacts) {
            summary(StatementSummary(emptySet(), listOf(e), emptyList(), emptyMap()), false, Role.GEN)
            assertEquals(ZERO_FACT, e.edge.from)
            assertEquals(ExclusionSet.Empty, e.edge.exclusion)
            assertEquals(ExclusionSet.Empty, e.edge.toExclusion)
            assertIs<MarkSlot.Concrete>(e.edge.to.mark)
            assertTrue(e.edge.to.tail == Tail.EXACT || e.edge.to.tail == Tail.ANY_TAINT)
        }
    }
    private fun rule(f: RuleStatement, b: RuleStatement) {
        reversedSummary(f.summary, b.summary, sourceTargets = true)
        summary(f.endFacts, false, Role.GEN); summary(b.endFacts, true, Role.GEN)
        for (e in f.endFacts.edges) {
            assertEquals(ZERO_FACT, e.edge.from)
            assertIs<MarkSlot.Concrete>(e.edge.to.mark)
            assertEquals(ExclusionSet.Empty, e.edge.exclusion)
            assertEquals(ExclusionSet.Empty, e.edge.toExclusion)
            assertTrue(b.endFacts.edges.any { it.forward === e.forward && it.edge == revEdge(e.edge) })
        }
        assertEquals(f.sinks.size, b.sinks.size)
        f.sinks.forEachIndexed { i, s -> sink(s); assertSame(s, b.sinks[i]) }
        for (base in f.endFacts.targets - b.summary.touched) assertTrue(b.summary.edges.any { it.edge == keepEdge(base) })
    }
    private fun action(f: CleanStep, b: CleanStep, i: JIRInterpreter) {
        when (f) {
            is CleanStep.Clean -> {
                assertTrue(f.cleaner.base != zero)
                assertSame(f.cleaner, assertIs<CleanStep.Clean>(b).cleaner)
                f.cleaner.path?.toList()?.forEach(::accessor)
            }
            is CleanStep.Kill -> reversedSummary(f.keepEdges, assertIs<CleanStep.Kill>(b).keepEdges)
            is CleanStep.Field -> {
                val reversed = assertIs<CleanStep.Field>(b)
                assertIs<AccessPathBase.CleanerTemporary>(f.temporary)
                assertSame(f.temporary, f.cleaner.base)
                assertTrue(f.cleaner.base != zero)
                assertNull(f.cleaner.path)
                assertSame(f.temporary, reversed.temporary)
                assertSame(f.cleaner, reversed.cleaner)
                assertFalse(i.isSummaryBase(f.temporary))
                reversedSummary(f.write, reversed.read)
                reversedSummary(f.read, reversed.write)
                val read = f.read.edges.single { it.edge.to.base == f.temporary }
                assertEquals(Tail.STAR, read.edge.from.tail)
                assertEquals(Tail.STAR, read.edge.to.tail)
                assertTrue(f.read.edges.any { it.edge == keepEdge(read.edge.from.base) })
                val write = f.write.edges.single {
                    it.edge.from.base == f.temporary && it.edge.to.base == read.edge.from.base
                }
                assertEquals(read.edge.from.base, write.edge.to.base)
                assertEquals(read.edge.from.path, write.edge.to.path)
                assertTrue(f.write.edges.filter { it.edge.from.base == write.edge.to.base }
                    .map { it.edge }.containsAll(strongKeep(write.edge.to.base, write.edge.to.path)))
            }
        }
    }
    private fun plan(f: CallPlan, b: CallPlan, i: JIRInterpreter) {
        assertEquals(CallPoint.BEFORE, f.entry); assertEquals(CallPoint.AFTER, f.exit)
        assertEquals(CallPoint.AFTER, b.entry); assertEquals(CallPoint.BEFORE, b.exit)
        assertEquals(f.passReads, b.passReads)
        assertTrue(stat in f.touched)
        f.sinks.forEachIndexed { j, s -> sink(s); assertSame(s, b.sinks[j]) }
        f.stages.forEachIndexed { j, stage ->
            val back = b.stages[j]
            assertEquals(stage.to, back.from); assertEquals(stage.from, back.to)
            when (stage) {
                is CallStage.Edges -> {
                    val r = assertIs<CallStage.Edges>(back)
                    assertNull(r.guard)
                    val role = when (stage.kind) {
                        StageKind.BIND_IN, StageKind.BIND_BACK -> Role.BINDING
                        StageKind.END_FACTS -> Role.GEN
                        else -> Role.STAGE
                    }
                    reversedSummary(stage.summary, r.summary, role, sourceTargets = stage.kind == StageKind.SOURCES)
                    if (stage.kind == StageKind.END_FACTS) {
                        val owning = assertIs<Guard.SinkTriggered>(stage.guard).sink
                        assertTrue(f.sinks.any { it === owning })
                        assertSame(owning, r.trigger)
                        assertEquals(owning.endFacts.map { it.edge }, stage.summary.edges.map { it.edge })
                    } else assertNull(r.trigger)
                    if (stage.kind == StageKind.BIND_IN || stage.kind == StageKind.BIND_BACK)
                        assertTrue(stage.summary.edges.any { it.edge == keepEdge(stat) })
                    if (stage.kind == StageKind.BIND_BACK)
                        assertTrue(stage.summary.edges.none { it.edge.to.base == zero })
                }
                is CallStage.Actions -> {
                    val r = assertIs<CallStage.Actions>(back)
                    assertEquals(stage.steps.size, r.steps.size)
                    stage.steps.forEachIndexed { k, step -> action(step, r.steps[r.steps.lastIndex - k], i) }
                }
                is CallStage.Callees -> {
                    val r = assertIs<CallStage.Callees>(back)
                    stage.callees.forEachIndexed { k, callee -> assertSame(callee, r.callees[k]) }
                }
            }
        }
        for (extra in b.stages.drop(f.stages.size)) {
            val stage = assertIs<CallStage.Edges>(extra)
            assertEquals(StageKind.PASS_OVER, stage.kind)
            assertEquals(CallPoint.AFTER, stage.from)
            assertEquals(CallPoint.BEFORE, stage.to)
            assertNull(stage.guard)
            assertNull(stage.trigger)
            summary(stage.summary, true, Role.STAGE)
            assertTrue(stage.summary.edges.all { it.isIdentity })
        }
        val finals = f.stages.filterIsInstance<CallStage.Edges>().filter { it.to == CallPoint.AFTER }
            .flatMap { it.summary.targets }.toSet() - f.touched
        for (base in finals) assertTrue(b.stages.filterIsInstance<CallStage.Edges>().any {
            it.from == CallPoint.AFTER && it.to == CallPoint.BEFORE && it.summary.edges.any { e -> e.edge == keepEdge(base) }
        })
    }

    @Test fun `actual sample statement boundary and call forms satisfy construction and reversal contracts`(): Unit {
        var statements = 0
        var calls = 0
        for (owner in listOf("sample.bidi.BidiStatementSample", "sample.bidi.BidiRuleSample",
            "sample.bidi.BidiCallSample", "sample.bidi.BidiGraphSample")) {
            for (m in assertNotNull(cp.findClassOrNull(owner)).declaredMethods) {
                if (m.instList.instructions.isEmpty() || m.isClassInitializer) continue
                val forms = interp.forms(key(m))
                assertSame(forms, interp.forms(key(m)))
                rule(forms.entryRules(Direction.FORWARD), forms.entryRules(Direction.BACKWARD))
                for (exit in listOf(normalExit(m), exceptionalExit(m))) {
                    val forward = forms.exitRules(Direction.FORWARD, exit)
                    val back = forms.exitRules(Direction.BACKWARD, exit)
                    rule(forward.rules, back.rules)
                    assertFalse(back.globalStateDrop)
                    assertTrue(back.entryMarks.isEmpty())
                    assertSame(back, forms.exitRules(Direction.BACKWARD, exit))
                }
                for (s in m.instList.instructions) {
                    val call = s.callExpr
                    if (call == null) {
                        val forward = forms.statement(Direction.FORWARD, s)
                        val backward = forms.statement(Direction.BACKWARD, s)
                        reversedSummary(forward, backward)
                        assertSame(forward, forms.statement(Direction.FORWARD, s))
                        assertSame(backward, forms.statement(Direction.BACKWARD, s))
                        statements++
                    } else {
                        val forward = forms.call(Direction.FORWARD, s, call)
                        val backward = forms.call(Direction.BACKWARD, s, call)
                        plan(forward, backward, interp)
                        assertSame(forward, forms.call(Direction.FORWARD, s, call))
                        assertSame(backward, forms.call(Direction.BACKWARD, s, call))
                        calls++
                    }
                }
            }
        }
        assertTrue(statements > 100)
        assertTrue(calls > 20)
    }

    @Test fun `nonempty source conjunction sink endfact pass and field action forms retain their cached identities`(): Unit {
        val target = method("target", "sample.bidi.BidiCallSample")
        val rules = richRules(target)
        val i = interp(rules)
        val caller = method("staticCall", "sample.bidi.BidiCallSample")
        val s = caller.instList.instructions.first { it.callExpr?.method?.method == target }
        val call = assertNotNull(s.callExpr)
        val forms = i.forms(key(caller))
        val f = forms.call(Direction.FORWARD, s, call)
        val b = forms.call(Direction.BACKWARD, s, call)
        plan(f, b, i)
        val source = f.stages.filterIsInstance<CallStage.Edges>().single { it.kind == StageKind.SOURCES }.summary
        assertTrue(source.sources.isNotEmpty())
        assertTrue(source.edges.any { it.isSource && it.edge.to.tail == Tail.EXACT })
        assertTrue(source.sources.any { it.actions.any { action -> action.target.tail == Tail.ANY_TAINT } })
        assertTrue(f.sinks.any { it.predicate!!.leaves.indices.any(it.predicate!!::isMulti) && it.endFacts.isNotEmpty() })
        val fields = f.stages.filterIsInstance<CallStage.Actions>().flatMap { it.steps }.filterIsInstance<CleanStep.Field>()
        assertTrue(fields.size >= 3)
        val identities = Collections.newSetFromMap(IdentityHashMap<AccessPathBase, Boolean>())
        fields.forEach { assertTrue(identities.add(it.temporary)) }
        assertSame(f, forms.call(Direction.FORWARD, s, call))
        assertSame(b, forms.call(Direction.BACKWARD, s, call))
        val boundary = i.forms(key(target))
        rule(boundary.entryRules(Direction.FORWARD), boundary.entryRules(Direction.BACKWARD))
        assertTrue(boundary.entryRules(Direction.FORWARD).summary.edges.any { it.isSource && it.edge.to.tail == Tail.ANY_TAINT })
        for (exit in listOf(normalExit(target), exceptionalExit(target))) {
            val ef = boundary.exitRules(Direction.FORWARD, exit)
            val eb = boundary.exitRules(Direction.BACKWARD, exit)
            rule(ef.rules, eb.rules)
            assertTrue(ef.rules.summary.sources.isNotEmpty())
            assertTrue(ef.rules.sinks.isNotEmpty())
        }

        // Pass rules are emitted only by the unresolved path, so use its real native callee.
        val external = method("external", "sample.bidi.BidiCallSample")
        val unresolved = interp(richRules(external))
        val outside = method("staticExternal", "sample.bidi.BidiCallSample")
        val outsideInst = outside.instList.instructions.first { it.callExpr?.method?.method == external }
        val outsideCall = assertNotNull(outsideInst.callExpr)
        val outsideForms = unresolved.forms(key(outside))
        val forwardPass = outsideForms.call(Direction.FORWARD, outsideInst, outsideCall)
        val backwardPass = outsideForms.call(Direction.BACKWARD, outsideInst, outsideCall)
        plan(forwardPass, backwardPass, unresolved)
        val pass = forwardPass.stages.filterIsInstance<CallStage.Edges>()
            .filter { it.kind == StageKind.UNRESOLVED }.flatMap { it.summary.edges }
            .single { !it.isIdentity && it.edge.to.tail == Tail.ANY }
        assertTrue(pass.may)
        assertTrue(pass.edge.from.tail == Tail.STAR || pass.edge.from.tail == Tail.EXACT)
        val reversedPass = backwardPass.stages.filterIsInstance<CallStage.Edges>()
            .filter { it.kind == StageKind.UNRESOLVED }.flatMap { it.summary.edges }
            .single { it.forward === pass.forward }
        assertTrue(reversedPass.may)
        assertEquals(Tail.ANY, reversedPass.edge.from.tail)
        assertEquals(Tail.ANY, reversedPass.edge.to.tail)
        assertSame(forwardPass, outsideForms.call(Direction.FORWARD, outsideInst, outsideCall))
        assertSame(backwardPass, outsideForms.call(Direction.BACKWARD, outsideInst, outsideCall))
    }

    @Test fun `required linked lambda concat and final boundary features produce analyzable real instructions`(): Unit {
        val features = cp.features.orEmpty()
        assertTrue(features.any { it is LambdaAnonymousClassFeature })
        assertTrue(features.any { it is LambdaExpressionToAnonymousClassTransformerFeature })
        assertSame(JMethodBoundaryInstFeature, features.filterIsInstance<JIRInstExtFeature>().last())
        val lambda = method("lambda", "sample.bidi.BidiGraphSample")
        assertTrue(lambda.instList.instructions.none { it.callExpr is JIRLambdaExpr })
        val allocated = lambda.instList.instructions.mapNotNull(LambdaExpressionToAnonymousClassTransformerFeature::findLambdaAllocation)
        assertTrue(allocated.isNotEmpty())
        for (cls in allocated) for (m in cls.declaredMethods) {
            if (m.instList.instructions.isEmpty()) continue
            assertIs<JMethodExitExceptionalInst>(m.instList.instructions.last())
            val forms = interp.forms(key(m))
            for (s in m.instList.instructions) {
                val call = s.callExpr
                if (call == null) reversedSummary(forms.statement(Direction.FORWARD, s), forms.statement(Direction.BACKWARD, s))
                else plan(forms.call(Direction.FORWARD, s, call), forms.call(Direction.BACKWARD, s, call), interp)
            }
        }
        val concat = method("concat", "sample.bidi.BidiConcatSample")
        assertTrue(concat.instList.instructions.mapNotNull { it.callExpr }.any {
            it.method.name == "concat" && it.method.method.enclosingClass.name == "java.lang.String"
        })
        assertTrue(concat.instList.instructions.none { it.callExpr is JIRLambdaExpr })
        val forms = interp.forms(key(concat))
        for (s in concat.instList.instructions) {
            val call = s.callExpr
            if (call == null) reversedSummary(forms.statement(Direction.FORWARD, s), forms.statement(Direction.BACKWARD, s))
            else {
                val forward = forms.call(Direction.FORWARD, s, call)
                val backward = forms.call(Direction.BACKWARD, s, call)
                plan(forward, backward, interp)
                assertSame(forward, forms.call(Direction.FORWARD, s, call))
                assertSame(backward, forms.call(Direction.BACKWARD, s, call))
            }
        }
    }

    @Test fun `static IR reads and writes retain the class plus field path without consuming class field budget`(): Unit {
        for (name in listOf("staticRead", "staticWrite")) {
            val m = method(name)
            val forms = interp.forms(key(m))
            val s = m.instList.instructions.filterIsInstance<JIRAssignInst>().single {
                val ref = (if (name == "staticRead") it.rhv else it.lhv) as? JIRFieldRef
                ref != null && ref.instance == null
            }
            val summary = forms.statement(Direction.FORWARD, s)
            val path = summary.edges.map { if (name == "staticRead") it.edge.from else it.edge.to }
                .single { it.base == stat && it.path.size == 2 }.path
            assertIs<ClassStaticAccessor>(manager.accessors.accessor(path[0]))
            assertIs<FieldAccessor>(manager.accessors.accessor(path[1]))
            assertEquals(1, assertNotNull(manager.path(path)).counted)
        }
    }

    private fun richRules(target: JIRMethod): TaintRulesProvider {
        fun any(p: Position) = PositionWithAccess(p, PositionAccessor.AnyFieldAccessor)
        fun field(p: Position, name: String) = PositionWithAccess(p,
            PositionAccessor.FieldAccessor("sample.bidi.BidiCallSample", name, "java.lang.Object"))
        fun contains(p: Position, mark: String): Condition = CommonCondition.Atom(ContainsMark(p, RuleMark(mark)))
        val condition: Condition = CommonCondition.And(listOf(contains(Argument(0), "A"), contains(Argument(1), "B")))
        val meta = TaintSinkMeta("construction sink", CommonTaintConfigurationSinkMeta.Severity.Error, null)
        val info = object : UserDefinedRuleInfo { override val relevantTaintMarks = setOf("T") }
        val sources = listOf(TaintMethodSource(target, condition, listOf(AssignMark(RuleMark("T"), any(Result))), null),
            TaintMethodSource(target, mkTrue(), listOf(AssignMark(RuleMark("U"), Result)), null),
            TaintMethodSource(target, mkTrue(), listOf(AssignMark(RuleMark("T"), field(Result, "f"))), info))
        val sinks = listOf(TaintMethodSink(target, condition, listOf(AssignMark(RuleMark("U"), any(Result))), "scan", meta, null))
        val passes = listOf(TaintPassThrough(target, mkTrue(), listOf(CopyAllMarks(Argument(0), any(Result)),
            CopyMark(RuleMark("T"), Argument(1), Result)), null))
        val cleaners = listOf(TaintCleaner(target, mkTrue(), listOf(RemoveMark(RuleMark("T"), field(Argument(0), "f")),
            RemoveMark(RuleMark("T"), field(Argument(0), "g"))), null))
        return object : TaintRulesProvider by NoTaintRules {
            override fun sourceRulesForMethod(method: CommonMethod, statement: CommonInst, fact: FactAp?, allRelevant: Boolean) =
                if (method == target) sources else emptyList()
            override fun sinkRulesForMethod(method: CommonMethod, statement: CommonInst, fact: FactAp?, allRelevant: Boolean) =
                if (method == target) sinks else emptyList()
            override fun passTroughRulesForMethod(method: CommonMethod, statement: CommonInst?, fact: FactAp?, allRelevant: Boolean) =
                if (method == target) passes else emptyList()
            override fun cleanerRulesForMethod(method: CommonMethod, statement: CommonInst, fact: FactAp?, allRelevant: Boolean) =
                if (method == target) cleaners else emptyList()
            override fun entryPointRulesForMethod(method: CommonMethod, statement: CommonInst, fact: FactAp?, allRelevant: Boolean) =
                if (method == target) listOf(TaintEntryPointSource(target, mkTrue(), listOf(AssignMark(RuleMark("V"), any(Argument(0)))), null)) else emptyList()
            override fun exitSourceRulesForMethod(method: CommonMethod, statement: CommonInst, fact: FactAp?, allRelevant: Boolean) =
                if (method == target) listOf(TaintMethodExitSource(target, condition, listOf(AssignMark(RuleMark("W"), any(Result))), null)) else emptyList()
            override fun sinkRulesForMethodExit(method: CommonMethod, statement: CommonInst, fact: FactAp?, initialFacts: Set<InitialFactAp>?, allRelevant: Boolean) =
                if (method == target) listOf(TaintMethodExitSink(target, condition, listOf(AssignMark(RuleMark("U"), Result)), "exit-scan", meta, null)) else emptyList()
        }
    }
}
