package org.opentaint.jvm.sast.project

import bench.EncryptionUtils
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToStream
import mu.KLogging
import org.opentaint.ir.api.jvm.ByteCodeIndexer
import org.opentaint.ir.api.jvm.JIRDBContext
import org.opentaint.ir.api.jvm.JIRClassOrInterface
import org.opentaint.ir.api.jvm.JIRClasspath
import org.opentaint.ir.api.jvm.JIRDatabase
import org.opentaint.ir.api.jvm.JIRFeature
import org.opentaint.ir.api.jvm.JIRMethod
import org.opentaint.ir.api.jvm.JIRSignal
import org.opentaint.ir.api.jvm.RegisteredLocation
import org.opentaint.ir.impl.JIRRamErsSettings
import org.opentaint.ir.impl.features.InMemoryHierarchy
import org.opentaint.ir.impl.features.Usages
import org.opentaint.ir.impl.features.classpaths.JIRUnknownClass
import org.opentaint.ir.impl.features.classpaths.UnknownClasses
import org.opentaint.ir.impl.fs.className
import org.opentaint.ir.impl.opentaint-ir
import org.opentaint.ir.taint.configuration.TaintConfigurationFeature
import org.objectweb.asm.tree.ClassNode
import org.opentaint.jvm.sast.dataflow.JIRSourceFileResolver
import org.opentaint.jvm.sast.dataflow.JIRTaintAnalyzer
import org.opentaint.dataflow.jvm.ap.ifds.LambdaAnonymousClassFeature
import org.opentaint.dataflow.jvm.ap.ifds.LambdaExpressionToAnonymousClassTransformerFeature
import org.opentaint.machine.TypeScorer
import org.opentaint.types.ClassScorer
import org.opentaint.types.scoreClassNode
import java.io.File
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap
import kotlin.io.path.Path
import kotlin.io.path.div
import kotlin.io.path.outputStream
import kotlin.time.Duration

class ProjectAnalyzer(
    private val project: ProjectResolver.Project,
    private val projectPackage: String?,
    private val resultDir: Path,
    private val cwe: List<Int>,
    private val useSymbolicExecution: Boolean,
    private val symbolicExecutionTimeout: Duration,
    private val ifdsAnalysisTimeout: Duration,
    private val debugIfdsSummaryDumpPath: Path?
) {
    fun analyze() {
        initializeCp()

        try {
            runAnalyzer()
        } finally {
            cp.close()
            db.close()
        }
    }

    private val dependencyFiles by lazy { project.dependencies.map { it.toFile() } }
    private val projectModulesFiles by lazy {
        val moduleFiles = mutableMapOf<File, ProjectResolver.ProjectModuleClasses>()
        for (module in project.modules) {
            for (cls in module.projectModuleClasses) {
                if (moduleFiles.putIfAbsent(cls.toFile(), module) != null) {
                    logger.warn("Project class $cls belongs to multiple modules")
                }
            }
        }
        moduleFiles
    }

    private val locationProjectModules = ConcurrentHashMap<RegisteredLocation, ProjectResolver.ProjectModuleClasses>()

    private lateinit var db: JIRDatabase
    private lateinit var cp: JIRClasspath

    private val projectClasses = ConcurrentHashMap<RegisteredLocation, MutableSet<String>>()

    private val projectLocations: Set<RegisteredLocation>
        get() = projectClasses.keys

    private val dependenciesLocations: Set<RegisteredLocation> by lazy {
        cp.registeredLocations.toHashSet() - projectLocations
    }

    private inner class ProjectClassIndexerFeature : JIRFeature<Nothing, Nothing> {
        override fun newIndexer(opentaint-ir: JIRDatabase, location: RegisteredLocation): ByteCodeIndexer =
            ProjectClassIndexer(location)

        override fun onSignal(signal: JIRSignal) {
            // todo: ignore?
        }

        override suspend fun query(classpath: JIRClasspath, req: Nothing): Sequence<Nothing> {
            error("Unexpected operation")
        }
    }

    private inner class ProjectClassIndexer(
        private val location: RegisteredLocation
    ) : ByteCodeIndexer {
        private val projectModule: ProjectResolver.ProjectModuleClasses? by lazy {
            location.jirLocation?.jarOrFolder
                ?.let { projectModulesFiles[it] }
                ?.also { module -> locationProjectModules[location] = module }
        }

        override fun flush(context: JIRDBContext) {
        }

        override fun index(classNode: ClassNode) {
            if (projectModule == null) return

            val className = classNode.name.className
            if (projectPackage != null && !className.startsWith(projectPackage)) return

            val classes = projectClasses.computeIfAbsent(location) { ConcurrentHashMap.newKeySet() }
            classes.add(className)
        }
    }

    private fun initializeCp() = runBlocking {
        val allCpFiles = mutableListOf<File>()
        allCpFiles.addAll(projectModulesFiles.keys)
        allCpFiles.addAll(dependencyFiles)

        db = opentaint-ir {
            when (val toolchain = project.javaToolchain) {
                is JavaToolchain.ConcreteJavaToolchain -> {
                    useJavaRuntime(File(toolchain.javaHome))
                }
                JavaToolchain.DefaultJavaToolchain -> {
                    useProcessJavaRuntime()
                }
            }

            persistenceImpl(JIRRamErsSettings)

            installFeatures(ProjectClassIndexerFeature())
            installFeatures(InMemoryHierarchy)
            installFeatures(Usages)
            installFeatures(ClassScorer(TypeScorer, ::scoreClassNode))
//            installFeatures(Approximations)

            loadByteCode(allCpFiles)
        }

        db.awaitBackgroundJobs()

        val configJson = EncryptionUtils.loadEncrypted(getPathFromEnv("opentaint_taint_config_path")) {
            bufferedReader().readText()
        }

        val lambdaAnonymousClass = LambdaAnonymousClassFeature()
        val lambdaTransformer = LambdaExpressionToAnonymousClassTransformerFeature(lambdaAnonymousClass)

        val configurationFeature = TaintConfigurationFeature.fromJson(configJson)
        val features = listOf(configurationFeature, UnknownClasses, lambdaAnonymousClass, lambdaTransformer)

        // todo: fix approximations with multiple JIRDatabase instances
//        cp = db.classpathWithApproximations(allCpFiles, features)
        cp = db.classpath(allCpFiles, features)

        val missedModules = project.modules.toSet() - locationProjectModules.values.toSet()
        if (missedModules.isNotEmpty()) {
            logger.warn {
                "Modules missed for project  ${project.sourceRoot}: ${missedModules.map { it.projectModuleSourceRoot }}"
            }
        }
    }

    private fun getPathFromEnv(envVar: String): Path =
        System.getenv(envVar)?.let { Path(it) } ?: error("$envVar not provided")

    private val json = Json {
        prettyPrint = true
    }

    @OptIn(ExperimentalSerializationApi::class)
    private fun runAnalyzer() {
        val analyzer = JIRTaintAnalyzer(
            cp,
            ifdsTimeout = ifdsAnalysisTimeout,
            opentaintTimeout = symbolicExecutionTimeout,
            symbolicExecutionEnabled = useSymbolicExecution,
            projectLocations = projectLocations,
            dependenciesLocations = dependenciesLocations,
            analysisCwe = cwe.takeIf { it.isNotEmpty() }?.toSet(),
            debugSummaryDump = debugIfdsSummaryDumpPath,
        )

        val sourcesResolver = JIRSourceFileResolver(
            project.sourceRoot,
            locationProjectModules.mapValues { (_, module) -> module.projectModuleSourceRoot }
        )

        logger.info { "Search entry points for project: ${project.sourceRoot}" }
        val entryPoints = allProjectEntryPoints()

        logger.info { "Start IFDS analysis for project: ${project.sourceRoot}" }
        analyzer.analyzeWithIfds(entryPoints)
        logger.info { "Finish IFDS analysis for project: ${project.sourceRoot}" }

        val ifdsSarifReport = analyzer.generateSarifReportFromIfdsTraces(sourcesResolver)
        (resultDir / "report-ifds.sarif").outputStream().use { json.encodeToStream(ifdsSarifReport, it) }
        logger.info { "Finish IFDS analysis report for project: ${project.sourceRoot}" }

        if (!useSymbolicExecution) return

        logger.info { "Start Opentaint for project: ${project.sourceRoot}" }
        analyzer.filterIfdsTracesWithOpentaint(entryPoints)
        logger.info { "Finish Opentaint for project: ${project.sourceRoot}" }

        val opentaintSarifReport = analyzer.generateSarifReportFromVerifiedIfdsTraces(sourcesResolver)
        (resultDir / "report-opentaint.sarif").outputStream().use { json.encodeToStream(opentaintSarifReport, it) }
        logger.info { "Finish Opentaint report for project: ${project.sourceRoot}" }
    }

    private fun allProjectEntryPoints(): List<JIRMethod> =
        projectPublicClasses()
            .flatMapTo(mutableListOf()) { it.publicAndProtectedMethods() }
            .also {
                it.sortWith(compareBy<JIRMethod> { it.enclosingClass.name }.thenBy { it.name })
            }

    private fun projectPublicClasses(): Sequence<JIRClassOrInterface> =
        projectClasses.values
            .asSequence()
            .flatten()
            .mapNotNull { cp.findClassOrNull(it) }
            .filterNot { it is JIRUnknownClass }
            .filterNot { it.isAbstract || it.isInterface || it.isAnonymous }
            .filter { it.outerClass == null }

    private fun JIRClassOrInterface.publicAndProtectedMethods(): Sequence<JIRMethod> =
        declaredMethods
            .asSequence()
            .filter { it.instList.size > 0 }
            .filter { it.isPublic || it.isProtected }
            .filter { !it.isConstructor }

            // todo: hack to avoid problems with Juliet benchmark
            .filterNot { it.isJulietGeneratedRunner() }

    private fun JIRMethod.isJulietGeneratedRunner(): Boolean {
        if (!isStatic || name != "main") return false

        return enclosingClass.name.startsWith("testcases.CWE")
    }

    companion object {
        private val logger = object : KLogging() {}.logger
    }
}
