package org.opentaint.dataflow.jvm.bidi

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.TestInstance
import org.opentaint.dataflow.ap.ifds.EmptyMethodContext
import org.opentaint.dataflow.ap.ifds.MethodContext
import org.opentaint.dataflow.ap.ifds.MethodEntryPoint
import org.opentaint.dataflow.ap.ifds.taint.ExternalMethodTracker
import org.opentaint.dataflow.bidi.ap.ApManager
import org.opentaint.dataflow.bidi.ap.ApOps
import org.opentaint.dataflow.bidi.ap.MethodKey
import org.opentaint.dataflow.ifds.SingletonUnit
import org.opentaint.dataflow.ifds.UnknownUnit
import org.opentaint.dataflow.jvm.ap.ifds.JIRCallResolver
import org.opentaint.dataflow.jvm.ap.ifds.JIRFactTypeChecker
import org.opentaint.dataflow.jvm.ap.ifds.JIRLanguageManager
import org.opentaint.dataflow.jvm.ap.ifds.JIRLocalAliasAnalysis
import org.opentaint.dataflow.jvm.ap.ifds.LambdaAnonymousClassFeature
import org.opentaint.dataflow.jvm.ap.ifds.LambdaExpressionToAnonymousClassTransformerFeature
import org.opentaint.dataflow.jvm.ap.ifds.analysis.JIRMethodEntrypointResolver
import org.opentaint.dataflow.jvm.ap.ifds.taint.TaintRulesProvider
import org.opentaint.dataflow.jvm.bidi.fixtures.NoTaintRules
import org.opentaint.dataflow.jvm.bidi.interp.JIRInterpreter
import org.opentaint.dataflow.jvm.bidi.interp.JIRMethodEntries
import org.opentaint.dataflow.jvm.bidi.interp.PrescanLambdas
import org.opentaint.dataflow.jvm.ifds.JIRUnitResolver
import org.opentaint.dataflow.util.Cancellation
import org.opentaint.ir.api.jvm.JIRClasspath
import org.opentaint.ir.api.jvm.JIRDatabase
import org.opentaint.ir.api.jvm.JIRMethod
import org.opentaint.ir.api.jvm.RegisteredLocation
import org.opentaint.ir.impl.JIRRamErsSettings
import org.opentaint.ir.impl.features.InMemoryHierarchy
import org.opentaint.ir.impl.features.Usages
import org.opentaint.ir.impl.features.classpaths.UnknownClasses
import org.opentaint.ir.impl.features.usagesExt
import org.opentaint.ir.impl.opentaintIrDb
import org.opentaint.jvm.graph.JApplicationGraphImpl
import org.opentaint.jvm.graph.JApplicationSingleExitGraph
import org.opentaint.jvm.graph.JMethodBoundaryInstFeature
import org.opentaint.jvm.graph.JMethodEnterInst
import org.opentaint.jvm.graph.JMethodExitExceptionalInst
import org.opentaint.jvm.graph.JMethodExitNormalInst
import org.opentaint.jvm.transformer.JStringConcatTransformer
import java.nio.file.Path
import kotlin.io.path.Path

/** A separate classpath for the complete bidi facade; legacy test features stay unchanged. */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
abstract class BidiJIRTestUtils {
    protected lateinit var samplesJar: Path
    protected lateinit var db: JIRDatabase
    protected lateinit var cp: JIRClasspath
    protected lateinit var manager: ApManager
    protected lateinit var ops: ApOps
    protected lateinit var languageManager: JIRLanguageManager
    protected lateinit var graph: JApplicationSingleExitGraph
    protected lateinit var checker: JIRFactTypeChecker
    protected lateinit var callResolver: JIRCallResolver
    protected lateinit var epResolver: JIRMethodEntrypointResolver
    protected lateinit var interp: JIRInterpreter
    protected val entries get() = interp.entries
    protected open val transformStringConcat: Boolean = false

    @BeforeAll
    fun setupBidiJIR(): Unit = runBlocking {
        samplesJar = Path(System.getenv("TEST_SAMPLES_JAR")
            ?: error("TEST_SAMPLES_JAR environment variable not set. Run tests via Gradle."))
        db = opentaintIrDb {
            useProcessJavaRuntime()
            persistenceImpl(JIRRamErsSettings)
            installFeatures(InMemoryHierarchy())
            installFeatures(Usages)
            keepLocalVariableNames()
            loadByteCode(listOf(samplesJar.toFile()))
        }
        db.awaitBackgroundJobs()
        val lambdas = LambdaAnonymousClassFeature()
        val features = listOf(UnknownClasses, lambdas,
            LambdaExpressionToAnonymousClassTransformerFeature(lambdas)) +
            (if (transformStringConcat) listOf(JStringConcatTransformer) else emptyList()) +
            JMethodBoundaryInstFeature
        cp = db.classpath(listOf(samplesJar.toFile()), features)
        manager = ApManager(Cancellation().also { it.activate() })
        ops = ApOps(manager)
        languageManager = JIRLanguageManager(cp)
        graph = JApplicationSingleExitGraph(JApplicationGraphImpl(cp, cp.usagesExt()))
        checker = JIRFactTypeChecker(cp)
        val sampleLocation = method("copy").enclosingClass.declaration.location
        val units = object : JIRUnitResolver {
            override fun locationIsUnknown(loc: RegisteredLocation): Boolean = loc != sampleLocation
            override fun resolve(method: JIRMethod) =
                if (method.enclosingClass.declaration.location == sampleLocation ||
                    method.enclosingClass is LambdaAnonymousClassFeature.JIRLambdaClass) SingletonUnit else UnknownUnit
        }
        callResolver = JIRCallResolver(cp, units)
        epResolver = JIRMethodEntrypointResolver(graph)
        interp = interp()
    }

    protected fun interp(
        rules: TaintRulesProvider = NoTaintRules,
        aliasParams: JIRLocalAliasAnalysis.Params = JIRLocalAliasAnalysis.Params(useAliasAnalysis = false),
        prescanLambdas: PrescanLambdas = emptyMap(),
        externalMethodTracker: ExternalMethodTracker? = null,
    ): JIRInterpreter {
        val entries = JIRMethodEntries(graph, languageManager, callResolver, rules, aliasParams,
            manager.cancellation, prescanLambdas)
        return JIRInterpreter(manager, rules, checker, callResolver, epResolver, entries,
            defaultGetModel = null, externalMethodTracker = externalMethodTracker)
    }

    protected fun method(name: String, owner: String = "sample.bidi.BidiStatementSample"): JIRMethod =
        checkNotNull(cp.findClassOrNull(owner)) { "Class $owner not found" }.declaredMethods.single { it.name == name }

    protected fun key(method: JIRMethod, context: MethodContext = EmptyMethodContext): MethodKey =
        MethodEntryPoint(context, method.instList.instructions.filterIsInstance<JMethodEnterInst>().single())

    protected fun normalExit(method: JIRMethod): JMethodExitNormalInst =
        method.instList.instructions.filterIsInstance<JMethodExitNormalInst>().single()

    protected fun exceptionalExit(method: JIRMethod): JMethodExitExceptionalInst =
        method.instList.instructions.filterIsInstance<JMethodExitExceptionalInst>().single()

    @AfterAll
    fun tearDownBidiJIR() {
        if (::cp.isInitialized) cp.close()
        if (::db.isInitialized) db.close()
    }
}
