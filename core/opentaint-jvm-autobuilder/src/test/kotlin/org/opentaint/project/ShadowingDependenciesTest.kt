package org.opentaint.project

import java.nio.file.Path
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.io.path.createDirectories
import kotlin.io.path.createTempDirectory
import kotlin.io.path.outputStream
import kotlin.io.path.writeBytes
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

class ShadowingDependenciesTest {

    private val tempDir: Path = createTempDirectory("shadowing-dependencies")

    @AfterTest
    fun cleanup() {
        tempDir.toFile().deleteRecursively()
    }

    private fun classesDir(name: String, vararg classes: String): Path {
        val dir = tempDir.resolve(name)
        for (cls in classes) {
            val file = dir.resolve(cls.replace('.', '/') + ".class")
            file.parent.createDirectories()
            file.writeBytes(byteArrayOf(0xCA.toByte(), 0xFE.toByte(), 0xBA.toByte(), 0xBE.toByte()))
        }
        dir.createDirectories()
        return dir
    }

    private fun jar(name: String, vararg classes: String): Path {
        val path = tempDir.resolve(name)
        path.parent.createDirectories()
        ZipOutputStream(path.outputStream()).use { zip ->
            for (cls in classes) {
                zip.putNextEntry(ZipEntry(cls.replace('.', '/') + ".class"))
                zip.write(byteArrayOf(0xCA.toByte(), 0xFE.toByte(), 0xBA.toByte(), 0xBE.toByte()))
                zip.closeEntry()
            }
        }
        return path
    }

    private fun project(modules: List<Path>, dependencies: List<Path>) = JavaProject(
        sourceRoot = tempDir,
        modules = listOf(ProjectModuleClasses(moduleSourceRoot = tempDir, moduleClasses = modules)),
        dependencies = dependencies,
    )

    @Test
    fun `a dependency republishing project classes is dropped`() {
        val moduleClasses = classesDir("module", "com.example.Dto", "com.example.Service")
        val ownArtifact = jar("example-client-1.0.jar", "com.example.Dto", "com.example.client.Api")
        val thirdParty = jar("guava-33.2.jar", "com.google.common.collect.Lists")

        val filtered = listOf(project(listOf(moduleClasses), listOf(ownArtifact, thirdParty)))
            .dropDependenciesShadowingProjectClasses()

        assertEquals(listOf(thirdParty), filtered.single().dependencies)
    }

    @Test
    fun `a dependency sharing no class is kept`() {
        val moduleClasses = classesDir("module", "com.example.Dto")
        val thirdParty = jar("guava-33.2.jar", "com.google.common.collect.Lists")

        val filtered = listOf(project(listOf(moduleClasses), listOf(thirdParty)))
            .dropDependenciesShadowingProjectClasses()

        assertEquals(listOf(thirdParty), filtered.single().dependencies)
    }

    @Test
    fun `shadowing is judged against every module of every project`() {
        // the jar shadows a class of the *other* project's module, and must be dropped from both
        val moduleA = classesDir("moduleA", "com.example.a.Dto")
        val moduleB = classesDir("moduleB", "com.example.b.Dto")
        val shadowing = jar("bundle-1.0.jar", "com.example.b.Dto", "com.example.bundled.Helper")

        val filtered = listOf(
            project(listOf(moduleA), listOf(shadowing)),
            project(listOf(moduleB), listOf(shadowing)),
        ).dropDependenciesShadowingProjectClasses()

        assertEquals(emptyList(), filtered[0].dependencies)
        assertEquals(emptyList(), filtered[1].dependencies)
    }

    @Test
    fun `module classes packaged as a jar also shadow`() {
        val moduleJar = jar("module-classes.jar", "com.example.Dto")
        val ownArtifact = jar("example-client-1.0.jar", "com.example.Dto")

        val filtered = listOf(project(listOf(moduleJar), listOf(ownArtifact)))
            .dropDependenciesShadowingProjectClasses()

        assertEquals(emptyList(), filtered.single().dependencies)
    }
}
