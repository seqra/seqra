package org.opentaint.project

import mu.KLogging
import java.nio.file.Path
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.extension
import kotlin.io.path.isDirectory
import kotlin.io.path.isRegularFile
import kotlin.io.path.outputStream
import kotlin.io.path.walk

class ProjectFromCPResolver {
    fun resolveProject(rootDir: Path, resolverWorkDir: Path, projectCp: ProjectFromCP): Project {
        if (projectCp.pkg.isEmpty()) {
            logger.warn { "Project packages not specified. Consider all jar files as project components." }
        }

        val resolvedCp = projectCp.cp.flatMap { resolveProjectCp(it, resolverWorkDir) }
        val module = ProjectModuleClasses(rootDir, projectCp.pkg, resolvedCp)
        return Project(rootDir, projectCp.toolchain, listOf(module))
    }

    private var unpackId = 0

    private fun resolveProjectCp(path: Path, resolverWorkDir: Path): List<Path> {
        if (!path.exists()) {
            logger.warn { "Project path $path not found. Skip" }
            return emptyList()
        }

        if (path.isDirectory()) {
            logger.info { "Project path $path is directory with class files" }
            return listOf(path)
        }

        if (!path.isRegularFile()) {
            logger.warn { "Project path $path is not a file. Skip" }
            return emptyList()
        }

        if (path.extension != "jar") {
            logger.warn { "Project path $path is not a JAR file. Skip" }
            return emptyList()
        }

        try {
            ZipFile(path.toFile()).use { zip ->
                val bootInf = zip.getEntry("BOOT-INF/")
                val webInf = zip.getEntry("WEB-INF/")
                val infEntry = bootInf ?: webInf

                if (infEntry == null) {
                    logger.info { "Project path $path is a simple jar file" }
                    return listOf(path)
                }

                val unpackDir = resolverWorkDir.resolve("unpack_${unpackId++}").createDirectories()
                val classesDir = unpackDir.resolve("classes").createDirectories()
                val libDir = unpackDir.resolve("lib").createDirectories()

                fun extractIfMatches(prefix: String, targetDir: Path, entry: ZipEntry) {
                    if (!entry.name.startsWith(prefix) || entry.isDirectory) return

                    val relative = entry.name.removePrefix(prefix)
                    val targetFile = targetDir.resolve(relative).createParentDirectories()

                    targetFile.outputStream().use { out ->
                        zip.getInputStream(entry).use { input ->
                            input.copyTo(out)
                        }
                    }
                }

                zip.stream().forEach { entry ->
                    extractIfMatches("${infEntry.name}classes/", classesDir, entry)
                    extractIfMatches("${infEntry.name}lib/", libDir, entry)
                }

                val result = mutableListOf<Path>()
                if (classesDir.walk().any { it.isRegularFile() && it.extension == "class" }) {
                    result.add(classesDir)
                }

                libDir.walk().filter { it.isRegularFile() && it.extension == "jar" }.forEach { jar ->
                    result.add(jar)
                }

                if (result.isEmpty()) {
                    logger.warn { "Project path $path is a an empty boot/web jar. Skip" }
                }

                return result
            }
        } catch (ex: Throwable) {
            logger.error(ex) { "Failed to inspect/unpack $path. Skip" }
            return emptyList()
        }
    }

    private val logger = object : KLogging() {}.logger
}
