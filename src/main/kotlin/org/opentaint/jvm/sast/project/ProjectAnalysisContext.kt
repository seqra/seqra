package org.opentaint.jvm.sast.project

import kotlinx.coroutines.runBlocking
import mu.KLogging
import org.opentaint.ir.api.jvm.JIRClasspath
import org.opentaint.ir.api.jvm.JIRDatabase
import org.opentaint.ir.approximation.Approximations
import org.opentaint.ir.impl.JIRRamErsSettings
import org.opentaint.ir.impl.features.InMemoryHierarchy
import org.opentaint.ir.impl.features.Usages
import org.opentaint.ir.impl.features.classpaths.UnknownClasses
import org.opentaint.ir.impl.opentaint-ir
import org.opentaint.dataflow.ap.ifds.access.ApMode
import org.opentaint.dataflow.jvm.ap.ifds.JIRSummariesFeature
import org.opentaint.dataflow.jvm.ap.ifds.LambdaAnonymousClassFeature
import org.opentaint.dataflow.jvm.ap.ifds.LambdaExpressionToAnonymousClassTransformerFeature
import org.opentaint.dataflow.jvm.graph.MethodReturnInstNormalizerFeature
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
    projectPackage: String?,
    projectKind: ProjectKind,
    summariesApMode: ApMode? = null,
): ProjectAnalysisContext {
    val dependencyFiles by lazy { project.dependencies.map { it.toFile() } }
    val projectModulesFiles by lazy {
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

    var db: JIRDatabase
    var cp: JIRClasspath
    var projectClasses: ProjectClasses
    val classPathExtensionFeature = ProjectClassPathExtensionFeature()

    runBlocking {
        val allCpFiles = mutableListOf<File>()
        allCpFiles.addAll(projectModulesFiles.keys)
        allCpFiles.addAll(dependencyFiles)

        db = opentaint-ir {
            val toolchain = project.javaToolchain
            if (toolchain != null) {
                useJavaRuntime(toolchain.toFile())
            } else {
                useProcessJavaRuntime()
            }

            persistenceImpl(JIRRamErsSettings)

            installFeatures(InMemoryHierarchy)
            installFeatures(Usages)
            keepLocalVariableNames()

            installFeatures(Approximations)

            installClassScorer()
            if (summariesApMode != null) {
                installFeatures(JIRSummariesFeature(summariesApMode))
            }

            loadByteCode(allCpFiles)
        }

        db.awaitBackgroundJobs()

        val lambdaAnonymousClass = LambdaAnonymousClassFeature()
        val lambdaTransformer = LambdaExpressionToAnonymousClassTransformerFeature(lambdaAnonymousClass)
        val methodNormalizer = MethodReturnInstNormalizerFeature

        val features = mutableListOf(
            UnknownClasses, lambdaAnonymousClass, lambdaTransformer, methodNormalizer,
            JStringConcatTransformer, JMultiDimArrayAllocationTransformer,
            classPathExtensionFeature
        )

        if (projectKind == ProjectKind.SPRING_WEB) {
            features.add(SpringReactorOperatorsTransformer)
            features.add(SpringAutowiredFieldInitializerTransformer())
        }

        // todo: fix approximations with multiple JIRDatabase instances
        cp = db.classpathWithApproximations(allCpFiles, features)
            ?: run {
                logger.warn {
                    "Classpath with approximations is requested, but some jar paths are missing"
                }
                db.classpath(allCpFiles, features)
            }
//        cp = db.classpath(allCpFiles, features)

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
                "Modules missed for project  ${project.sourceRoot}: ${missedModules.map { it.moduleSourceRoot }}"
            }
        }
    }

    return ProjectAnalysisContext(
        project, projectPackage, projectKind,
        db, cp, projectClasses
    )
}

class ProjectAnalysisContext(
    val project: Project,
    val projectPackage: String?,
    val projectKind: ProjectKind,
    val db: JIRDatabase,
    val cp: JIRClasspath,
    val projectClasses: ProjectClasses,
): AutoCloseable {
    override fun close() {
        cp.close()
        db.close()
    }
}
