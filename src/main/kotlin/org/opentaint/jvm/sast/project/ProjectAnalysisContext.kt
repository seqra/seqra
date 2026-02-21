package org.opentaint.jvm.sast.project

import kotlinx.coroutines.runBlocking
import mu.KLogging
import org.opentaint.dataflow.jvm.ap.ifds.JIRSummariesFeature
import org.opentaint.dataflow.jvm.ap.ifds.LambdaAnonymousClassFeature
import org.opentaint.dataflow.jvm.ap.ifds.LambdaExpressionToAnonymousClassTransformerFeature
import org.opentaint.ir.api.jvm.JIRClasspath
import org.opentaint.ir.api.jvm.JIRDatabase
import org.opentaint.ir.api.jvm.JIRSettings
import org.opentaint.ir.api.jvm.ext.JAVA_OBJECT
import org.opentaint.ir.approximation.Approximations
import org.opentaint.ir.impl.JIRRamErsSettings
import org.opentaint.ir.impl.features.InMemoryHierarchy
import org.opentaint.ir.impl.features.Usages
import org.opentaint.ir.impl.features.classpaths.JIRUnknownClass
import org.opentaint.ir.impl.features.classpaths.UnknownClasses
import org.opentaint.ir.impl.opentaintIrDb
import org.opentaint.jvm.sast.project.spring.SpringWebProjectContext
import org.opentaint.jvm.sast.project.spring.createSpringProjectContext
import org.opentaint.jvm.transformer.JMultiDimArrayAllocationTransformer
import org.opentaint.jvm.transformer.JStringConcatTransformer
import org.opentaint.jvm.util.classpathWithApproximations
import org.opentaint.jvm.util.types.installClassScorer
import org.opentaint.project.Project
import org.opentaint.project.ProjectModuleClasses
import java.io.File

private val logger = object : KLogging() {}.logger

fun initializeProjectAnalysisContext(
    project: Project,
    options: ProjectAnalysisOptions
): ProjectAnalysisContext = initializeProjectAnalysisContextUtil(project, options) {
    val cpFiles = dependencyFiles + projectModulesFiles.keys
    createAnalysisContextWithCp(project, cpFiles)
}

fun initializeProjectModulesAnalysisContexts(
    project: Project,
    options: ProjectAnalysisOptions
): List<Pair<ProjectModuleClasses, ProjectAnalysisContext>> =
    initializeProjectAnalysisContextUtil(project, options) {
        projectModulesFiles.map { (file, module) ->
            val cpFiles = dependencyFiles + file
            val moduleProject = project.copy(modules = listOf(module))
            val analysisCtx = createAnalysisContextWithCp(moduleProject, cpFiles)
            module to analysisCtx
        }
    }

private fun <T> initializeProjectAnalysisContextUtil(
    project: Project,
    options: ProjectAnalysisOptions,
    createAnalysisContext: AnalysisContextBuilder.() -> T
): T {
    val dependencyFiles = project.dependencies.map { it.toFile() }
    val projectModulesFiles = run {
        val moduleFiles = mutableMapOf<File, ProjectModuleClasses>()
        for (module in project.modules) {
            for (cls in module.moduleClasses) {
                if (moduleFiles.putIfAbsent(cls.toFile(), module) != null) {
                    logger.warn("Project class $cls belongs to multiple modules")
                }
            }
        }
        moduleFiles
    }

    val settings = JIRSettings().apply {
        val toolchain = project.javaToolchain
        if (toolchain != null) {
            useJavaRuntime(toolchain.toFile())
        } else {
            useProcessJavaRuntime()
        }

        persistenceImpl(JIRRamErsSettings)

        installFeatures(InMemoryHierarchy())
        installFeatures(Usages)
        keepLocalVariableNames()

        installFeatures(Approximations(emptyList()))

        installClassScorer()

        options.summariesApMode?.let {
            installFeatures(JIRSummariesFeature(it))
        }

        loadByteCode(dependencyFiles)
        loadByteCode(projectModulesFiles.keys.toList())
    }

    val db: JIRDatabase
    runBlocking {
        db = opentaintIrDb(settings)
        db.awaitBackgroundJobs()
    }

    val builder = AnalysisContextBuilder(db, settings, dependencyFiles, projectModulesFiles, options)
    return builder.createAnalysisContext()
}

private data class AnalysisContextBuilder(
    val db: JIRDatabase,
    val settings: JIRSettings,
    val dependencyFiles: List<File>,
    val projectModulesFiles: Map<File, ProjectModuleClasses>,
    val options: ProjectAnalysisOptions,
)

private fun AnalysisContextBuilder.createAnalysisContextWithCp(
    project: Project,
    cpFiles: List<File>
): ProjectAnalysisContext {
    val (cp, projectClasses) = initializeCp(db, settings, projectModulesFiles, cpFiles)
    return createAnalysisContext(project, db, cp, projectClasses, options)
}

private fun createAnalysisContext(
    project: Project,
    db: JIRDatabase,
    cp: JIRClasspath,
    projectClasses: ProjectClasses,
    options: ProjectAnalysisOptions
): ProjectAnalysisContext {
    val missedModules = project.modules.toSet() - projectClasses.locationProjectModules.values.toSet()
    if (missedModules.isNotEmpty()) {
        logger.warn {
            "Modules missed for project  ${project.sourceRoot}: ${missedModules.map { it.moduleSourceRoot }}"
        }
    }

    val springContext = projectClasses.createSpringProjectContext()

    return ProjectAnalysisContext(
        project, options.projectKind, db,
        cp, projectClasses, springContext
    )
}

private fun initializeCp(
    db: JIRDatabase,
    settings: JIRSettings,
    projectModulesFiles: Map<File, ProjectModuleClasses>,
    allCpFiles: List<File>
): Pair<JIRClasspath, ProjectClasses> {
    val projectClasses = ProjectClasses(projectModulesFiles)
    val classPathExtensionFeature = ProjectClassPathExtensionFeature()

    val lambdaAnonymousClass = LambdaAnonymousClassFeature()
    val lambdaTransformer = LambdaExpressionToAnonymousClassTransformerFeature(lambdaAnonymousClass)
//        val methodNormalizer = MethodReturnInstNormalizerFeature

    val features = mutableListOf(
        KotlinInlineFunctionScopeTransformer,
        UnknownClasses, lambdaAnonymousClass, lambdaTransformer, /*methodNormalizer,*/
        JStringConcatTransformer, JMultiDimArrayAllocationTransformer,
        classPathExtensionFeature,
        JavaPropertiesResolveTransformer(projectClasses)
    )

//        note: reactor operators special handling has no reasons for now
//        features.add(SpringReactorOperatorsTransformer)


    val cp: JIRClasspath
    runBlocking {
        cp = db.classpathWithApproximations(allCpFiles, features)
            ?: run {
                logger.warn {
                    "Classpath with approximations is requested, but some jar paths are missing"
                }
                db.classpath(allCpFiles, features)
            }
//        cp = db.classpath(allCpFiles, features)
    }

    cp.validate(settings)

    projectClasses.initCp(cp)
    projectClasses.loadProjectClasses()

    return cp to projectClasses
}

private fun JIRClasspath.validate(settings: JIRSettings) {
    val objectCls = findClassOrNull(JAVA_OBJECT)
    if (objectCls == null || objectCls is JIRUnknownClass) {
        logger.error { "Invalid JDK ${settings.jre}. Analysis result may be incorrect" }
    }
}

class ProjectAnalysisContext(
    val project: Project,
    val projectKind: ProjectKind,
    val db: JIRDatabase,
    val cp: JIRClasspath,
    val projectClasses: ProjectClasses,
    val springWebProjectContext: SpringWebProjectContext?
): AutoCloseable {
    override fun close() {
        cp.close()
        db.close()
    }
}
