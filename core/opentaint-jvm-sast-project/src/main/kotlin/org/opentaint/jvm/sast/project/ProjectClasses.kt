package org.opentaint.jvm.sast.project

import org.opentaint.ir.api.jvm.JIRClassOrInterface
import org.opentaint.ir.api.jvm.JIRClasspath
import org.opentaint.ir.api.jvm.JIRMethod
import org.opentaint.ir.api.jvm.RegisteredLocation
import org.opentaint.ir.api.jvm.cfg.JIRRawLineNumberInst
import org.opentaint.ir.impl.features.classpaths.JIRUnknownClass
import org.opentaint.project.ProjectModuleClasses
import java.io.File

class ProjectClasses(projectModulesFiles: Map<File, ProjectModuleClasses>) {
    private val projectModulePaths = projectModulesFiles.mapKeys { it.key.absolutePath }

    val locationProjectModules = hashMapOf<RegisteredLocation, ProjectModuleClasses>()
    val projectClasses = hashMapOf<RegisteredLocation, MutableSet<String>>()

    val projectLocationsUnsafe: Set<RegisteredLocation>
        get() = projectClasses.keys

    private var _cp: JIRClasspath? = null

    fun initCp(cp: JIRClasspath) {
        _cp = cp
    }

    val cp: JIRClasspath
        get() = _cp ?: error("Class path not initialized")

    fun isProjectLocation(loc: RegisteredLocation): Boolean =
        locationProjectModules.containsKey(loc)

    fun isProjectClass(cls: JIRClassOrInterface): Boolean {
        val module = locationProjectModules[cls.declaration.location] ?: return false
        return isModuleClass(cls.name, module)
    }

    fun loadProjectClasses() {
        cp.registeredLocations.forEach { loadProjectClassesFromLocation(it) }
    }

    private fun loadProjectClassesFromLocation(location: RegisteredLocation) {
        if (location.isRuntime) return
        val jIRLocation = location.jIRLocation ?: return

        val projectModule = projectModulePaths[jIRLocation.path] ?: return
        locationProjectModules[location] = projectModule

        val classes = projectClasses.computeIfAbsent(location) { hashSetOf() }

        val classSources = cp.db.persistence.findClassSources(cp.db, location)
        for (classSource in classSources) {
            val className = classSource.className

            if (!isModuleClass(className, projectModule)) continue

            classes.add(className)
        }
    }

    private fun isModuleClass(className: String, module: ProjectModuleClasses): Boolean {
        if (module.packages.isEmpty()) return true
        return module.packages.any { className.startsWith(it) }
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

fun ProjectClasses.projectAllAnalyzableClasses(): Sequence<JIRClassOrInterface> =
    allProjectClasses()
        .filterNot { it.isInterface || it.isAnonymous }

fun JIRClassOrInterface.publicAndProtectedMethods(): Sequence<JIRMethod> =
    declaredMethods
        .asSequence()
        .filterNot { it.isAbstract || it.isNative || it.isClassInitializer }
        .filter { it.isPublic || it.isProtected }

        // todo: hack to avoid problems with Juliet benchmark
        .filterNot { it.isJulietGeneratedRunner() }

fun JIRClassOrInterface.allAnalyzableMethods(): Sequence<JIRMethod> =
    declaredMethods
        .asSequence()
        .filterNot { it.isNative || it.isClassInitializer }

fun JIRClassOrInterface.getMethodFromLineNumber(lineNumber: Int): JIRMethod? =
    declaredMethods.firstOrNull { md ->
        md.rawInstList.filterIsInstance<JIRRawLineNumberInst>().any { it.lineNumber == lineNumber }
    }

private fun JIRMethod.isJulietGeneratedRunner(): Boolean {
    if (!isStatic || name != "main") return false

    return enclosingClass.name.startsWith("testcases.CWE")
}
