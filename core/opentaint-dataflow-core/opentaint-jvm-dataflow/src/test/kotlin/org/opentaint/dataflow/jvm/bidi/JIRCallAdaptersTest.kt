package org.opentaint.dataflow.jvm.bidi

import kotlinx.coroutines.runBlocking
import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.ap.ifds.Accessor
import org.opentaint.dataflow.ap.ifds.ClassStaticAccessor
import org.opentaint.dataflow.ap.ifds.CombinedMethodContext
import org.opentaint.dataflow.ap.ifds.ElementAccessor
import org.opentaint.dataflow.ap.ifds.EmptyMethodContext
import org.opentaint.dataflow.ap.ifds.ExclusionSet
import org.opentaint.dataflow.ap.ifds.FieldAccessor
import org.opentaint.dataflow.ap.ifds.MethodContext
import org.opentaint.dataflow.ap.ifds.MethodEntryPoint
import org.opentaint.dataflow.ap.ifds.MethodWithContext
import org.opentaint.dataflow.ap.ifds.TaintMarkAccessor
import org.opentaint.dataflow.ap.ifds.access.AnyAccessorUnrollStrategy
import org.opentaint.dataflow.ap.ifds.access.FinalFactAp
import org.opentaint.dataflow.ap.ifds.access.tree.TreeApManager
import org.opentaint.dataflow.ap.ifds.taint.TaintAnalysisUnitStorage
import org.opentaint.dataflow.ap.ifds.taint.TaintSinkTracker
import org.opentaint.dataflow.configuration.mkTrue
import org.opentaint.dataflow.configuration.jvm.Argument
import org.opentaint.dataflow.configuration.jvm.CopyAllMarks
import org.opentaint.dataflow.configuration.jvm.Result
import org.opentaint.dataflow.configuration.jvm.TaintPassThrough
import org.opentaint.dataflow.ifds.SingletonUnit
import org.opentaint.dataflow.ifds.UnitType
import org.opentaint.dataflow.ifds.UnknownUnit
import org.opentaint.dataflow.jvm.BasicTestUtils
import org.opentaint.dataflow.jvm.ap.ifds.JIRArgumentTypeMethodContext
import org.opentaint.dataflow.jvm.ap.ifds.JIRCallResolutionContext
import org.opentaint.dataflow.jvm.ap.ifds.JIRCallResolver
import org.opentaint.dataflow.jvm.ap.ifds.JIRCallResolver.MethodResolutionResult
import org.opentaint.dataflow.jvm.ap.ifds.JIRInstanceTypeMethodContext
import org.opentaint.dataflow.jvm.ap.ifds.JIRLanguageManager
import org.opentaint.dataflow.jvm.ap.ifds.JIRLocalAliasAnalysis
import org.opentaint.dataflow.jvm.ap.ifds.JIRLocalAliasAnalysis.AliasAccessor
import org.opentaint.dataflow.jvm.ap.ifds.JIRLocalAliasAnalysis.AliasAllocInfo
import org.opentaint.dataflow.jvm.ap.ifds.JIRLocalAliasAnalysis.AliasApInfo
import org.opentaint.dataflow.jvm.ap.ifds.JIRLocalAliasAnalysis.AliasInfo
import org.opentaint.dataflow.jvm.ap.ifds.JIRLocalVariableReachability
import org.opentaint.dataflow.jvm.ap.ifds.MethodFlowFunctionUtils.accessPathBase
import org.opentaint.dataflow.jvm.ap.ifds.TypeConstraintInfo
import org.opentaint.dataflow.jvm.ap.ifds.analysis.JIRAnalysisManager
import org.opentaint.dataflow.jvm.ap.ifds.analysis.JIRMethodAnalysisContext
import org.opentaint.dataflow.jvm.ap.ifds.analysis.aliasesPersistedThroughCall
import org.opentaint.dataflow.jvm.ap.ifds.analysis.forEachAliasAfterCallStatement
import org.opentaint.dataflow.jvm.ap.ifds.taint.JIRTaintAnalysisContext
import org.opentaint.dataflow.jvm.ap.ifds.taint.TaintRulesProvider
import org.opentaint.dataflow.jvm.bidi.fixtures.JIRCallTestFixtures
import org.opentaint.dataflow.jvm.bidi.fixtures.NoTaintRules
import org.opentaint.dataflow.jvm.ifds.JIRUnitResolver
import org.opentaint.dataflow.util.Cancellation
import org.opentaint.dataflow.util.RefManager
import org.opentaint.dataflow.util.SoftReferenceManager
import org.opentaint.ir.api.jvm.JIRMethod
import org.opentaint.ir.api.jvm.RegisteredLocation
import org.opentaint.ir.api.common.CommonMethod
import org.opentaint.ir.api.common.cfg.CommonInst
import org.opentaint.dataflow.ap.ifds.access.FactAp
import org.opentaint.ir.api.jvm.cfg.JIRAssignInst
import org.opentaint.ir.api.jvm.cfg.JIRFieldRef
import org.opentaint.ir.api.jvm.cfg.JIRInst
import org.opentaint.ir.api.jvm.cfg.JIRInstanceCallExpr
import org.opentaint.ir.api.jvm.cfg.JIRNewExpr
import org.opentaint.ir.api.jvm.cfg.JIRStringConstant
import org.opentaint.ir.api.jvm.ext.cfg.callExpr
import org.opentaint.ir.impl.features.usagesExt
import org.opentaint.jvm.graph.JApplicationGraphImpl
import org.opentaint.jvm.graph.JApplicationSingleExitGraph
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class JIRCallAdaptersTest : BasicTestUtils() {
    private val f by lazy { JIRCallTestFixtures(cp) }
    private val language by lazy { JIRLanguageManager(cp) }
    private val graph by lazy { JApplicationSingleExitGraph(JApplicationGraphImpl(cp, runBlocking { cp.usagesExt() })) }
    private val knownLocation by lazy { f.method("staticCall").enclosingClass.declaration.location }
    private val units by lazy { KnownLocation(knownLocation) }
    private val resolver by lazy { JIRCallResolver(cp, units) }
    private val oldManager by lazy { JIRAnalysisManager(cp, RefManager(), NoTaintRules) }
    private val oldAp by lazy {
        TreeApManager(object : AnyAccessorUnrollStrategy {
            override fun unrollAccessor(accessor: Accessor) = false
        }, RefManager(), Cancellation().also { it.activate() })
    }
    private val baseClass by lazy { checkNotNull(cp.findClassOrNull(BASE)) }
    private val derivedClass by lazy { checkNotNull(cp.findClassOrNull(DERIVED)) }

    private fun context(method: JIRMethod, type: MethodContext = EmptyMethodContext,
                        aliases: JIRLocalAliasAnalysis? = null): JIRCallResolutionContext =
        object : JIRCallResolutionContext {
            override val methodEntryPoint = f.key(method, type)
            override val aliasAnalysis = aliases
        }

    private fun aliases(method: JIRMethod, rules: TaintRulesProvider = NoTaintRules): JIRLocalAliasAnalysis = JIRLocalAliasAnalysis(
        f.key(method).statement as JIRInst, graph, resolver, rules,
        JIRLocalVariableReachability(method, graph, language), Cancellation().also { it.activate() },
        language, JIRLocalAliasAnalysis.Params(aliasAnalysisInterProcCallDepth = 0),
    )

    private fun oldContext(method: JIRMethod, type: MethodContext, aliases: JIRLocalAliasAnalysis?): JIRMethodAnalysisContext =
        JIRMethodAnalysisContext(oldManager, SoftReferenceManager(RefManager()), f.key(method, type), oldManager.factTypeChecker,
            JIRLocalVariableReachability(method, graph, oldManager), aliases,
            JIRTaintAnalysisContext(TaintSinkTracker(TaintAnalysisUnitStorage(oldAp, oldManager)), NoTaintRules,
                relevantRuleIds = hashSetOf()))

    private fun concrete(results: List<MethodResolutionResult>): Set<MethodWithContext> =
        results.filterIsInstance<MethodResolutionResult.ConcreteMethod>().map { it.method }.toSet()

    private fun resolve(name: String, ctx: JIRCallResolutionContext): List<MethodResolutionResult> {
        val statement = f.call(name)
        return resolver.resolve(f.expr(statement), statement, ctx)
    }

    @Test
    fun `minimal resolution context preserves concrete and empty target results of the existing resolver`(): Unit {
        for ((caller, target) in listOf("staticCall" to "target", "staticExternal" to "external")) {
            val results = resolve(caller, context(f.method(caller)))
            assertEquals(setOf(MethodWithContext(f.method(target), EmptyMethodContext)), concrete(results))
            assertTrue(results.none { it === MethodResolutionResult.MethodResolutionFailed })
        }
        // I8 dropping is the call-plan adapter's responsibility; preserve the old resolver's empty concrete result.
        assertEquals(0, f.method("external").instList.size)
        val unknown = JIRCallResolver(cp, object : JIRUnitResolver {
            override fun resolve(method: JIRMethod): UnitType = UnknownUnit
            override fun locationIsUnknown(loc: RegisteredLocation) = true
        })
        val statement = f.call("staticCall")
        assertEquals(listOf<MethodResolutionResult>(MethodResolutionResult.MethodResolutionFailed),
            unknown.resolve(f.expr(statement), statement, context(f.method("staticCall"))))
    }

    @Test
    fun `minimal context preserves Object ignore and lambda failure branches`(): Unit {
        val method = f.method("callAssign", "sample.bidi.BidiStatementSample")
        val statement = method.instList.instructions.first { it.callExpr?.method?.method?.enclosingClass?.name == "java.lang.Object" }
        val result = resolver.resolve(checkNotNull(statement.callExpr), statement, context(method))
        assertEquals(listOf<MethodResolutionResult>(MethodResolutionResult.MethodResolutionFailed), result)
        val lambda = resolve("lambdaCall", context(f.method("lambdaCall")))
        val named = f.expr(f.call("lambdaCall")).method.method
        assertTrue(lambda.any { it is MethodResolutionResult.Lambda && it.method == named })
        assertTrue(lambda.any { it === MethodResolutionResult.MethodResolutionFailed })
        assertTrue(concrete(lambda).isEmpty())
    }

    @Test
    fun `caller Argument context selects virtual methods independently for each method key`(): Unit {
        val method = f.method("virtualCall")
        val aa = aliases(method)
        val unbound = resolve("virtualCall", context(method, aliases = aa))
        val expectedDefault = setOf(f.method("virtual", BASE), f.method("virtual", DERIVED))
        assertEquals(expectedDefault, concrete(unbound).map { it.method }.toSet())
        for ((cls, owner) in listOf(baseClass to BASE, derivedClass to DERIVED)) {
            val exact = JIRArgumentTypeMethodContext(0, TypeConstraintInfo(cls, exactType = true))
            val selected = resolve("virtualCall", context(method, exact, aa))
            assertEquals(setOf(MethodWithContext(f.method("virtual", owner), EmptyMethodContext)), concrete(selected))
            assertTrue(selected.none { it === MethodResolutionResult.MethodResolutionFailed })
        }
        val unrelatedArgument = JIRArgumentTypeMethodContext(1, TypeConstraintInfo(derivedClass, exactType = true))
        assertEquals(concrete(unbound), concrete(resolve("virtualCall", context(method, unrelatedArgument, aa))))
        val unrelatedThis = JIRInstanceTypeMethodContext(TypeConstraintInfo(derivedClass, exactType = true))
        assertEquals(concrete(unbound), concrete(resolve("virtualCall", context(method, unrelatedThis, aa))))
        val combined = CombinedMethodContext(unrelatedThis,
            JIRArgumentTypeMethodContext(0, TypeConstraintInfo(derivedClass, exactType = true)))
        assertEquals(setOf(MethodWithContext(f.method("virtual", DERIVED), EmptyMethodContext)),
            concrete(resolve("virtualCall", context(method, combined, aa))))
    }

    @Test
    fun `local alias and allocation constraints use only the minimal caller method and alias inputs`(): Unit {
        val aliased = f.method("virtualAlias")
        val aliasAnalysis = aliases(aliased)
        val exactDerived = JIRArgumentTypeMethodContext(0, TypeConstraintInfo(derivedClass, exactType = true))
        assertEquals(setOf(MethodWithContext(f.method("virtual", DERIVED), EmptyMethodContext)),
            concrete(resolve("virtualAlias", context(aliased, exactDerived, aliasAnalysis))))
        val allocated = f.method("virtualAllocated")
        val allocationAliases = aliases(allocated)
        val statement = f.call("virtualAllocated")
        val receiver = (f.expr(statement) as JIRInstanceCallExpr).instance
        val receiverBase = assertIs<AccessPathBase.LocalVar>(accessPathBase(receiver))
        assertTrue(allocationAliases.findAlias(receiverBase, statement).orEmpty().any { it is AliasAllocInfo })
        assertEquals(setOf(MethodWithContext(f.method("virtual", DERIVED), EmptyMethodContext)),
            concrete(resolve("virtualAllocated", context(allocated, aliases = allocationAliases))))
    }

    @Test
    fun `existing prescan analysis context implements the minimal interface with unchanged resolver behavior`(): Unit {
        val type = JIRArgumentTypeMethodContext(0, TypeConstraintInfo(derivedClass, exactType = true))
        for (name in listOf("staticCall", "instanceCall", "virtualCall", "virtualAlias", "virtualAllocated", "lambdaCall")) {
            val method = f.method(name)
            val aa = aliases(method)
            val old = oldContext(method, type, aa)
            val narrowed: JIRCallResolutionContext = old
            assertSame(old.methodEntryPoint, narrowed.methodEntryPoint)
            assertSame(old.aliasAnalysis, narrowed.aliasAnalysis)
            val statement = f.call(name)
            assertEquals(resolver.resolve(f.expr(statement), statement, old).toSet(),
                resolver.resolve(f.expr(statement), statement, context(method, type, aa)).toSet(), name)
        }
    }

    private fun local(name: String): AccessPathBase.LocalVar {
        val instructions = f.method(name).instList.instructions.filterIsInstance<JIRAssignInst>()
        val assignment = when (name) {
            "aliasPersisted", "aliasOverwritten", "aliasBeforeOnly" -> instructions.first { (it.rhv as? JIRFieldRef)?.field?.name == "f" }
            "aliasAllocation" -> instructions.first { (it.rhv as? JIRNewExpr)?.type?.typeName == "java.lang.Object" }
            "aliasConstant" -> instructions.first { it.rhv is JIRStringConstant }
            "aliasSelfPath" -> {
                val write = instructions.first { (it.lhv as? JIRFieldRef)?.field?.name == "next" }
                return assertIs(accessPathBase(assertNotNull((write.lhv as JIRFieldRef).instance)))
            }
            else -> error(name)
        }
        return assertIs(accessPathBase(assignment.lhv))
    }

    private fun snapshots(name: String): Triple<JIRLocalAliasAnalysis, List<AliasInfo>, List<AliasInfo>> {
        val aa = aliases(f.method(name))
        val statement = f.call(name)
        return Triple(aa, aa.findAlias(local(name), statement).orEmpty(), aa.findAliasAfterStatement(local(name), statement).orEmpty())
    }

    /** The independent set condition; retained order and duplicate occurrences follow the BEFORE list. */
    private fun expected(before: List<AliasInfo>, after: List<AliasInfo>): List<AliasApInfo> = before
        .filterIsInstance<AliasApInfo>().filter { it.base !is AccessPathBase.Constant && after.contains(it) }

    @Test
    fun `persisted aliases retain complete equal paths from both sides of a call`(): Unit {
        val name = "aliasPersisted"
        val (aa, before, after) = snapshots(name)
        val path = listOf(AliasAccessor.Field(JIRCallTestFixtures.SAMPLE_CLASS, "next", JIRCallTestFixtures.SAMPLE_CLASS),
            AliasAccessor.Field(JIRCallTestFixtures.SAMPLE_CLASS, "f", "java.lang.Object"))
        val held = AliasApInfo(AccessPathBase.Argument(0), path)
        assertTrue(held in before, "nested field path exists before call")
        assertTrue(held in after, "unpassed receiver's nested field path persists")
        val actual = aa.aliasesPersistedThroughCall(local(name), f.call(name))
        assertEquals(expected(before, after), actual)
        assertTrue(held in actual)
        assertTrue(actual.none { it.base == held.base && it.accessors.isEmpty() })
    }

    @Test
    fun `before-only aliases and newly created result aliases do not persist through call`(): Unit {
        val name = "aliasOverwritten"
        val (aa, before, after) = snapshots(name)
        val oldPath = AliasApInfo(AccessPathBase.Argument(0),
            listOf(AliasAccessor.Field(JIRCallTestFixtures.SAMPLE_CLASS, "f", "java.lang.Object")))
        val statement = f.call(name)
        // JIR assigns the call result to a temporary, then copies it to a at the next statement.
        assertTrue(oldPath in before, "old value is a live call operand")
        assertTrue(oldPath in after, "old local is unchanged until the following copy")
        val actual = aa.aliasesPersistedThroughCall(local(name), f.call(name))
        assertEquals(expected(before, after), actual)
        assertTrue(oldPath in actual)
        assertTrue(actual.all { it in before && it in after })

        // Passing x invalidates its mutable field path while a stays live across the call.
        val beforeOnly = "aliasBeforeOnly"
        val (mutating, mutableBefore, mutableAfter) = snapshots(beforeOnly)
        assertTrue(oldPath in mutableBefore, "field alias exists before its receiver is passed")
        assertTrue(oldPath !in mutableAfter, "passing the receiver invalidates its field alias")
        val mutatingPersisted = mutating.aliasesPersistedThroughCall(local(beforeOnly), f.call(beforeOnly))
        assertEquals(expected(mutableBefore, mutableAfter), mutatingPersisted)
        assertTrue(oldPath !in mutatingPersisted)

        // This actual call model creates a new result alias to the second argument after the call.
        val external = f.method("external")
        val model = TaintPassThrough(external, mkTrue(), listOf(CopyAllMarks(Argument(1), Result)), null)
        val provider = object : TaintRulesProvider by NoTaintRules {
            override fun passTroughRulesForMethod(method: CommonMethod, statement: CommonInst?, fact: FactAp?,
                                                 allRelevant: Boolean): Iterable<TaintPassThrough> =
                if (method == external) listOf(model) else emptyList()
        }
        val modeled = aliases(f.method(name), provider)
        val callLhs = assertIs<AccessPathBase.LocalVar>(accessPathBase(assertIs<JIRAssignInst>(statement).lhv))
        val modelBefore = modeled.findAlias(callLhs, statement).orEmpty()
        val modelAfter = modeled.findAliasAfterStatement(callLhs, statement).orEmpty()
        val newResult = AliasApInfo(AccessPathBase.Argument(1), emptyList())
        assertTrue(newResult !in modelBefore)
        assertTrue(newResult in modelAfter)
        val modelPersisted = modeled.aliasesPersistedThroughCall(callLhs, statement)
        assertEquals(expected(modelBefore, modelAfter), modelPersisted)
        assertTrue(newResult !in modelPersisted)
    }

    @Test
    fun `constant and allocation payloads are excluded from the persisted AP aliases`(): Unit {
        for (name in listOf("aliasConstant", "aliasAllocation")) {
            val (aa, before, after) = snapshots(name)
            if (name == "aliasConstant") {
                assertTrue(before.any { it is AliasApInfo && it.base is AccessPathBase.Constant })
                assertTrue(after.any { it is AliasApInfo && it.base is AccessPathBase.Constant })
            } else {
                assertTrue(before.any { it is AliasAllocInfo })
                assertTrue(after.any { it is AliasAllocInfo })
            }
            val actual = aa.aliasesPersistedThroughCall(local(name), f.call(name))
            assertEquals(expected(before, after), actual, name)
            assertTrue(actual.none { it.base is AccessPathBase.Constant })
        }
    }

    @Test
    fun `persistent self field paths remain while missing alias states yield an empty helper result`(): Unit {
        val name = "aliasSelfPath"
        val (aa, before, after) = snapshots(name)
        val x = local(name)
        val held = AliasApInfo(x,
            listOf(AliasAccessor.Field(JIRCallTestFixtures.SAMPLE_CLASS, "next", JIRCallTestFixtures.SAMPLE_CLASS)))
        assertTrue(held in before, "heap self reference before call")
        assertTrue(held in after, "heap self reference after call")
        assertTrue(held in aa.aliasesPersistedThroughCall(x, f.call(name)))
        assertEquals(expected(before, after), aa.aliasesPersistedThroughCall(x, f.call(name)))
        assertTrue(aa.aliasesPersistedThroughCall(AccessPathBase.LocalVar(Int.MAX_VALUE), f.call(name)).isEmpty())
    }

    private fun accessor(a: AliasAccessor): Accessor = when (a) {
        is AliasAccessor.Field -> FieldAccessor(a.className, a.fieldName, a.fieldType)
        is AliasAccessor.Array -> ElementAccessor
        is AliasAccessor.Static -> ClassStaticAccessor(a.typeName)
    }

    @Test
    fun `old callback maps the same persisted aliases including complete input suffix and marks`(): Unit {
        for (name in listOf("aliasPersisted", "aliasOverwritten", "aliasBeforeOnly", "aliasConstant", "aliasAllocation", "aliasSelfPath")) {
            val (aa, before, after) = snapshots(name)
            val input = oldAp.createFinalAp(local(name), ExclusionSet.Empty)
                .prependAccessor(TaintMarkAccessor("T")).prependAccessor(ElementAccessor)
            val expectedFacts = expected(before, after).map { alias ->
                alias.accessors.foldRight(input.rebase(alias.base)) { a, value -> value.prependAccessor(accessor(a)) }
            }
            val actual = arrayListOf<FinalFactAp>()
            aa.forEachAliasAfterCallStatement(f.call(name), input, actual::add)
            assertEquals(expectedFacts, actual, name)
            val nonlocal = arrayListOf<FinalFactAp>()
            aa.forEachAliasAfterCallStatement(f.call(name), input.rebase(AccessPathBase.Argument(0)), nonlocal::add)
            assertTrue(nonlocal.isEmpty())
        }
    }

    private class KnownLocation(private val known: RegisteredLocation) : JIRUnitResolver {
        override fun resolve(method: JIRMethod): UnitType =
            if (method.enclosingClass.declaration.location == known) SingletonUnit else UnknownUnit
        override fun locationIsUnknown(loc: RegisteredLocation): Boolean = loc != known
    }

    private companion object {
        const val BASE = "sample.bidi.BidiCallSample\$Base"
        const val DERIVED = "sample.bidi.BidiCallSample\$Derived"
    }
}
