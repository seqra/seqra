package org.opentaint.jvm.sast.project

import org.opentaint.ir.api.jvm.JIRClassOrInterface
import org.opentaint.ir.api.jvm.JIRClasspath
import org.opentaint.ir.api.jvm.JIRMethod
import org.opentaint.ir.api.jvm.RegisteredLocation
import org.opentaint.ir.impl.features.classpaths.JIRUnknownClass
import java.io.File

class ProjectClasses(
    val cp: JIRClasspath,
    private val projectPackage: String?,
    private val projectModulesFiles: Map<File, ProjectResolver.ProjectModuleClasses>
) {
    val locationProjectModules = hashMapOf<RegisteredLocation, ProjectResolver.ProjectModuleClasses>()
    val projectClasses = hashMapOf<RegisteredLocation, MutableSet<String>>()

    val projectLocations: Set<RegisteredLocation>
        get() = projectClasses.keys

    val dependenciesLocations: Set<RegisteredLocation>
        get() = cp.registeredLocations.toHashSet() - projectLocations

    fun loadProjectClasses() {
        cp.registeredLocations.forEach { loadProjectClassesFromLocation(it) }
    }

    private fun loadProjectClassesFromLocation(location: RegisteredLocation) {
        val jirLocation = location.jirLocation ?: return
        val projectModule = projectModulesFiles[jirLocation.jarOrFolder] ?: return
        locationProjectModules[location] = projectModule

        val classes = projectClasses.computeIfAbsent(location) { hashSetOf() }

        val classSources = cp.db.persistence.findClassSources(cp.db, location)
        for (classSource in classSources) {
            val className = classSource.className

            if (projectPackage != null && !className.startsWith(projectPackage)) {
                continue
            }

            classes.add(className)
        }
    }
}

fun ProjectClasses.allProjectClasses(): Sequence<JIRClassOrInterface> =
    projectClasses.values
        .asSequence()
        .flatten()
        .mapNotNull { cp.findClassOrNull(it) }
        .filterNot { it is JIRUnknownClass }

fun ProjectClasses.projectPublicClasses(): Sequence<JIRClassOrInterface> =
    allProjectClasses()
        .filterNot { it.isAbstract || it.isInterface || it.isAnonymous }
        .filter { it.outerClass == null }

fun JIRClassOrInterface.publicAndProtectedMethods(): Sequence<JIRMethod> =
    declaredMethods
        .asSequence()
        .filterNot { it.isAbstract || it.isNative || it.isConstructor || it.isClassInitializer }
        .filter { it.isPublic || it.isProtected }

        // todo: hack to avoid problems with Juliet benchmark
        .filterNot { it.isJulietGeneratedRunner() }

private fun JIRMethod.isJulietGeneratedRunner(): Boolean {
    if (!isStatic || name != "main") return false

    return enclosingClass.name.startsWith("testcases.CWE")
}
