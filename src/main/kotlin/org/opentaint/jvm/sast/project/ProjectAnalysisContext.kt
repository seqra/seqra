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
import org.opentaint.dataflow.ap.ifds.access.ApMode
import org.opentaint.dataflow.jvm.ap.ifds.JIRSummariesFeature
import org.opentaint.dataflow.jvm.ap.ifds.LambdaAnonymousClassFeature
import org.opentaint.dataflow.jvm.ap.ifds.LambdaExpressionToAnonymousClassTransformerFeature
import org.opentaint.dataflow.jvm.graph.MethodReturnInstNormalizerFeature
import org.opentaint.machine.TypeScorer
import org.opentaint.dataflow.jvm.graph.transformers.JIRMultiDimArrayAllocationTransformer
import org.opentaint.dataflow.jvm.graph.transformers.JIRStringConcatTransformer
import org.opentaint.types.ClassScorer
import org.opentaint.types.scoreClassNode
import org.opentaint.util.ConfigUtils
import java.io.File
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.time.Duration

abstract class AbstractProjectAnalyzer(
    protected val project: ProjectResolver.Project,
    private val projectPackage: String?,
    protected val ifdsAnalysisTimeout: Duration,
    protected val ifdsApMode: ApMode,
    private val projectKind: ProjectKind
) {
    fun analyze() {
        initializeCp()
        val entryPoints = getEntryPoints()

        try {
            runAnalyzer(entryPoints)
        } finally {
            cp.close()
            db.close()
        }
    }

    protected val taintConfig = TaintConfiguration()

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
    protected lateinit var cp: JIRClasspath
    protected lateinit var projectClasses: ProjectClasses
    private val classPathExtensionFeature = ProjectClassPathExtensionFeature()

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
            installFeatures(JIRSummariesFeature(ifdsApMode))
            installFeatures(ClassScorer(TypeScorer, ::scoreClassNode))
//            installFeatures(Approximations)

            loadByteCode(allCpFiles)
        }

        db.awaitBackgroundJobs()

        ConfigUtils.loadEncrypted(getPathFromEnv("opentaint_taint_config_path")) {
            taintConfig.loadConfig(this)
        }

        val lambdaAnonymousClass = LambdaAnonymousClassFeature()
        val lambdaTransformer = LambdaExpressionToAnonymousClassTransformerFeature(lambdaAnonymousClass)
        val methodNormalizer = MethodReturnInstNormalizerFeature

        val features = mutableListOf(
            UnknownClasses, lambdaAnonymousClass, lambdaTransformer, methodNormalizer,
            JIRStringConcatTransformer, JIRMultiDimArrayAllocationTransformer,
            classPathExtensionFeature
        )

        if (projectKind == ProjectKind.SPRING_WEB) {
            features.add(SpringReactorOperatorsTransformer)
            features.add(SpringAutowiredFieldInitializerTransformer())
        }

        // todo: fix approximations with multiple JIRDatabase instances
//        cp = db.classpathWithApproximations(allCpFiles, features)
        cp = db.classpath(allCpFiles, features)

        projectClasses = ProjectClasses(cp, projectPackage, projectModulesFiles)
        projectClasses.loadProjectClasses()

        if (projectKind == ProjectKind.SPRING_WEB) {
            cp.features?.filterIsInstance<SpringAutowiredFieldInitializerTransformer>()?.forEach {
                it.init(projectClasses)
            }
        }

        val missedModules = project.modules.toSet() - projectClasses.locationProjectModules.values.toSet()
        if (missedModules.isNotEmpty()) {
            logger.warn {
                "Modules missed for project  ${project.sourceRoot}: ${missedModules.map { it.projectModuleSourceRoot }}"
            }
        }
    }

    private fun getPathFromEnv(envVar: String): Path =
        System.getenv(envVar)?.let { Path(it) } ?: error("$envVar not provided")

    protected abstract fun runAnalyzer(entryPoints: List<JIRMethod>)

    private fun getEntryPoints(): List<JIRMethod> {
        logger.info { "Search entry points for project: ${project.sourceRoot}" }
        return when (projectKind) {
            ProjectKind.UNKNOWN -> allProjectEntryPoints()
            ProjectKind.SPRING_WEB -> projectClasses.springWebProjectEntryPoints(cp)
        }
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
