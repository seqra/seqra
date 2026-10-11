package org.opentaint.jvm.sast.project.spring

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.opentaint.common.sast.dataflow.TaintAnalyzer
import org.opentaint.common.sast.dataflow.TaintAnalyzerOptions
import org.opentaint.dataflow.ap.ifds.MethodWithContext
import org.opentaint.dataflow.ap.ifds.TaintAnalysisManager
import org.opentaint.dataflow.ap.ifds.TaintAnalysisUnitRunnerManager
import org.opentaint.dataflow.ap.ifds.access.ApMode
import org.opentaint.dataflow.ap.ifds.access.FactAp
import org.opentaint.dataflow.ap.ifds.access.InitialFactAp
import org.opentaint.dataflow.ap.ifds.trace.VulnerabilityWithTrace
import org.opentaint.dataflow.ap.ifds.trace.path.TracePathGenerationResult
import org.opentaint.dataflow.bidi.driver.*
import org.opentaint.dataflow.configuration.CommonCondition
import org.opentaint.dataflow.configuration.CommonTaintConfigurationSinkMeta
import org.opentaint.dataflow.configuration.jvm.*
import org.opentaint.dataflow.configuration.mkTrue
import org.opentaint.dataflow.ifds.SingletonUnit
import org.opentaint.dataflow.ifds.UnknownUnit
import org.opentaint.dataflow.jvm.ap.ifds.JIRLocalAliasAnalysis
import org.opentaint.dataflow.jvm.ap.ifds.analysis.JIRAnalysisManager
import org.opentaint.dataflow.jvm.ap.ifds.taint.TaintRulesProvider
import org.opentaint.dataflow.jvm.bidi.JIRBidiAnalysis
import org.opentaint.dataflow.jvm.ifds.JIRUnitResolver
import org.opentaint.dataflow.jvm.util.JIRInstListBuilder
import org.opentaint.dataflow.jvm.util.typeName
import org.opentaint.ir.api.common.CommonMethod
import org.opentaint.ir.api.common.cfg.CommonInst
import org.opentaint.ir.api.jvm.JIRClassOrInterface
import org.opentaint.ir.api.jvm.JIRField
import org.opentaint.ir.api.jvm.JIRMethod
import org.opentaint.ir.api.jvm.RegisteredLocation
import org.opentaint.ir.api.jvm.cfg.*
import org.opentaint.ir.api.jvm.ext.findMethodOrNull
import org.opentaint.ir.api.jvm.ext.toType
import org.opentaint.ir.impl.cfg.VirtualMethodRefImpl
import org.opentaint.ir.impl.features.usagesExt
import org.opentaint.jvm.graph.JApplicationGraphImpl
import org.opentaint.jvm.graph.JApplicationSingleExitGraph
import org.opentaint.jvm.sast.ast.BasicTestUtils
import org.opentaint.jvm.sast.dataflow.ClassLocationChecker
import org.opentaint.jvm.sast.dataflow.JIRTaintAnalyzer
import kotlin.test.*
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/** Whole generated dispatch, constructor, controller and cleanup execution through phase 3. */
class SpringDispatcherAcceptanceTest : BasicTestUtils() {
    override val sourceFileExtension = "java"
    private val store by lazy { findMethod(COMPONENT, "store") }
    private val read by lazy { findMethod(COMPONENT, "read") }
    private val spring by lazy { SpringWebProjectContext(setOf(store, read), cp).also { it.addComponent(findClass(COMPONENT)) } }
    private val root by lazy {
        val wrappers = listOf(wrapper(store), wrapper(read))
        val generate = Class.forName("org.opentaint.jvm.sast.project.spring.SpringWebProjectKt")
            .getDeclaredMethod("generateDispatcher", SpringWebProjectContext::class.java, List::class.java)
            .also { it.isAccessible = true }
        generate.invoke(null, spring, wrappers) as JIRMethod
    }
    private val projects by lazy { object : ClassLocationChecker {
        override fun isProjectLocation(loc: RegisteredLocation): Boolean = loc == store.enclosingClass.declaration.location
        override fun isProjectClass(cls: JIRClassOrInterface): Boolean = isProjectLocation(cls.declaration.location)
    } }
    private val units by lazy { object : JIRUnitResolver {
        override fun resolve(method: JIRMethod) = if (projects.isProjectClass(method.enclosingClass)) SingletonUnit else UnknownUnit
        override fun locationIsUnknown(loc: RegisteredLocation): Boolean = !projects.isProjectLocation(loc)
    } }

    @Test
    fun `actual optional JVM entry preserves component registry between controller calls and clears ordinary static state`(): Unit {
        // Construction is forced before analysis so the actual generated root is on the extended classpath.
        val dispatch = root
        JIRTaintAnalyzer(cp, provider(), projects, options(), analysisUnit = units,
            bidiPolicy = FixedLimits(listOf(1, 2, 3))).use { analyzer ->
            assertIs<JIRBidiAnalysis>(analyzer.bidiEntry())
            val (output, status) = analyzer.analyzeWithIfds(listOf(dispatch))
            assertEquals(TaintAnalysisUnitRunnerManager.Status.OK, status.analysisStatus)
            assertEquals(TaintAnalysisUnitRunnerManager.Status.OK, status.traceResolutionStatus)
            assertEquals(EXPECTED, output.map { it.vulnerability.ruleId }.toSet())
            assertTrue(output.all { it.trace === TracePathGenerationResult.Simple })
            assertEquals(setOf(store), output.filter { it.vulnerability.ruleId != "registry" }
                .map { (it.vulnerability.statement as JIRInst).location.method }.toSet())
            assertEquals(setOf(read), output.filter { it.vulnerability.ruleId == "registry" }
                .map { (it.vulnerability.statement as JIRInst).location.method }.toSet())
            assertTrue(analyzer.ifdsEngine.allUnits().isEmpty())
        }
    }

    @Test
    fun `real joined prescan and driver confirm the exact controller sink keys without an outside finding`(): Unit {
        val dispatch = root
        val graph = runBlocking { JApplicationSingleExitGraph(JApplicationGraphImpl(cp, cp.usagesExt())) }
        val rules = provider()
        val params = JIRAnalysisManager.Params(aliasAnalysisParams = JIRLocalAliasAnalysis.Params(useAliasAnalysis = false))
        var report: Report? = null
        val analyzer = object : TaintAnalyzer<JIRMethod, JIRInst>(options()) {
            override fun analysisGraph() = graph
            override fun analysisManager() = JIRAnalysisManager(cp, refManager, rules, params = params)
            override fun unitResolver() = units
            override fun bidiEntry(): BidiEntry {
                val actual = JIRBidiAnalysis(cp, graph, units, rules, params, FixedLimits(listOf(1, 2, 3)), refManager)
                return object : BidiEntry {
                    override fun gather(manager: TaintAnalysisManager, startMethods: List<MethodWithContext>) =
                        actual.gather(manager, startMethods)
                    override fun run(budget: Duration): Report = actual.run(budget).also { report = it }
                    override fun toVulnerabilities(report: Report): List<VulnerabilityWithTrace> = actual.toVulnerabilities(report)
                    override fun status(report: Report) = actual.status(report)
                }
            }
        }
        analyzer.use {
            val (output, status) = analyzer.analyzeWithIfds(listOf(dispatch))
            assertEquals(TaintAnalysisUnitRunnerManager.Status.OK, status.analysisStatus)
            assertEquals(EXPECTED, output.map { it.vulnerability.ruleId }.toSet())
            val completed = assertNotNull(report)
            assertEquals(EXPECTED, completed.entries.map { it.key.rule.id }.toSet())
            assertTrue(completed.entries.all { it.state == ReportState.CONFIRMED })
            assertTrue(completed.entries.all { e -> e.witnesses.any { it.confirmed } })
            assertTrue(completed.entries.none { it.key.rule.id == "outside" })
            assertTrue(analyzer.ifdsEngine.allUnits().isEmpty())
            assertFailsWith<IllegalStateException> { analyzer.ifdsEngine.apManager }
        }
    }

    /** Actual Spring wrapper geometry: registry load then the real controller virtual call. */
    private fun wrapper(controller: JIRMethod): JIRMethod {
        val component = controller.enclosingClass.toType()
        val typed = checkNotNull(component.findMethodOrNull(controller.name, controller.description))
        val instructions = JIRInstListBuilder()
        val wrapper = SpringGeneratedMethod("acceptance_${controller.name}", "void".typeName(),
            "()V", emptyList(), instructions).also {
            spring.controllerDispatcherMethods += it
            it.bind(spring.controllerDispatcher)
        }
        val receiver = JIRLocalVar(0, "controller", component)
        instructions.addInstWithLocation(wrapper) { loc ->
            JIRAssignInst(loc, receiver, spring.registryFieldRef(controller.enclosingClass))
        }
        instructions.addInstWithLocation(wrapper) { loc ->
            JIRCallInst(loc, JIRVirtualCallExpr(VirtualMethodRefImpl.of(component, typed), receiver,
                typed.parameters.map { JIRNullConstant(it.type) }))
        }
        instructions.addInstWithLocation(wrapper) { loc -> JIRReturnInst(loc, null) }
        return wrapper
    }

    private fun provider(): TaintRulesProvider {
        val mark = TaintMark("Spring dispatcher T")
        val delegate = object : TaintRulesProvider by AcceptanceEmptyRules {
            override fun entryPointRulesForMethod(method: CommonMethod, statement: CommonInst, fact: FactAp?, allRelevant: Boolean) =
                if (method == store) listOf(TaintEntryPointSource(method, mkTrue(), listOf(AssignMark(mark, Argument(0))),
                    null, "controller-entry")) else emptyList()

            override fun sourceRulesForMethod(method: CommonMethod, statement: CommonInst, fact: FactAp?, allRelevant: Boolean) =
                if (method == findMethod(SAMPLE, "source")) listOf(TaintMethodSource(method, mkTrue(),
                    listOf(AssignMark(mark, Result)), null, "registry-source")) else emptyList()

            override fun sinkRulesForMethod(method: CommonMethod, statement: CommonInst, fact: FactAp?, allRelevant: Boolean): Iterable<TaintMethodSink> {
                val id = when (method) {
                    findMethod(SAMPLE, "directSink") -> "direct"
                    findMethod(SAMPLE, "insideStaticSink") -> "inside-static"
                    findMethod(SAMPLE, "registrySink") -> "registry"
                    findMethod(SAMPLE, "outsideSink") -> "outside"
                    else -> return emptyList()
                }
                return listOf(TaintMethodSink(method, CommonCondition.Atom<JirCondition>(ContainsMark(Argument(0), mark)),
                    emptyList(), id, TaintSinkMeta(id, CommonTaintConfigurationSinkMeta.Severity.Error, null), null, id))
            }
        }
        return SpringRuleProvider(delegate, spring)
    }

    private fun options() = TaintAnalyzerOptions(30.seconds, ApMode.Tree, symbolicExecutionEnabled = false)

    private companion object {
        const val SAMPLE = "test.samples.bidi.SpringDispatcherAcceptanceSample"
        const val COMPONENT = "$SAMPLE\$Component"
        val EXPECTED = setOf("direct", "inside-static", "registry")
    }
}

private object AcceptanceEmptyRules : TaintRulesProvider {
    override fun entryPointRulesForMethod(method: CommonMethod, statement: CommonInst, fact: FactAp?, allRelevant: Boolean) = emptyList<TaintEntryPointSource>()
    override fun sourceRulesForMethod(method: CommonMethod, statement: CommonInst, fact: FactAp?, allRelevant: Boolean) = emptyList<TaintMethodSource>()
    override fun exitSourceRulesForMethod(method: CommonMethod, statement: CommonInst, fact: FactAp?, allRelevant: Boolean) = emptyList<TaintMethodExitSource>()
    override fun sinkRulesForMethod(method: CommonMethod, statement: CommonInst, fact: FactAp?, allRelevant: Boolean) = emptyList<TaintMethodSink>()
    override fun sinkRulesForMethodEntry(method: CommonMethod, statement: CommonInst, fact: FactAp?, allRelevant: Boolean) = emptyList<TaintMethodEntrySink>()
    override fun sinkRulesForMethodExit(method: CommonMethod, statement: CommonInst, fact: FactAp?, initialFacts: Set<InitialFactAp>?, allRelevant: Boolean) = emptyList<TaintMethodExitSink>()
    override fun passTroughRulesForMethod(method: CommonMethod, statement: CommonInst?, fact: FactAp?, allRelevant: Boolean) = emptyList<TaintPassThrough>()
    override fun cleanerRulesForMethod(method: CommonMethod, statement: CommonInst, fact: FactAp?, allRelevant: Boolean) = emptyList<TaintCleaner>()
    override fun sourceRulesForStaticField(field: JIRField, statement: CommonInst, fact: FactAp?, allRelevant: Boolean) = emptyList<TaintStaticFieldSource>()
    override fun selectRules(ruleIds: Set<String>) = Unit
}
