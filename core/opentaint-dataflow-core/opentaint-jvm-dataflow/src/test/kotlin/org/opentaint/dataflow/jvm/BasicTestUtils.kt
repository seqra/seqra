package org.opentaint.dataflow.jvm

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.TestInstance
import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.ap.ifds.access.FactAp
import org.opentaint.dataflow.ap.ifds.access.InitialFactAp
import org.opentaint.dataflow.configuration.jvm.TaintCleaner
import org.opentaint.dataflow.configuration.jvm.TaintEntryPointSource
import org.opentaint.dataflow.configuration.jvm.TaintMethodEntrySink
import org.opentaint.dataflow.configuration.jvm.TaintMethodExitSink
import org.opentaint.dataflow.configuration.jvm.TaintMethodExitSource
import org.opentaint.dataflow.configuration.jvm.TaintMethodSink
import org.opentaint.dataflow.configuration.jvm.TaintMethodSource
import org.opentaint.dataflow.configuration.jvm.TaintPassThrough
import org.opentaint.dataflow.configuration.jvm.TaintStaticFieldSource
import org.opentaint.dataflow.ifds.SingletonUnit
import org.opentaint.dataflow.ifds.UnitType
import org.opentaint.dataflow.ifds.UnknownUnit
import org.opentaint.dataflow.jvm.ap.ifds.JIRCallResolver
import org.opentaint.dataflow.jvm.ap.ifds.JIRLocalAliasAnalysis
import org.opentaint.dataflow.jvm.ap.ifds.JIRLocalAliasAnalysis.AliasAccessor
import org.opentaint.dataflow.jvm.ap.ifds.JIRLocalAliasAnalysis.AliasApInfo
import org.opentaint.dataflow.jvm.ap.ifds.JIRLocalVariableReachability
import org.opentaint.dataflow.jvm.ap.ifds.analysis.JIRAnalysisManager
import org.opentaint.dataflow.jvm.ap.ifds.taint.TaintRulesProvider
import org.opentaint.dataflow.jvm.ifds.JIRUnitResolver
import org.opentaint.ir.api.common.CommonMethod
import org.opentaint.ir.api.common.cfg.CommonInst
import org.opentaint.ir.api.jvm.JIRClasspath
import org.opentaint.ir.api.jvm.JIRDatabase
import org.opentaint.ir.api.jvm.JIRField
import org.opentaint.ir.api.jvm.JIRMethod
import org.opentaint.ir.api.jvm.RegisteredLocation
import org.opentaint.ir.api.jvm.cfg.JIRCallInst
import org.opentaint.ir.api.jvm.cfg.JIRInst
import org.opentaint.ir.api.jvm.cfg.JIRLocalVar
import org.opentaint.ir.api.jvm.cfg.JIRValue
import org.opentaint.ir.impl.JIRRamErsSettings
import org.opentaint.ir.impl.features.InMemoryHierarchy
import org.opentaint.ir.impl.features.Usages
import org.opentaint.ir.impl.features.classpaths.UnknownClasses
import org.opentaint.ir.impl.features.usagesExt
import org.opentaint.ir.impl.opentaintIrDb
import org.opentaint.jvm.graph.JApplicationGraphImpl
import java.nio.file.Path
import kotlin.io.path.Path

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
abstract class BasicTestUtils {
    protected lateinit var samplesJar: Path
    protected lateinit var db: JIRDatabase
    protected lateinit var cp: JIRClasspath

    private val noRules = object : TaintRulesProvider {
        override fun entryPointRulesForMethod(
            method: CommonMethod,
            fact: FactAp?,
            allRelevant: Boolean
        ): Iterable<TaintEntryPointSource> = emptyList()

        override fun sourceRulesForMethod(
            method: CommonMethod,
            statement: CommonInst,
            fact: FactAp?,
            allRelevant: Boolean
        ): Iterable<TaintMethodSource> = emptyList()

        override fun exitSourceRulesForMethod(
            method: CommonMethod,
            statement: CommonInst,
            fact: FactAp?,
            allRelevant: Boolean
        ): Iterable<TaintMethodExitSource> = emptyList()

        override fun sinkRulesForMethod(
            method: CommonMethod,
            statement: CommonInst,
            fact: FactAp?,
            allRelevant: Boolean
        ): Iterable<TaintMethodSink> = emptyList()

        override fun sinkRulesForMethodEntry(
            method: CommonMethod,
            fact: FactAp?,
            allRelevant: Boolean
        ): Iterable<TaintMethodEntrySink> = emptyList()

        override fun sinkRulesForMethodExit(
            method: CommonMethod,
            statement: CommonInst,
            fact: FactAp?,
            initialFacts: Set<InitialFactAp>?,
            allRelevant: Boolean
        ): Iterable<TaintMethodExitSink> = emptyList()

        override fun passTroughRulesForMethod(
            method: CommonMethod,
            statement: CommonInst,
            fact: FactAp?,
            allRelevant: Boolean
        ): Iterable<TaintPassThrough> = emptyList()

        override fun cleanerRulesForMethod(
            method: CommonMethod,
            statement: CommonInst,
            fact: FactAp?,
            allRelevant: Boolean
        ): Iterable<TaintCleaner> = emptyList()

        override fun sourceRulesForStaticField(
            field: JIRField,
            statement: CommonInst,
            fact: FactAp?,
            allRelevant: Boolean
        ): Iterable<TaintStaticFieldSource> = emptyList()
    }

    protected val manager by lazy { JIRAnalysisManager(cp, noRules) }

    @BeforeAll
    fun setup() {
        val jarPath = System.getenv("TEST_SAMPLES_JAR")
            ?: error("TEST_SAMPLES_JAR environment variable not set. Run tests via Gradle.")

        samplesJar = Path(jarPath)

        setupCp()
    }

    protected fun setupCp() = runBlocking {
        db = opentaintIrDb {
            useProcessJavaRuntime()
            persistenceImpl(JIRRamErsSettings)
            installFeatures(InMemoryHierarchy())
            installFeatures(Usages)

            keepLocalVariableNames()

            loadByteCode(listOf(samplesJar.toFile()))
        }

        db.awaitBackgroundJobs()

        cp = db.classpath(listOf(samplesJar.toFile()), listOf(UnknownClasses))
    }

    @AfterAll
    fun tearDown() {
        if (::cp.isInitialized) cp.close()
        if (::db.isInitialized) db.close()
    }

    protected fun findClass(name: String) = cp.findClassOrNull(name)
        ?: error("Class $name not found")

    protected fun findMethod(className: String, methodName: String) =
        findClass(className).declaredMethods.find { it.name == methodName }
            ?: error("Method $methodName not found in $className")

    protected fun aaForMethod(method: JIRMethod): JIRLocalAliasAnalysis {
        val ep = method.instList.first()
        val usages = runBlocking { cp.usagesExt() }
        val graph = JApplicationGraphImpl(cp, usages)

        val callResolver = JIRCallResolver(cp, SingleLocationUnit(method.enclosingClass.declaration.location))
        val localReachability = JIRLocalVariableReachability(method, graph, manager)

        val params = loadSettings(method)

        return JIRLocalAliasAnalysis(ep, graph, callResolver, localReachability, manager, params)
    }

    private fun loadSettings(method: JIRMethod): JIRLocalAliasAnalysis.Params {
        val settings = method.annotations.find { it.name == ALIAS_SETTINGS }
        val callDepth = settings?.values[INTER_PROC_SETTING] as? Int
            ?: return JIRLocalAliasAnalysis.Params()
        return interProcParams(callDepth)
    }

    protected fun interProcParams(depth: Int) =
        JIRLocalAliasAnalysis.Params(useAliasAnalysis = true, aliasAnalysisInterProcCallDepth = depth)

    protected fun JIRMethod.findSinkCall(sinkName: String): JIRCallInst =
        instList.filterIsInstance<JIRCallInst>().first { it.callExpr.method.name == sinkName }

    protected fun JIRLocalAliasAnalysis.valueApAliases(value: JIRValue, stmt: JIRInst): List<AliasApInfo> =
        valueAliases(value, stmt).filterIsInstance<AliasApInfo>()

    protected fun JIRLocalAliasAnalysis.sinkArgApAliases(sink: JIRCallInst): List<AliasApInfo> =
        valueApAliases(sink.callExpr.args[0], sink)

    abstract fun JIRLocalAliasAnalysis.getAliases(
        base: AccessPathBase.LocalVar,
        statement: JIRInst
    ): List<JIRLocalAliasAnalysis.AliasInfo>?

    protected fun JIRLocalAliasAnalysis.valueAliases(
        value: JIRValue,
        stmt: JIRInst
    ): List<JIRLocalAliasAnalysis.AliasInfo> {
        check(value is JIRLocalVar) { "Only local var aliases supported" }
        return getAliases(AccessPathBase.LocalVar(value.index), stmt).orEmpty()
    }

    protected fun AliasApInfo.isPlainBase(expected: AccessPathBase): Boolean =
        accessors.isEmpty() && base == expected

    protected fun AliasAccessor.isField(name: String): Boolean =
        this is AliasAccessor.Field && this.fieldName == name

    protected fun List<AliasAccessor>.singleFieldNamed(name: String): Boolean =
        size == 1 && single().isField(name)

    protected class SingleLocationUnit(val loc: RegisteredLocation) : JIRUnitResolver {
        override fun resolve(method: JIRMethod): UnitType =
            if (method.enclosingClass.declaration.location == loc) SingletonUnit else UnknownUnit

        override fun locationIsUnknown(loc: RegisteredLocation): Boolean = loc != this.loc
    }

    companion object {
        const val ALIAS_SAMPLE_PKG = "sample.alias"
        const val SIMPLE_SAMPLE = "$ALIAS_SAMPLE_PKG.SimpleAliasSample"
        const val LOOP_SAMPLE = "$ALIAS_SAMPLE_PKG.LoopAliasSample"
        const val HEAP_SAMPLE = "$ALIAS_SAMPLE_PKG.HeapAliasSample"
        const val COMBINED_HEAP_SAMPLE = "$ALIAS_SAMPLE_PKG.CombinedHeapAliasSample"
        const val INTERPROC_SAMPLE = "$ALIAS_SAMPLE_PKG.InterProcAliasSample"

        private const val ALIAS_SETTINGS = "sample.AliasSettings"
        private const val INTER_PROC_SETTING = "interProcDepth"

        protected const val FIELD_VALUE = "value"
        protected const val FIELD_BOX = "box"
        protected const val FIELD_NEXT = "next"
        protected const val FIELD_DATA = "data"
        protected const val FIELD_INTERPROC = "field"
    }
}
