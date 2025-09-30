package org.opentaint.jvm.sast.project

import kotlinx.coroutines.runBlocking
import mu.KLogging
import org.opentaint.ir.api.jvm.JIRClasspath
import org.opentaint.ir.api.jvm.JIRDatabase
import org.opentaint.ir.api.jvm.JIRMethod
import org.opentaint.ir.impl.JIRRamErsSettings
import org.opentaint.ir.impl.features.InMemoryHierarchy
import org.opentaint.ir.impl.features.Usages
import org.opentaint.ir.impl.features.classpaths.UnknownClasses
import org.opentaint.ir.impl.opentaint-ir
import org.opentaint.ir.taint.configuration.v2.TaintConfiguration
import org.opentaint.jvm.sast.dataflow.JIRSourceFileResolver
import org.opentaint.jvm.sast.dataflow.JIRTaintAnalyzer
import org.opentaint.dataflow.ap.ifds.access.ApMode
import org.opentaint.dataflow.jvm.ap.ifds.LambdaAnonymousClassFeature
import org.opentaint.dataflow.jvm.ap.ifds.LambdaExpressionToAnonymousClassTransformerFeature
import org.opentaint.dataflow.jvm.graph.MethodReturnInstNormalizerFeature
import org.opentaint.machine.TypeScorer
import org.opentaint.types.ClassScorer
import org.opentaint.types.scoreClassNode
import org.opentaint.util.ConfigUtils
import java.io.File
import java.nio.file.Path
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
    private val ifdsApMode: ApMode,
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

    private val taintConfig = TaintConfiguration()

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

    private lateinit var db: JIRDatabase
    private lateinit var cp: JIRClasspath
    private lateinit var projectClasses: ProjectClasses

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

            installFeatures(InMemoryHierarchy)
            installFeatures(Usages)
            installFeatures(ClassScorer(TypeScorer, ::scoreClassNode))
//            installFeatures(Approximations)

            loadByteCode(allCpFiles)
        }

        db.awaitBackgroundJobs()
        db.setImmutable()

        ConfigUtils.loadEncrypted(getPathFromEnv("opentaint_taint_config_path")) {
            taintConfig.loadConfig(this)
        }

        val lambdaAnonymousClass = LambdaAnonymousClassFeature()
        val lambdaTransformer = LambdaExpressionToAnonymousClassTransformerFeature(lambdaAnonymousClass)
        val methodNormalizer = MethodReturnInstNormalizerFeature

        val features = listOf(
            UnknownClasses, lambdaAnonymousClass, lambdaTransformer, methodNormalizer
        )

        // todo: fix approximations with multiple JIRDatabase instances
//        cp = db.classpathWithApproximations(allCpFiles, features)
        cp = db.classpath(allCpFiles, features)

        projectClasses = ProjectClasses(cp, projectPackage, projectModulesFiles)
        projectClasses.loadProjectClasses()

        val missedModules = project.modules.toSet() - projectClasses.locationProjectModules.values.toSet()
        if (missedModules.isNotEmpty()) {
            logger.warn {
                "Modules missed for project  ${project.sourceRoot}: ${missedModules.map { it.projectModuleSourceRoot }}"
            }
        }
    }

    private fun getPathFromEnv(envVar: String): Path =
        System.getenv(envVar)?.let { Path(it) } ?: error("$envVar not provided")

    private fun runAnalyzer() {
        val analyzer = JIRTaintAnalyzer(
            cp, taintConfig,
            projectLocations = projectClasses.projectLocations,
            dependenciesLocations = projectClasses.dependenciesLocations,
            ifdsTimeout = ifdsAnalysisTimeout,
            ifdsApMode = ifdsApMode,
            opentaintTimeout = symbolicExecutionTimeout,
            symbolicExecutionEnabled = useSymbolicExecution,
            analysisCwe = cwe.takeIf { it.isNotEmpty() }?.toSet(),
        )

        val sourcesResolver = JIRSourceFileResolver(
            project.sourceRoot,
            projectClasses.locationProjectModules.mapValues { (_, module) -> module.projectModuleSourceRoot }
        )

        logger.info { "Search entry points for project: ${project.sourceRoot}" }
        val entryPoints = allProjectEntryPoints()

        logger.info { "Start IFDS analysis for project: ${project.sourceRoot}" }
        analyzer.analyzeWithIfds(entryPoints)
        logger.info { "Finish IFDS analysis for project: ${project.sourceRoot}" }

        (resultDir / "report-ifds.sarif").outputStream().use {
            analyzer.generateSarifReportFromIfdsTraces(it, sourcesResolver)
        }

        logger.info { "Finish IFDS analysis report for project: ${project.sourceRoot}" }

        if (!useSymbolicExecution) return

        logger.info { "Start Opentaint for project: ${project.sourceRoot}" }
        analyzer.filterIfdsTracesWithOpentaint(entryPoints)
        logger.info { "Finish Opentaint for project: ${project.sourceRoot}" }

        (resultDir / "report-opentaint.sarif").outputStream().use {
            analyzer.generateSarifReportFromVerifiedIfdsTraces(it, sourcesResolver)
        }

        logger.info { "Finish Opentaint report for project: ${project.sourceRoot}" }
    }

    private fun allProjectEntryPoints(): List<JIRMethod> =
        projectClasses.projectPublicClasses()
            .flatMapTo(mutableListOf()) { it.publicAndProtectedMethods() }
            .also {
                it.sortWith(compareBy<JIRMethod> { it.enclosingClass.name }.thenBy { it.name })
            }

    companion object {
        private val logger = object : KLogging() {}.logger
    }
}
