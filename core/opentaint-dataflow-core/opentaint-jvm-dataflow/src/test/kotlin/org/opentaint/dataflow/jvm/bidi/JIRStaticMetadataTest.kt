package org.opentaint.dataflow.jvm.bidi

import org.opentaint.dataflow.ap.ifds.ClassStaticAccessor
import org.opentaint.dataflow.ap.ifds.FieldAccessor
import org.opentaint.dataflow.ap.ifds.access.FactAp
import org.opentaint.dataflow.bidi.ap.ApManager
import org.opentaint.dataflow.bidi.ap.Tail
import org.opentaint.dataflow.bidi.engine.StaticMarkRole
import org.opentaint.dataflow.configuration.CommonCondition
import org.opentaint.dataflow.configuration.mkTrue
import org.opentaint.dataflow.configuration.jvm.*
import org.opentaint.dataflow.jvm.bidi.fixtures.JIRRuleTestFixtures
import org.opentaint.dataflow.jvm.bidi.fixtures.NoTaintRules
import org.opentaint.dataflow.jvm.bidi.fixtures.JIRPrescanFixtures
import org.opentaint.dataflow.jvm.bidi.interp.JIRStaticMetadataCollector
import org.opentaint.dataflow.jvm.bidi.interp.MetadataAliases
import org.opentaint.dataflow.jvm.ap.ifds.taint.ContainsMarkOnAnyField
import org.opentaint.dataflow.util.Cancellation
import org.opentaint.ir.api.common.CommonMethod
import org.opentaint.ir.api.common.cfg.CommonInst
import org.opentaint.ir.api.jvm.ext.cfg.callExpr
import kotlin.test.*

class JIRStaticMetadataTest : BidiJIRTestUtils() {
    private val owner = "sample.bidi.BidiStatementSample"
    private val cls = ClassStaticAccessor(owner)
    private val fld = FieldAccessor(owner, "sField", "java.lang.Object")
    private val slot = listOf(cls, fld)

    @Test
    fun `static read alone requests abstract field while strong write contributes only legal domain and effect`(): Unit {
        val collector = JIRStaticMetadataCollector(interp)
        val read = collector.collect(key(method("staticRead")))
        assertEquals(setOf(slot), read.accesses)
        assertEquals(setOf(slot), read.abstractions)
        assertTrue(read.marks.isEmpty())
        val write = collector.collect(key(method("staticWrite")))
        assertEquals(setOf(slot), write.accesses)
        assertTrue(write.abstractions.isEmpty())
        assertFalse(read.staticMayWrite)
        assertTrue(write.staticMayWrite)
        assertTrue(write.cleanerMarks.isEmpty())
    }

    @Test
    fun `live source and sink NNF static leaves preserve full paths roles and marks without false footprints`(): Unit {
        val v = JIRRuleTestFixtures(cp)
        val target = checkNotNull(method("callOnly").instList.instructions.first { it.callExpr != null }.callExpr).method.method
        val p = PositionWithAccess(ClassStatic(owner), PositionAccessor.FieldAccessor(owner, "sField", "java.lang.Object"))
        val deep = PositionWithAccess(p, PositionAccessor.FieldAccessor(owner, "f", "java.lang.Object"))
        val atom: Condition = CommonCondition.Atom(ContainsMark(deep, TaintMark("T")))
        val falseCond: Condition = CommonCondition.And(listOf(atom, CommonCondition.Not(atom)))
        val source = v.source(listOf(AssignMark(TaintMark("U"), Result)), atom, target)
        val sink = v.sink(condition = CommonCondition.Or(listOf(atom,
            CommonCondition.Atom(ContainsMarkOnAnyField(p, TaintMark("V"))))), method = target)
        val never = v.source(listOf(AssignMark(TaintMark("NEVER"), ClassStatic("never.Owner"))), falseCond, target)
        val rules = object : org.opentaint.dataflow.jvm.ap.ifds.taint.TaintRulesProvider by NoTaintRules {
            override fun sourceRulesForMethod(method: CommonMethod, statement: CommonInst, fact: FactAp?, allRelevant: Boolean) =
                if (method == target) listOf(source, never) else emptyList()
            override fun sinkRulesForMethod(method: CommonMethod, statement: CommonInst, fact: FactAp?, allRelevant: Boolean) =
                if (method == target) listOf(sink) else emptyList()
        }
        val metadata = JIRStaticMetadataCollector(interp(rules)).collect(key(method("callOnly")))
        assertEquals(setOf("T", "V"), metadata.marks.map { it.mark }.toSet())
        assertTrue(metadata.marks.any { it.role == StaticMarkRole.SOURCE && it.path.size == 3 && it.tail == Tail.EXACT })
        assertTrue(metadata.marks.any { it.role == StaticMarkRole.SINK && it.path == slot && it.tail == Tail.ANY })
        assertTrue(metadata.abstractions.isEmpty(), "Source and sink ContainsMark create mark interest, never F")
        assertTrue(metadata.accesses.none { it.firstOrNull() == ClassStaticAccessor("never.Owner") })
    }

    @Test
    fun `selected class cleaner keeps exact point scope and no cleaner target creates a read request`(): Unit {
        val v = JIRRuleTestFixtures(cp)
        val target = method("target", "sample.bidi.BidiPrescanSample")
        val selected = v.cleaner(listOf(RemoveMark(TaintMark("T"), ClassStatic(owner), org.opentaint.dataflow.configuration.TaintCleanReach.Exact)),
            mkTrue(), target)
        val all = v.cleaner(listOf(RemoveAllMarks(ClassStatic(owner))),
            mkTrue(), target)
        val queries = ArrayList<Boolean>()
        val rules = object : org.opentaint.dataflow.jvm.ap.ifds.taint.TaintRulesProvider by NoTaintRules {
            override fun cleanerRulesForMethod(method: CommonMethod, statement: CommonInst, fact: FactAp?, allRelevant: Boolean): Iterable<TaintCleaner> {
                assertNull(fact)
                queries += allRelevant
                return if (method == target) listOf(selected, all) else emptyList()
            }
        }
        val metadata = JIRStaticMetadataCollector(interp(rules)).collect(key(method("ruleCalls", "sample.bidi.BidiPrescanSample")))
        assertEquals(setOf("T"), metadata.cleanerMarks)
        assertTrue(queries.containsAll(listOf(false, true)))
        assertEquals(listOf(Triple(listOf(cls), Tail.EXACT, "T")), metadata.marks.map { Triple(it.path,it.tail,it.mark) })
        assertEquals(setOf(cls), metadata.classPoints)
        assertTrue(metadata.abstractions.isEmpty())
        assertTrue(metadata.staticMayWrite)
    }

    @Test
    fun `exact class condition owns only its point and keeps frontend admitted mark zero`(): Unit {
        val v = JIRRuleTestFixtures(cp)
        val caller = method("callOnly")
        val target = checkNotNull(caller.instList.instructions.first { it.callExpr != null }.callExpr).method.method
        val sink = v.sink(condition = CommonCondition.Atom(ContainsMark(ClassStatic(owner), TaintMark("<zero>"))), method = target)
        val rules = object : org.opentaint.dataflow.jvm.ap.ifds.taint.TaintRulesProvider by NoTaintRules {
            override fun sinkRulesForMethod(method: CommonMethod, statement: CommonInst, fact: FactAp?, allRelevant: Boolean) =
                if (method == target) listOf(sink) else emptyList()
        }
        val metadata = JIRStaticMetadataCollector(interp(rules)).collect(key(caller))
        assertEquals(setOf(cls), metadata.classPoints)
        assertTrue(metadata.wideClasses.isEmpty()); assertFalse(metadata.allStatic)
        val ap = ApManager(Cancellation())
        val compiled = metadata.compile(ap)
        assertTrue(compiled.marks.single().marks.containsId(0), "Local producer metadata keeps the admitted mark ID")
        val profile = org.opentaint.dataflow.bidi.engine.Run1StaticProfiles.build(ap, emptyMap(),
            mapOf(caller to compiled))[caller]
        val classId = ap.accessors.index(cls)
        assertEquals(setOf(classId), profile.classPointClasses)
        assertTrue(profile.precomputedRequests.isEmpty, "No class request slot or field product is introduced")
        assertTrue(metadata.abstractions.isEmpty())
        assertTrue(profile.classPointMarks(classId).isEmpty, "Structural class consumers retain no mark payload")
    }

    @Test
    fun `neutral descriptors compile with a different AP ordering and do not retain numeric ids`(): Unit {
        val metadata = JIRStaticMetadataCollector(interp).collect(key(method("staticRead")))
        val another = ApManager(Cancellation())
        another.accessors.index(FieldAccessor("other.Owner", "other", "int"))
        another.accessors.index(ClassStaticAccessor("other.Owner"))
        val compiled = metadata.compile(another)
        val expected = slot.map(another.accessors::index)
        assertTrue(expected in compiled.accesses)
        assertTrue(expected in compiled.abstractions)
        assertFailsWith<UnsupportedOperationException> { (metadata.accesses as MutableSet<List<org.opentaint.dataflow.ap.ifds.Accessor>>).clear() }
    }

    @Test
    fun `inherited static accesses use actual declaring owner in transformed read and write`(): Unit {
        val declaring = "sample.bidi.BidiStaticMetadataSample\$Parent"
        val child = "sample.bidi.BidiStaticMetadataSample\$Child"
        val path = listOf(ClassStaticAccessor(declaring), FieldAccessor(declaring, "inherited", "java.lang.Object"))
        for (name in listOf("read", "write", "writeConstant")) {
            val metadata = JIRStaticMetadataCollector(interp).collect(key(method(name, child)))
            assertEquals(setOf(path), metadata.accesses)
            assertEquals(if (name == "read") setOf(path) else emptySet(), metadata.abstractions)
            assertEquals(name != "read", metadata.staticMayWrite)
            assertTrue(metadata.accesses.none { it.firstOrNull() == ClassStaticAccessor(child) })
        }
    }

    @Test
    fun `conditional exit leaves are included while conditional entry source remains inactive`(): Unit {
        val m = method("copy")
        val p = PositionWithAccess(ClassStatic(owner), PositionAccessor.FieldAccessor(owner, "sField", "java.lang.Object"))
        val cond: Condition = CommonCondition.Atom(ContainsMark(p, TaintMark("BOUNDARY")))
        val source = TaintMethodExitSource(m, cond, listOf(AssignMark(TaintMark("OUT"), Result)), null)
        val entrySource = TaintEntryPointSource(m, cond,
            listOf(AssignMark(TaintMark("IN"), ClassStatic("never.Owner"))), null)
        val rules = object : org.opentaint.dataflow.jvm.ap.ifds.taint.TaintRulesProvider by NoTaintRules {
            override fun exitSourceRulesForMethod(method: CommonMethod, statement: CommonInst, fact: FactAp?, allRelevant: Boolean) =
                if (method == m) listOf(source) else emptyList()
            override fun entryPointRulesForMethod(method: CommonMethod, statement: CommonInst, fact: FactAp?, allRelevant: Boolean) =
                if (method == m) listOf(entrySource) else emptyList()
        }
        val metadata = JIRStaticMetadataCollector(interp(rules)).collect(key(m))
        assertEquals(setOf(slot), metadata.accesses)
        assertEquals(setOf("BOUNDARY"), metadata.marks.map { it.mark }.toSet())
        assertTrue(metadata.abstractions.isEmpty(), "Exit ContainsMark is not an abstract field read")
        assertTrue(metadata.marks.all { it.role == StaticMarkRole.SOURCE })
        assertTrue(metadata.accesses.none { it.firstOrNull() == ClassStaticAccessor("never.Owner") })
    }

    @Test
    fun `all marks static kill contributes its exact slot without selected mark interest`(): Unit {
        val target = method("target", "sample.bidi.BidiPrescanSample")
        val p = PositionWithAccess(ClassStatic(owner), PositionAccessor.FieldAccessor(owner, "sField", "java.lang.Object"))
        val kill = TaintCleaner(target, mkTrue(), listOf(RemoveAllMarks(p)), null)
        val rules = object : org.opentaint.dataflow.jvm.ap.ifds.taint.TaintRulesProvider by NoTaintRules {
            override fun cleanerRulesForMethod(method: CommonMethod, statement: CommonInst, fact: FactAp?, allRelevant: Boolean) =
                if (method == target) listOf(kill) else emptyList()
        }
        val metadata = JIRStaticMetadataCollector(interp(rules)).collect(key(method("ruleCalls", "sample.bidi.BidiPrescanSample")))
        assertEquals(setOf(slot), metadata.accesses)
        assertTrue(metadata.abstractions.isEmpty())
        assertTrue(metadata.staticMayWrite)
        assertTrue(metadata.cleanerMarks.isEmpty())
    }

    @Test
    fun `field selected cleaner keeps local producer metadata without a frozen mark row`(): Unit {
        val v = JIRRuleTestFixtures(cp)
        val caller = method("callOnly")
        val target = checkNotNull(caller.instList.instructions.first { it.callExpr != null }.callExpr).method.method
        val position = PositionWithAccess(ClassStatic(owner),
            PositionAccessor.FieldAccessor(owner, "sField", "java.lang.Object"))
        val cleaner = v.cleaner(listOf(RemoveMark(TaintMark("T"), position,
            org.opentaint.dataflow.configuration.TaintCleanReach.ExactAndAnyField)), mkTrue(), target)
        val rules = object : org.opentaint.dataflow.jvm.ap.ifds.taint.TaintRulesProvider by NoTaintRules {
            override fun cleanerRulesForMethod(method: CommonMethod, statement: CommonInst, fact: FactAp?, allRelevant: Boolean) =
                if (method == target) listOf(cleaner) else emptyList()
        }
        val metadata = JIRStaticMetadataCollector(interp(rules)).collect(key(caller))
        assertTrue(metadata.abstractions.isEmpty())
        assertTrue(metadata.staticMayWrite)
        assertEquals(setOf(slot), metadata.marks.map { it.path.take(2) }.toSet())
        val ap = ApManager(Cancellation())
        val compiled = metadata.compile(ap)
        val selected = org.opentaint.dataflow.bidi.engine.Run1Slot.StaticField(ap.accessors.index(cls), ap.accessors.index(fld))
        val other = org.opentaint.dataflow.bidi.engine.Run1Slot.StaticField(ap.accessors.index(cls),
            ap.accessors.index(FieldAccessor(owner, "other", "java.lang.Object")))
        val profile = org.opentaint.dataflow.bidi.engine.Run1StaticProfiles.build(ap, emptyMap(),
            mapOf(caller to compiled), declaredFieldUniverse = setOf(selected, other))[caller]
        assertTrue(compiled.abstractions.isEmpty())
        assertTrue(compiled.marks.any { it.pattern.fact.path.take(2) == selected.pathData.toList() &&
            it.marks.containsId(ap.marks.mark("T").id) }, "The actual cleaner remains a local need producer")
        assertEquals(setOf(selected, other), profile.footprint.fieldUniverse)
        assertTrue(profile.precomputedRequests.isEmpty)
        assertTrue(profile.precomputedRequests.borrow(selected).isEmpty)
        assertFalse(profile.precomputedRequests.hasRow(other))
        assertTrue(profile.wideRequests(ap.factsOf(org.opentaint.dataflow.bidi.ap.PathFact(
            org.opentaint.dataflow.bidi.ap.STATIC, other.pathData.toList(), Tail.EXACT,
            org.opentaint.dataflow.bidi.ap.MarkSlot.Concrete(ap.marks.mark("T"))),
            org.opentaint.dataflow.bidi.ap.ExclusionSet.Empty, org.opentaint.dataflow.bidi.ap.Layer.NORMAL)).isEmpty())
    }

    @Test
    fun `static write knowledge uses existing direct target evidence and treats virtual residuals as unknown`(): Unit {
        val direct = method("ruleCalls", "sample.bidi.BidiPrescanSample")
        val directCalls = direct.instList.instructions.filter { it.callExpr != null }.toSet<CommonInst>()
        assertFalse(JIRStaticMetadataCollector(interp, resolvedCalls = directCalls).collect(key(direct)).staticMayWrite)
        assertTrue(JIRStaticMetadataCollector(interp, resolvedCalls = emptySet()).collect(key(direct)).staticMayWrite)
        val virtual = method("callOnly")
        val virtualCalls = virtual.instList.instructions.filter { it.callExpr != null }.toSet<CommonInst>()
        assertTrue(JIRStaticMetadataCollector(interp, resolvedCalls = virtualCalls).collect(key(virtual)).staticMayWrite,
            "A subscribed virtual target does not certify absence of unknown residual targets")
    }

    @Test
    fun `genuine static self-copy pass reads its field unlike syntactic write-preservation keeps`(): Unit {
        val v = JIRRuleTestFixtures(cp)
        val caller = method("callOnly")
        val target = checkNotNull(caller.instList.instructions.first { it.callExpr != null }.callExpr).method.method
        val field = PositionWithAccess(ClassStatic(owner),
            PositionAccessor.FieldAccessor(owner, "sField", "java.lang.Object"))
        val pass = v.pass(listOf(CopyAllMarks(field, field)), mkTrue(), target)
        val rules = object : org.opentaint.dataflow.jvm.ap.ifds.taint.TaintRulesProvider by NoTaintRules {
            override fun passTroughRulesForMethod(method: CommonMethod, statement: CommonInst?, fact: FactAp?, allRelevant: Boolean) =
                if (method == target) listOf(pass) else emptyList()
        }
        val metadata = JIRStaticMetadataCollector(interp(rules)).collect(key(caller))
        assertEquals(setOf(slot), metadata.abstractions, "A rule read stays a read even when its target is identical")
        assertTrue(metadata.marks.isEmpty(), "CopyAllMarks does not manufacture concrete mark needs")
        assertTrue(JIRStaticMetadataCollector(interp).collect(key(method("staticWrite"))).abstractions.isEmpty())
    }

    @Test
    fun `uncomputed prescan aliases stay uncomputed and signed potential branches keep only live footprints`(): Unit {
        val m = method("aliasRule", "sample.bidi.BidiStaticMetadataSample")
        val call = m.instList.instructions.single { it.callExpr != null }
        assertIs<org.opentaint.ir.api.jvm.cfg.JIRLocalVar>(
            (checkNotNull(call.callExpr) as org.opentaint.ir.api.jvm.cfg.JIRInstanceCallExpr).instance)
        val target = checkNotNull(call.callExpr).method.method
        val unknown: Condition = CommonCondition.Atom(ConstantMatches(This, Regex("wanted")))
        fun marked(name: String): Condition = CommonCondition.Atom(ContainsMark(
            PositionWithAccess(ClassStatic(owner), PositionAccessor.FieldAccessor(owner, "sField", "java.lang.Object")),
            TaintMark(name)))
        fun source(name: String, cond: Condition) = TaintMethodSource(target,
            CommonCondition.And(listOf(marked(name), cond)), listOf(AssignMark(TaintMark("OUT"), Result)), null)
        val rules = object : org.opentaint.dataflow.jvm.ap.ifds.taint.TaintRulesProvider by NoTaintRules {
            override fun sourceRulesForMethod(method: CommonMethod, statement: CommonInst, fact: FactAp?, allRelevant: Boolean) =
                if (method == target) listOf(source("P", unknown), source("N", CommonCondition.Not(unknown)),
                    source("CONTRADICTION", CommonCondition.And(listOf(unknown, CommonCondition.Not(unknown)))),
                    source("KNOWN_FALSE", CommonCondition.Atom(IsConstant(This)))) else emptyList()
        }
        JIRPrescanFixtures(cp, graph, aliases = true).use { f ->
            f.run("normal")
            assertFalse(f.cancellation.isActive(), "Prescan shutdown has cancelled its token")
            val aliases = assertNotNull(f.context(m).aliasAnalysis)
            assertFalse(aliases.hasComputedAliasInfo())
            f.beforeResolve = { error("Metadata may not resolve a call") }
            val metadata = JIRStaticMetadataCollector(interp(rules), MetadataAliases.Unknown).collect(key(m))
            assertFalse(aliases.hasComputedAliasInfo(), "Metadata must borrow, never initialize, alias analysis")
            assertEquals(setOf("P", "N"), metadata.marks.map { it.mark }.toSet())
        }
    }

    @Test
    fun `native generated cleaners are visited without resolving calls or constructing persisted aliases`(): Unit {
        val v = org.opentaint.dataflow.jvm.bidi.fixtures.JIRCallTestFixtures(cp)
        val original = v.call("staticCall") as org.opentaint.ir.api.jvm.cfg.JIRAssignInst
        val call = v.expr(original) as org.opentaint.ir.api.jvm.cfg.JIRStaticCallExpr
        val direct = org.opentaint.dataflow.bidi.ap.Cleaner(org.opentaint.dataflow.ap.ifds.AccessPathBase.ClassStatic,
            null, org.opentaint.dataflow.bidi.ap.CleanReach.AT_AND_BELOW, null)
        val named = object : org.opentaint.ir.api.jvm.JIRMethod by call.method.method,
            org.opentaint.dataflow.bidi.interp.CallCleaners { override val callCleaners = listOf(direct) }
        val typed = object : org.opentaint.ir.api.jvm.JIRTypedMethod by call.method { override val method = named }
        val reference = object : org.opentaint.ir.api.jvm.cfg.TypedMethodRef {
            override val name = typed.name
            override val method = typed
        }
        val expr = org.opentaint.ir.api.jvm.cfg.JIRStaticCallExpr(reference, call.args)
        val s = org.opentaint.ir.api.jvm.cfg.JIRAssignInst(original.location, original.lhv, expr)
        val builder = org.opentaint.dataflow.jvm.bidi.interp.JIRCallPlanBuilder(v.manager, v.forms(), v.checker,
            v.typeFilters, v.key(s.location.method), s, expr, { _, _, _ -> error("Second resolution") },
            v.epResolver, emptyMap(), persistedAliases = { _, _ -> error("Alias rebuild") })
        val visited = ArrayList<org.opentaint.dataflow.bidi.interp.CleanStep>()
        builder.visitStaticMetadata({}, {}, visited::add)
        assertSame(direct, (visited.single() as org.opentaint.dataflow.bidi.interp.CleanStep.Clean).cleaner)
    }
}
