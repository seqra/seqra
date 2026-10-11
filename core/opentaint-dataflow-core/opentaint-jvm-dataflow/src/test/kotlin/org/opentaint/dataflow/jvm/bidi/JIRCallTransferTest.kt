package org.opentaint.dataflow.jvm.bidi

import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.ap.ifds.ClassStaticAccessor
import org.opentaint.dataflow.ap.ifds.ElementAccessor
import org.opentaint.dataflow.ap.ifds.FieldAccessor
import org.opentaint.dataflow.bidi.ap.*
import org.opentaint.dataflow.bidi.ap.TaintMark
import org.opentaint.dataflow.bidi.interp.*
import org.opentaint.dataflow.bidi.interp.CallPoint.*
import org.opentaint.dataflow.bidi.store.RequestKind
import org.opentaint.dataflow.configuration.CommonCondition
import org.opentaint.dataflow.configuration.CommonTaintConfigurationSinkMeta
import org.opentaint.dataflow.configuration.TaintCleanReach
import org.opentaint.dataflow.configuration.mkFalse
import org.opentaint.dataflow.configuration.jvm.*
import org.opentaint.dataflow.configuration.jvm.TaintMark as RuleMark
import org.opentaint.dataflow.configuration.jvm.serialized.UserDefinedRuleInfo
import org.opentaint.dataflow.jvm.BasicTestUtils
import org.opentaint.dataflow.jvm.ap.ifds.JIRCallResolver.MethodResolutionResult
import org.opentaint.dataflow.jvm.ap.ifds.JIRLocalAliasAnalysis.AliasAccessor
import org.opentaint.dataflow.jvm.ap.ifds.JIRLocalAliasAnalysis.AliasApInfo
import org.opentaint.dataflow.jvm.ap.ifds.MethodFlowFunctionUtils.accessPathBase
import org.opentaint.dataflow.jvm.bidi.fixtures.CallRules
import org.opentaint.dataflow.jvm.bidi.fixtures.JIRCallTestFixtures
import org.opentaint.dataflow.jvm.bidi.fixtures.JIRRuleTestFixtures
import org.opentaint.ir.api.common.cfg.CommonInst
import org.opentaint.ir.api.jvm.cfg.*
import kotlin.test.*

/** Expected caller facts follow the call rules, independently of the generated stages. */
class JIRCallTransferTest : BasicTestUtils() {
    private val v by lazy { JIRCallTestFixtures(cp) }
    private val m get() = v.manager
    private val ops get() = v.ops
    private val t get() = m.marks.mark("T")
    private val u get() = m.marks.mark("U")
    private val mt get() = MarkSlot.Concrete(t)
    private val mu get() = MarkSlot.Concrete(u)
    private val f get() = field("f")
    private val g get() = field("g")
    private val next get() = field("next", CLS)
    private val element get() = m.accessors.index(ElementAccessor)
    private val arg0 = AccessPathBase.Argument(0)
    private val arg1 = AccessPathBase.Argument(1)
    private val ret = AccessPathBase.Return
    private val stat = AccessPathBase.ClassStatic
    private val other = AccessPathBase.LocalVar(990)
    private val trueCondition get() = JIRRuleTestFixtures.trueCondition
    private val falseCondition get() = mkFalse<JirCondition>()
    private fun contains(position: Position, mark: String): Condition =
        CommonCondition.Atom<JirCondition>(ContainsMark(position, RuleMark(mark)))
    private fun field(name: String, type: String = "java.lang.Object") = m.accessors.index(FieldAccessor(CLS, name, type))
    private fun p(vararg ids: Int) = ids.toList()
    private fun ex(vararg ids: Int) = ExclusionSet.of(ids)
    private fun mx(vararg ids: Int) = MarkSlot.Star(MarkSet(ids))
    private fun fact(base: AccessPathBase, path: List<Int> = emptyList(), tail: Tail = Tail.EXACT,
                     mark: MarkSlot = mt, exclusion: ExclusionSet = ExclusionSet.Empty,
                     demand: Boolean = tail == Tail.ANY) = Conclusion(PathFact(base, path, tail, mark), exclusion, demand)
    private fun flow(base: AccessPathBase, path: List<Int> = emptyList(), exclusion: ExclusionSet = ExclusionSet.Empty,
                     mark: MarkSlot = MarkSlot.STAR) = fact(base, path, Tail.STAR, mark, exclusion)
    private fun must(base: AccessPathBase, path: List<Int> = emptyList(), exclusion: ExclusionSet = ExclusionSet.Empty,
                     mark: MarkSlot = mt) = fact(base, path, Tail.ANY_TAINT, mark, exclusion)
    private fun item(c: Conclusion, candidate: Boolean? = null, premise: Set<Pattern>? = null): PlanItem {
        val initial = if (c.fact.base == AccessPathBase.Zero) ZERO_PATTERN else Pattern(PathFact(c.fact.base,
            emptyList(), if (c.fact.mark is MarkSlot.Star) Tail.STAR else Tail.EXACT,
            if (c.fact.mark is MarkSlot.Star) MarkSlot.STAR else c.fact.mark), ExclusionSet.Empty)
        return PlanItem(premise ?: setOf(initial), c, candidate)
    }
    private fun base(value: JIRValue) = checkNotNull(accessPathBase(value))
    private fun inputBase(s: JIRInst, index: Int = 0) = base(v.expr(s).args[index])
    private fun resultBase(s: JIRInst) = base(assertIs<JIRAssignInst>(s).lhv)
    private fun at(position: Position, name: String, type: String = "java.lang.Object") =
        PositionWithAccess(position, PositionAccessor.FieldAccessor(CLS, name, type))
    private fun any(position: Position) = PositionWithAccess(position, PositionAccessor.AnyFieldAccessor)
    private fun user(vararg names: String) = object : UserDefinedRuleInfo { override val relevantTaintMarks = names.toSet() }
    private fun copyTo(c: Conclusion, to: AccessPathBase, path: List<Int> = c.fact.path) = c.copy(fact = c.fact.copy(base = to, path = path))
    private fun pass(s: JIRInst, actions: List<Action>) = TaintPassThrough(v.expr(s).method.method, trueCondition, actions, null)
    private fun cleaner(s: JIRInst, actions: List<Action>, info: UserDefinedRuleInfo? = null, condition: Condition = trueCondition) =
        TaintCleaner(v.expr(s).method.method, condition, actions, info)
    private fun source(s: JIRInst, actions: List<AssignMark>, condition: Condition = trueCondition, info: UserDefinedRuleInfo? = null) =
        TaintMethodSource(v.expr(s).method.method, condition, actions, info)
    private fun sink(s: JIRInst, actions: List<AssignMark>, condition: Condition) =
        TaintMethodSink(v.expr(s).method.method, condition, actions, "call-sink",
            TaintSinkMeta("call sink", CommonTaintConfigurationSinkMeta.Severity.Error, null), null)

    @Test fun `resolved summary results bind argument suffixes to the lhs with marks layers and exclusions`(): Unit {
        val s = v.call("staticCall")
        val a = inputBase(s)
        val r = resultBase(s)
        val plan = v.builder(s, resolutions = listOf(v.resolved())).build()
        for (input in listOf(fact(a, p(f)), fact(a, p(f), Tail.ANY, mu), must(a, p(f), ex(g)),
            flow(a, p(f), ex(g), mx(u.id)))) {
            val expected = copyTo(input, r)
            val result = check(plan, s, listOf(item(input)), setOf(expected),
                methodResult = { _, i -> if (i.c.fact.base == arg0) listOf(i.copy(c = copyTo(i.c, ret), identityCandidate = false)) else emptyList() })
            assertEquals(setOf(copyTo(input, arg0)), result.at(BOUND))
            assertEquals(setOf(copyTo(input, ret)), result.at(RETURNED))
            assertTrue(result.final.all { it.premise === result.inputs.single().premise })
        }
    }

    @Test fun `multiple resolved callees and a failed resolution contribute a union rather than overwrite`(): Unit {
        val s = v.call("staticCall")
        val a = inputBase(s)
        val r = resultBase(s)
        val plan = v.builder(s, resolutions = listOf(v.resolved("target"), v.resolved("instance"),
            MethodResolutionResult.MethodResolutionFailed)).build()
        val input = fact(a, p(next))
        val result = check(plan, s, listOf(item(input)), setOf(input, fact(r, p(f)), fact(r, p(g), mark = mu)),
            methodResult = { key, i -> when {
                i.c.fact.base != arg0 -> emptyList()
                key.method == v.method("target") -> listOf(i.copy(c = fact(ret, p(f)), identityCandidate = false))
                else -> listOf(i.copy(c = fact(ret, p(g), mark = mu), identityCandidate = false))
            } })
        assertEquals(setOf(fact(arg0, p(next)), fact(ret, p(f)), fact(ret, p(g), mark = mu)), result.at(RETURNED))
    }

    @Test fun `repeated actual arguments and receiver argument bindings remain independent`(): Unit {
        for (name in listOf("repeated", "receiverArgument")) {
            val s = v.call(name)
            val actual = inputBase(s)
            val r = resultBase(s)
            val plan = v.builder(s, resolutions = listOf(v.resolved(if (name == "repeated") "target" else "instance"))).build()
            val result = check(plan, s, listOf(item(fact(actual, p(f)))), setOf(fact(r, p(f)), fact(r, p(g), mark = mu)),
                methodResult = { _, i -> when (i.c.fact.base) {
                    arg0 -> listOf(i.copy(c = fact(ret, p(f)), identityCandidate = false))
                    arg1 -> listOf(i.copy(c = fact(ret, p(g), mark = mu), identityCandidate = false))
                    else -> emptyList()
                } })
            val expected = buildSet { add(fact(arg0, p(f))); add(fact(arg1, p(f)))
                if (name == "receiverArgument") add(fact(AccessPathBase.This, p(f))) }
            assertEquals(expected, result.at(BOUND))
        }
    }

    @Test fun `old lhs is killed untouched base passes over and ignored result has no caller binding`(): Unit {
        val s = v.call("staticCall")
        val plan = v.builder(s, resolutions = listOf(v.resolved())).build()
        val untouched = fact(other, p(g))
        val result = check(plan, s, listOf(item(fact(resultBase(s), p(f))), item(untouched)), setOf(untouched))
        assertTrue(result.at(BOUND).isEmpty())
        val lowered = v.call("ignored")
        val ignored = JIRCallInst(lowered.location, v.expr(lowered))
        check(v.builder(ignored, resolutions = listOf(v.resolved())).build(), ignored,
            listOf(item(fact(inputBase(ignored)))), emptySet(),
            methodResult = { _, i -> listOf(i.copy(c = fact(ret), identityCandidate = false)) })
    }

    @Test fun `constant argument enters callee but never receives a caller fact on binding back`(): Unit {
        val s = v.call("constant")
        val constant = inputBase(s, 1)
        assertIs<AccessPathBase.Constant>(constant)
        val plan = v.builder(s, resolutions = listOf(v.resolved())).build()
        val input = fact(inputBase(s))
        check(plan, s, listOf(item(input)), setOf(fact(resultBase(s))),
            methodResult = { _, i -> if (i.c.fact.base == arg0) listOf(
                i.copy(c = fact(arg1), identityCandidate = false), i.copy(c = fact(ret), identityCandidate = false)) else emptyList() })
    }

    @Test fun `zero passes over enters resolved callee and source results return without a zero binding`(): Unit {
        val s = v.call("staticCall")
        val rules = CallRules().apply { sources = listOf(source(s, listOf(AssignMark(RuleMark("T"), Result)))) }
        val plan = v.builder(s, rules, listOf(v.resolved())).build()
        val zero = Conclusion(ZERO_FACT, ExclusionSet.Empty, false)
        val result = check(plan, s, listOf(item(zero)), setOf(zero, fact(resultBase(s))), sourceHits = 1,
            methodResult = { _, i -> assertEquals(zero, i.c); emptyList() })
        assertEquals(setOf(zero), result.at(ADDED))
        assertEquals(setOf(zero, fact(ret)), result.at(REWRITTEN))
        assertEquals(1, result.final.count { it.c == zero })
        assertEquals(setOf(ZERO_PATTERN), result.final.single { it.c.fact.base != AccessPathBase.Zero }.premise)
    }

    @Test fun `static FLOW enters and returns through summaries with its original premise and E`(): Unit {
        val s = v.call("staticCall")
        val plan = v.builder(s, resolutions = listOf(v.resolved())).build()
        val input = flow(stat, exclusion = ex(g), mark = mx(u.id))
        val original = item(input)
        val result = check(plan, s, listOf(original), setOf(input), run1 = true,
            methodResult = { _, i -> listOf(i.copy(identityCandidate = true)) })
        assertEquals(setOf(input), result.at(BOUND))
        assertTrue(result.final.all { it.premise === original.premise })
    }

    @Test fun `unresolved default identity and pass result are sibling paths`(): Unit {
        val s = v.call("staticExternal")
        val rules = CallRules().apply { passes = listOf(pass(s, listOf(CopyAllMarks(Argument(0), Result)))) }
        val plan = v.builder(s, rules).build()
        val input = must(inputBase(s), p(f), ex(g))
        val result = check(plan, s, listOf(item(input)), setOf(input, copyTo(input, resultBase(s))))
        assertEquals(setOf(copyTo(input, arg0), copyTo(input, ret)), result.at(RETURNED))
        assertEquals(setOf(true, false), result.items(RETURNED).map { it.identityCandidate }.toSet())
    }

    @Test fun `unresolved identity has no declared pass filter on the added stage`(): Unit {
        val s = v.call("getter")
        val rules = CallRules().apply { passes = listOf(pass(s, listOf(CopyAllMarks(This, Result)))) }
        val plan = v.builder(s, rules).build()
        // A direct ADDED-stage input isolates this contract; the receiver's BIND_IN filter would reject it.
        val input = fact(AccessPathBase.This, p(element))
        val result = check(plan, s, listOf(item(input)), emptySet(), from = ADDED)
        assertEquals(setOf(input), result.at(RETURNED))
        assertTrue(result.items(RETURNED).all { it.identityCandidate == true })
        val receiver = base(assertIs<JIRInstanceCallExpr>(v.expr(s)).instance)
        check(plan, s, listOf(item(fact(AccessPathBase.This))), setOf(fact(receiver), fact(resultBase(s))), from = ADDED)
    }

    @Test fun `run one unresolved static field pass requests position on original static FLOW premise`(): Unit {
        val s = v.call("staticExternal")
        val cls = m.accessors.index(ClassStaticAccessor(CLS))
        val staticField = field("sField")
        val rules = CallRules().apply { passes = listOf(pass(s, listOf(CopyAllMarks(at(ClassStatic(CLS), "sField"), Result)))) }
        val plan = v.builder(s, rules).build()
        val original = setOf(Pattern(PathFact(stat, emptyList(), Tail.STAR, MarkSlot.STAR), ExclusionSet.Empty))
        val input = flow(stat, exclusion = ex(g))
        val result = check(plan, s, listOf(item(input, premise = original)), setOf(input), run1 = true, fieldLimit = 1,
            positions = listOf(checkNotNull(m.path(p(cls, staticField)))))
        assertTrue(result.requests.single().first === original)
        val blocked = flow(stat, exclusion = ex(cls))
        check(plan, s, listOf(item(blocked, premise = original)), setOf(blocked), run1 = true, fieldLimit = 1)
    }

    @Test fun `sink sees uncleaned bound T and its end fact survives the cleaner chain`(): Unit {
        val local = localCall()
        val s = local.inst
        val rules = CallRules().apply {
            sinks = listOf(sink(s, listOf(AssignMark(RuleMark("U"), Result)), contains(Argument(0), "T")))
            cleaners = listOf(cleaner(s, listOf(RemoveMark(RuleMark("T"), Argument(0)))))
            passes = listOf(pass(s, listOf(CopyAllMarks(Argument(0), Result))))
        }
        val plan = v.builder(s, rules, persistedAliases = { b, _ ->
            if (b == local.result) listOf(alias(other, "next", "next")) else emptyList()
        }).build()
        val result = check(plan, s, listOf(item(fact(inputBase(s)))), setOf(fact(resultBase(s), mark = mu),
            fact(other, p(next, next), mark = mu)))
        assertEquals(setOf(fact(arg0)), result.bound)
        assertEquals(setOf(fact(arg0)), result.cleanInputs.toSet())
        assertTrue(result.at(ADDED).isEmpty())
        assertTrue(result.final.all { it.premise == setOf(ZERO_PATTERN) })
        assertEquals(listOf("bound", "clean"), result.events.filter { it == "bound" || it == "clean" })
    }

    @Test fun `unresolved pass sees F75 cleaned FLOW and request retains incoming caller premise`(): Unit {
        val s = v.call("staticExternal")
        val rules = CallRules().apply {
            cleaners = listOf(cleaner(s, listOf(RemoveMark(RuleMark("T"), Argument(0)))))
            passes = listOf(pass(s, listOf(CopyAllMarks(Argument(0), Result))))
        }
        val plan = v.builder(s, rules).build()
        val input = flow(inputBase(s), exclusion = ex(g), mark = mx(u.id))
        val original = item(input)
        val cleaned = input.copy(fact = input.fact.copy(mark = mx(t.id, u.id)))
        val result = check(plan, s, listOf(original), setOf(cleaned, copyTo(cleaned, resultBase(s))), run1 = true,
            marks = listOf(t))
        assertEquals(setOf(copyTo(cleaned, arg0)), result.at(ADDED))
        assertTrue(result.requests.all { it.first === original.premise })
    }

    @Test fun `source branch bypasses returned rewriter while ordinary callee results are rewritten`(): Unit {
        val local = localCall()
        val s = local.inst
        val rules = CallRules().apply {
            sources = listOf(source(s, listOf(AssignMark(RuleMark("T"), Result)), info = user("T")))
        }
        val plan = v.builder(s, rules, listOf(v.resolved()), persistedAliases = { b, _ ->
            if (b == local.result) listOf(alias(other, "next", "next")) else emptyList()
        }).build()
        val zero = Conclusion(ZERO_FACT, ExclusionSet.Empty, false)
        val input = fact(inputBase(s), mark = mu)
        val result = check(plan, s, listOf(item(zero), item(input)), setOf(zero, fact(resultBase(s)),
            fact(other, p(next, next))), sourceHits = 1,
            methodResult = { _, i -> if (i.c.fact.base == arg0) listOf(i.copy(c = fact(ret), identityCandidate = false)) else emptyList() })
        assertEquals(setOf(fact(ret)), result.at(RETURNED))
        assertTrue(result.final.filter { it.c.fact.base != AccessPathBase.Zero }.all { it.premise == setOf(ZERO_PATTERN) })
    }

    @Test fun `conditional user source rewrites default identity and pass results unless statically false`(): Unit {
        val s = v.call("staticExternal")
        for (condition in listOf<Condition>(contains(Argument(1), "U"), falseCondition)) {
            val rules = CallRules().apply {
                sources = listOf(source(s, listOf(AssignMark(RuleMark("T"), Result)), condition, user("T")))
                passes = listOf(pass(s, listOf(CopyAllMarks(Argument(0), Result))))
            }
            val plan = v.builder(s, rules).build()
            val input = fact(inputBase(s))
            val expected = if (condition == falseCondition) setOf(input, fact(resultBase(s))) else setOf(input)
            check(plan, s, listOf(item(input)), expected)
        }
    }

    @Test fun `constructor cleaned pass over skips rewriter while resolved result takes it`(): Unit {
        val s = v.call("constructor")
        val rules = CallRules().apply {
            allSources = listOf(source(s, listOf(AssignMark(RuleMark("T"), Argument(0))),
                contains(Argument(0), "U"), user("T")))
        }
        val plan = v.builder(s, rules, listOf(v.resolved("<init>"))).build()
        val input = fact(inputBase(s))
        val result = check(plan, s, listOf(item(input)), setOf(input),
            methodResult = { _, i -> listOf(i.copy(identityCandidate = true)) })
        assertEquals(setOf(copyTo(input, arg0)), result.at(RETURNED))
        assertEquals(setOf(copyTo(input, arg0)), result.at(REWRITTEN))
        assertTrue(result.final.all { it.identityCandidate == true })
        val backward = check(plan.reversed(), s, listOf(item(input)), setOf(input), direction = Direction.BACKWARD)
        assertTrue(backward.at(RETURNED).isEmpty())
        assertEquals(setOf(copyTo(input, arg0)), backward.at(BOUND))
    }

    @Test fun `alias guard omits normal identity accepts demand identity and preserves old alias facts`(): Unit {
        val local = localCall()
        val plan = v.builder(local.inst, resolutions = listOf(v.resolved()), persistedAliases = { b, _ ->
            if (b == local.argument) listOf(alias(other, "next", "next")) else emptyList()
        }).build()
        val old = fact(other, p(g), mark = mu)
        for (demand in listOf(false, true)) {
            val input = fact(local.argument, p(f), demand = demand)
            val expected = buildSet { add(input); add(old)
                if (demand) add(fact(other, p(next, next, f), demand = true)) }
            check(plan, local.inst, listOf(item(input), item(old)), expected,
                methodResult = { _, i -> listOf(i.copy(identityCandidate = true)) })
        }
    }

    @Test fun `pass and summary memory effects retain full alias path until final positive call cuts`(): Unit {
        val local = localCall()
        val rules = CallRules().apply { passes = listOf(pass(local.inst, listOf(CopyAllMarks(Argument(0), Result)))) }
        val plan = v.builder(local.inst, rules, persistedAliases = { b, _ ->
            if (b == local.result) listOf(alias(other, "next", "next")) else emptyList()
        }).build()
        val input = fact(local.argument, p(f))
        val old = fact(other, p(g), mark = mu)
        for ((limit, aliasFact) in listOf(1 to fact(other, p(next), Tail.ANY),
            2 to fact(other, p(next, next), Tail.ANY), 3 to fact(other, p(next, next, f)))) {
            val result = check(plan, local.inst, listOf(item(input), item(old)), setOf(input,
                fact(local.result, p(f)), aliasFact, old), fieldLimit = limit)
            assertEquals(p(next, next, f), result.exitInputs.single { it.fact.base == other && it.fact.mark == mt }.fact.path)
        }
    }

    @Test fun `native pending resume keeps must E for mappings admits aliases then demotes before call cut`(): Unit {
        val local = localCall()
        val plan = v.builder(local.inst, resolutions = listOf(v.resolved()), persistedAliases = { b, _ ->
            if (b == local.result) listOf(alias(other, "next", "next")) else emptyList()
        }).build()
        val origin = item(fact(local.argument)).premise
        val returned = item(must(ret, p(f), ex(g)), candidate = true, premise = origin)
        for ((limit, aliasFact) in listOf(1 to fact(other, p(next), Tail.ANY),
            2 to fact(other, p(next, next), Tail.ANY), 3 to fact(other, p(next, next, f), Tail.ANY))) {
            val result = check(plan, local.inst, listOf(returned), setOf(fact(local.result, p(f), Tail.ANY), aliasFact),
                from = RETURNED, pending = true, fieldLimit = limit)
            assertTrue(result.items(REWRITTEN).all { it.c.fact.tail == Tail.ANY_TAINT && it.c.exclusion == ex(g) && !it.c.demand })
            assertTrue(result.items(REWRITTEN).all { it.identityCandidate == false && it.premise === origin })
            assertTrue(result.exitInputs.all { it.demand && it.exclusion == ExclusionSet.Empty && it.fact.tail == Tail.ANY })
        }
        check(plan, local.inst, listOf(returned), setOf(must(local.result, p(f), ex(g))), from = RETURNED)
        val demand = item(fact(ret, p(f), Tail.ANY), candidate = true, premise = origin)
        check(plan, local.inst, listOf(demand), setOf(fact(local.result, p(f), Tail.ANY),
            fact(other, p(next, next, f), Tail.ANY)), from = RETURNED)
    }

    @Test fun `returned lowered field action observes pending must E and projects temporary before final demotion`(): Unit {
        val s = v.call("staticCall")
        val rules = CallRules().apply {
            allCleaners = listOf(cleaner(s, listOf(RemoveMark(RuleMark("T"), at(Result, "f"))), user("T")))
        }
        val plan = v.builder(s, rules, listOf(v.resolved())).build()
        val origin = item(fact(inputBase(s))).premise
        val input = item(must(ret, exclusion = ex(g)), candidate = true, premise = origin)
        for (limit in listOf(1, 2, 3)) {
            val result = check(plan, s, listOf(input), setOf(fact(resultBase(s), tail = Tail.ANY),
                fact(resultBase(s), p(f), Tail.ANY)), from = RETURNED, pending = true, fieldLimit = limit)
            assertTrue(result.cleanInputs.any { it.fact.base is AccessPathBase.CleanerTemporary &&
                it.fact.tail == Tail.ANY_TAINT && !it.demand })
            assertTrue(result.statementCuts.any { it.fact.base is AccessPathBase.CleanerTemporary })
            assertTrue(result.at(REWRITTEN).none { it.fact.base is AccessPathBase.CleanerTemporary })
            assertTrue(result.at(REWRITTEN).contains(must(ret, exclusion = ex(f, g))))
        }
        val blocked = item(must(ret, exclusion = ex(f)), candidate = true, premise = origin)
        val result = check(plan, s, listOf(blocked), setOf(fact(resultBase(s), tail = Tail.ANY)), from = RETURNED, pending = true)
        assertTrue(result.cleanInputs.none { it.fact.base is AccessPathBase.CleanerTemporary })
        val killRules = CallRules().apply { allCleaners = listOf(cleaner(s,
            listOf(RemoveMark(RuleMark("T"), Result, TaintCleanReach.ExactAndAnyField)), user("T"))) }
        // The rewriter uses EXACT for a root action; a concrete exact result is surely killed.
        val killPlan = v.builder(s, killRules, listOf(v.resolved())).build()
        check(killPlan, s, listOf(item(fact(ret), true, origin)), emptySet(), from = RETURNED, pending = true)
    }

    @Test fun `binding back applies final byte array filter before positive call cuts`(): Unit {
        val s = v.call("bytesCall")
        val plan = v.builder(s).build()
        val tracked = MarkSlot.Concrete(m.marks.mark("T%%primitive%%"))
        val origin = item(fact(inputBase(s), mark = tracked)).premise
        for (limit in listOf(1, 2, 3)) {
            // Cutting [e,f] first could erase f and make an invalid primitive suffix pass the byte[] filter.
            val bad = fact(ret, p(element, f), mark = tracked)
            val result = check(plan, s, listOf(item(bad, false, origin)), emptySet(), from = RETURNED, fieldLimit = limit)
            assertEquals(setOf(bad), result.at(REWRITTEN))
            assertTrue(result.exitInputs.isEmpty())
            val good = fact(ret, p(element), mark = tracked)
            check(plan, s, listOf(item(good, false, origin)), setOf(fact(resultBase(s), p(element), mark = tracked)),
                from = RETURNED, fieldLimit = limit)
        }
    }

    @Test fun `reverse may pass returns demand requirements through bindings without requests`(): Unit {
        val s = v.call("staticExternal")
        val rules = CallRules().apply { passes = listOf(pass(s, listOf(CopyAllMarks(Argument(0), any(Result))))) }
        val forward = v.builder(s, rules).build()
        val backward = forward.reversed()
        val input = fact(resultBase(s), p(f))
        val result = check(backward, s, listOf(item(input)), setOf(fact(inputBase(s), tail = Tail.ANY)),
            direction = Direction.BACKWARD)
        assertEquals(setOf(fact(arg0, tail = Tail.ANY)), result.at(ADDED))
    }

    @Test fun `reverse alias path and explicit pass over produce separate requirements`(): Unit {
        val local = localCall()
        val plan = v.builder(local.inst, persistedAliases = { b, _ ->
            if (b == local.result) listOf(alias(other, "next", "next")) else emptyList()
        }).build().reversed()
        val input = fact(other, p(next, next, f), demand = true)
        val result = check(plan, local.inst, listOf(item(input)), setOf(input))
        assertEquals(setOf(fact(ret, p(f), demand = true)), result.at(REWRITTEN))
        assertTrue(other in plan.touched)
    }

    @Test fun `reversed end fact retains sink trigger and adds its independent bound seed`(): Unit {
        val s = v.call("staticExternal")
        val rules = CallRules().apply {
            sinks = listOf(sink(s, listOf(AssignMark(RuleMark("U"), Result)), contains(Argument(0), "T")))
        }
        val forward = v.builder(s, rules).build()
        val backward = forward.reversed()
        assertEquals(2, forward.sinks.size, "An Object argument has root and element sink alternatives (SI1)")
        val input = fact(resultBase(s), mark = mu, demand = true)
        val result = check(backward, s, listOf(item(input)), setOf(fact(inputBase(s), demand = false),
            fact(inputBase(s), p(element)), Conclusion(ZERO_FACT, ExclusionSet.Empty, true)),
            direction = Direction.BACKWARD, triggerSeeds = { sink ->
                sink.patterns.map { pattern -> item(Conclusion(pattern.fact, pattern.exclusion, pattern.fact.tail == Tail.ANY),
                    premise = setOf(ZERO_PATTERN)) }
            }, triggers = forward.sinks.toSet())
        assertEquals(setOf(Conclusion(ZERO_FACT, ExclusionSet.Empty, true), fact(arg0), fact(arg0, p(element))), result.at(BOUND))
    }

    private data class LocalCall(val inst: JIRAssignInst, val argument: AccessPathBase.LocalVar,
                                 val result: AccessPathBase.LocalVar)
    private fun localCall(): LocalCall {
        val actual = assertIs<JIRAssignInst>(v.call("staticCall"))
        val call = assertIs<JIRStaticCallExpr>(v.expr(actual))
        val argument = JIRLocalVar(900, "callArgument", call.args.first().type)
        val result = JIRLocalVar(901, "callResult", actual.lhv.type)
        val s = JIRAssignInst(actual.location, result, call.copy(args = listOf(argument) + call.args.drop(1)))
        return LocalCall(s, assertIs<AccessPathBase.LocalVar>(base(argument)), assertIs<AccessPathBase.LocalVar>(base(result)))
    }
    private fun alias(base: AccessPathBase, vararg names: String) = AliasApInfo(base,
        names.map { AliasAccessor.Field(CLS, it, if (it == "next") CLS else "java.lang.Object") })

    private data class WalkResult(
        val inputs: List<PlanItem>, val reached: List<Pair<CallPoint, PlanItem>>,
        val requests: List<Pair<Set<Pattern>, RequestKind>>, val cleanInputs: List<Conclusion>,
        val statementCuts: List<Conclusion>, val exitInputs: List<Conclusion>, val bound: Set<Conclusion>,
        val events: List<String>, val exit: CallPoint,
    ) {
        val final get() = items(exit)
        fun items(point: CallPoint) = reached.filter { it.first == point }.map { it.second }
        fun at(point: CallPoint) = items(point).map { it.c }.toSet()
    }

    private fun check(
        plan: CallPlan, statement: JIRInst, inputs: List<PlanItem>, expected: Set<Conclusion>,
        methodResult: (MethodKey, PlanItem) -> List<PlanItem> = { _, _ -> emptyList() },
        from: CallPoint = plan.entry, pending: Boolean = false, fieldLimit: Int = Int.MAX_VALUE,
        run1: Boolean = false, direction: Direction = if (plan.entry == AFTER) Direction.BACKWARD else Direction.FORWARD,
        marks: List<TaintMark> = emptyList(), positions: List<PathNode> = emptyList(), sourceHits: Int = 0,
        triggerSeeds: (SinkRule) -> List<PlanItem> = { emptyList() }, triggers: Set<SinkRule> = emptySet(),
    ): WalkResult {
        val mode = ApMode(run1, direction, fieldLimit)
        for (input in inputs) {
            val key = m.premiseOf(input.premise.map(m::initial))
            if (input.c.fact.base != AccessPathBase.Zero)
                require(key.isFlow == (input.c.fact.mark is MarkSlot.Star)) { "K6 caller edge kind" }
        }
        val requests = arrayListOf<Pair<Set<Pattern>, RequestKind>>()
        val cleanInputs = arrayListOf<Conclusion>()
        val statementCuts = arrayListOf<Conclusion>()
        val exitInputs = arrayListOf<Conclusion>()
        val bound = linkedSetOf<Conclusion>()
        val firedTriggers = linkedSetOf<SinkRule>()
        val events = arrayListOf<String>()
        var hits = 0
        val reference = FormsReference(ops, ReferenceAlgebra(mode, manager = m,
            request = { pr, kind -> requests += pr to kind }, allowsSource = { _, _ -> true },
            sourceHit = { _, _ -> hits++ }))
        val place = Place(statement, statementEdge = false, sources = false)
        val raw = reference.run(plan, inputs, place, PlanHooks(guards = true,
            atBound = { items ->
                events += "bound"; bound += items.map { it.c }
                plan.sinks.flatMap { sink -> Layer.entries.filter { layer -> sink.patterns.all { pattern ->
                    items.any { (if (it.c.demand) Layer.DEMAND else Layer.NORMAL) == layer &&
                        satisfies(pattern, Pattern(it.c.fact, it.c.exclusion), mode.restricted) }
                } }.map { sink to it } }
            }, callees = { stage, i -> stage.callees.flatMap { methodResult(it, i) } },
            clean = { cl, i ->
                events += "clean"; cleanInputs += i.c
                val cleaned = cleanRes(cl, i.c)
                if (run1) cleaned.request?.let { requests += i.premise to RequestKind.Mark(it) }
                cleaned.facts.map { i.copy(c = it) }
            }, statementCut = { i -> statementCuts += i.c; i.copy(c = limit(i.c, fieldLimit)) },
            exit = { i -> exitInputs += i.c; i.copy(c = limit(i.c, fieldLimit)) },
            trigger = { sink -> firedTriggers += sink; triggerSeeds(sink) }), from, pending)
        val result = WalkResult(inputs, raw, requests, cleanInputs, statementCuts, exitInputs, bound, events, plan.exit)
        assertEquals(expected, result.at(plan.exit), "raw from=$from pending=$pending L=$fieldLimit")
        assertEquals(marks, requests.mapNotNull { (it.second as? RequestKind.Mark)?.mark })
        assertEquals(positions, requests.mapNotNull { (it.second as? RequestKind.Position)?.path })
        assertEquals(sourceHits, hits)
        assertEquals(triggers, firedTriggers)
        assertTrue(raw.none { (_, i) -> i.c.fact.base is AccessPathBase.CleanerTemporary })
        compareTree(plan, place, inputs, raw, methodResult, from, pending, mode, marks, positions, sourceHits, triggerSeeds, triggers)
        return result
    }

    private data class TreeItem(val premise: Set<Pattern>, val facts: Facts, val candidate: Boolean?)

    /** A small execution adapter uses tree AP operations; expected facts are declared in each test. */
    private fun compareTree(
        plan: CallPlan, place: Place, inputs: List<PlanItem>, expectedWalk: List<Pair<CallPoint, PlanItem>>,
        methodResult: (MethodKey, PlanItem) -> List<PlanItem>, from: CallPoint, pending: Boolean,
        mode: ApMode, marks: List<TaintMark>, positions: List<PathNode>, sourceHits: Int,
        triggerSeeds: (SinkRule) -> List<PlanItem>, triggers: Set<SinkRule>,
    ) {
        val decoder = FormsReference(ops)
        fun tree(i: PlanItem) = TreeItem(i.premise, m.factsOf(i.c.fact, i.c.exclusion,
            if (i.c.demand) Layer.DEMAND else Layer.NORMAL), i.identityCandidate)
        fun raw(i: TreeItem) = decoder.conclusions(i.facts).map { PlanItem(i.premise, it, i.candidate) }
        fun key(pr: Set<Pattern>) = m.premiseOf(pr.map(m::initial))
        val requests = arrayListOf<TaintMark>()
        val positionRequests = arrayListOf<PathNode>()
        val firedTriggers = linkedSetOf<SinkRule>()
        var hits = 0
        fun apOut(sink: (Facts) -> Unit) = object : ApOut {
            override fun result(f: Facts) = sink(f)
            override fun markRequest(mark: TaintMark) { requests += mark }
            override fun positionRequest(position: PathNode) { positionRequests += position }
        }
        val applier = FormApplier(object : FactAlgebra<Set<Pattern>, Facts> {
            override fun base(c: Facts) = c.base
            override fun filter(c: Facts, filter: TypeFilter) = ops.filter(c, filter)
            override fun applyEdge(me: MicroEdge, c: Facts, premise: Set<Pattern>, statementEdge: Boolean,
                                   out: (Set<Pattern>, Facts) -> Unit) {
                ops.applyEdge(c, key(premise), me.edge, statementEdge, mode,
                    apOut { out(premise, it) }, me.may)
            }
            override fun conjunction(cj: ConjunctiveEdge, premise: Set<Pattern>, c: Facts, node: CommonInst,
                                     out: (Set<Pattern>, Facts) -> Unit) = error("These call vectors have no conjunction")
            override fun allowsSource(node: CommonInst, me: MicroEdge) = true
            override fun sourceHit(node: CommonInst, me: MicroEdge) { hits++ }
        })
        fun cut(i: TreeItem): List<TreeItem> = buildList { ops.limit(i.facts, mode.fieldLimit, apOut { add(i.copy(facts = it)) }) }
        fun finish(i: TreeItem): List<TreeItem> {
            val normalized = if (pending) raw(i).map { item ->
                require(item.c.fact.mark is MarkSlot.Concrete && item.c.fact.tail != Tail.STAR)
                val fact = if (item.c.fact.tail == Tail.ANY_TAINT) item.c.fact.copy(tail = Tail.ANY) else item.c.fact
                tree(item.copy(c = Conclusion(fact, ExclusionSet.Empty, demand = true), identityCandidate = false))
            } else listOf(i)
            return normalized.flatMap(::cut)
        }
        fun statement(s: StatementSummary, i: TreeItem, useCut: Boolean): List<TreeItem> = buildList {
            applier.statement(s, i.premise, i.facts, place.copy(statementEdge = true, sources = false),
                sink = { pr, f -> val n = TreeItem(pr, f, i.candidate); addAll(if (useCut) cut(n) else listOf(n)) },
                untouched = { pr, f -> add(TreeItem(pr, f, i.candidate)) })
        }
        fun clean(cl: Cleaner, i: TreeItem): List<TreeItem> = buildList {
            ops.clean(i.facts, key(i.premise), cl, mode, apOut { add(i.copy(facts = it)) })
        }
        fun actions(steps: List<CleanStep>, items: List<TreeItem>): List<TreeItem> = steps.fold(items) { batch, step ->
            batch.flatMap { i -> when (step) {
                is CleanStep.Clean -> clean(step.cleaner, i)
                is CleanStep.Kill -> statement(step.keepEdges, i, false)
                is CleanStep.Field -> statement(step.read, i, true).flatMap { clean(step.cleaner, it) }
                    .flatMap { statement(step.write, it, true) }.filter { it.facts.base != step.temporary }
            } }
        }
        val seen = linkedSetOf<Pair<CallPoint, TreeItem>>()
        val work = ArrayDeque<Pair<CallPoint, List<TreeItem>>>()
        val start = arrayListOf<TreeItem>()
        for (input in inputs.map(::tree)) {
            val i = if (pending) input.copy(candidate = false) else input
            when {
                from != plan.entry -> start += i
                i.facts.base == AccessPathBase.Zero -> { finish(i).forEach { seen += plan.exit to it }; start += i }
                i.facts.base !in plan.touched -> finish(i).forEach { seen += plan.exit to it }
                else -> start += i
            }
        }
        if (start.isNotEmpty()) work += from to start
        while (work.isNotEmpty()) {
            val (point, batch) = work.removeFirst()
            val items = batch.filter { seen.add(point to it) }
            if (items.isEmpty() || point == plan.exit) continue
            val sinks = if (point == BOUND) plan.sinks.flatMap { sink -> Layer.entries.filter { layer ->
                sink.patterns.all { pattern -> items.any { i -> i.facts.layer == layer &&
                    ops.satisfying(i.facts, pattern, mode) != null } }
            }.map { sink to it } } else emptyList()
            for (stage in plan.stagesFrom[point].orEmpty()) {
                val result = arrayListOf<TreeItem>()
                when (stage) {
                    is CallStage.Actions -> result += actions(stage.steps, items)
                    is CallStage.Callees -> for (i in items) for (r in raw(i))
                        result += stage.callees.flatMap { methodResult(it, r) }.map(::tree)
                    is CallStage.Edges -> {
                        val at = place.copy(statementEdge = stage.kind.statementEdges, sources = stage.kind == StageKind.SOURCES)
                        val guard = stage.guard
                        if (guard is Guard.SinkTriggered) {
                            for ((sink, layer) in sinks) if (sink === guard.sink)
                                applier.stage(stage.summary, setOf(ZERO_PATTERN), ops.zero(layer), at) { pr, f, me ->
                                    result += TreeItem(pr, f, stage.kind.identityCandidateOf(me, null)) }
                        } else {
                            for (i in items) {
                                if (guard == Guard.MemoryEffect && !Guard.MemoryEffect.admits(checkNotNull(i.candidate), i.facts.layer)) continue
                                applier.stage(stage.summary, i.premise, i.facts, at) { pr, f, me ->
                                    result += TreeItem(pr, f, stage.kind.identityCandidateOf(me, i.candidate)) }
                            }
                            stage.trigger?.let { sink -> if (result.isNotEmpty()) {
                                firedTriggers += sink; result += triggerSeeds(sink).map(::tree)
                            } }
                        }
                    }
                }
                val next = if (stage.to == plan.exit) result.flatMap(::finish) else result
                if (next.isNotEmpty()) work += stage.to to next
            }
        }
        val got = seen.flatMap { (point, i) -> raw(i).map { point to it } }.toSet()
        assertEquals(expectedWalk.toSet(), got, "tree/reference complete call graph")
        assertEquals(marks, requests)
        assertEquals(positions, positionRequests)
        assertEquals(sourceHits, hits)
        assertEquals(triggers, firedTriggers)
    }

    private companion object { const val CLS = JIRCallTestFixtures.SAMPLE_CLASS }
}
