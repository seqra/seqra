package org.opentaint.jvm.sast

import mu.KLogging
import org.opentaint.dataflow.sarif.SourceFileResolver
import org.opentaint.ir.api.common.cfg.CommonInst
import org.opentaint.ir.api.jvm.JIRClassOrInterface
import org.opentaint.ir.api.jvm.RegisteredLocation
import org.opentaint.ir.api.jvm.cfg.JIRInst
import org.opentaint.ir.api.jvm.ext.packageName
import org.opentaint.jvm.sast.ast.JavaClassNameExtractor
import java.io.IOException
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes
import kotlin.io.path.extension
import kotlin.io.path.nameWithoutExtension
import kotlin.io.path.relativeTo

fun JIRClassOrInterface.mostOuterClass(): JIRClassOrInterface {
    var result = this
    while (true) {
        result = result.outerClass ?: break
    }
    return result
}

class JIRSourceFileResolver(
    private val projectSourceRoot: Path?,
    private val projectLocationsSourceRoots: Map<RegisteredLocation, Path>
) : SourceFileResolver<CommonInst> {
    private class SourceLocations(
        val allSourceByFileName: Map<String, List<Path>>,
        val javaLocations: Map<String, List<Path>>,
        val kotlinLocations: Map<String, List<Path>>
    )

    private val locationSources: Map<RegisteredLocation, SourceLocations> by lazy {
        projectLocationsSourceRoots.mapValues { (_, sourcesRoot) ->
            logger.info { "Start source root indexing: $sourcesRoot" }
            collectAllSources(sourcesRoot).also {
                logger.info { "Finish source root indexing: $sourcesRoot" }
            }
        }
    }

    private fun collectAllSources(root: Path): SourceLocations {
        val collectedJava = mutableListOf<Path>()
        val collectedKotlin = mutableListOf<Path>()

        Files.walkFileTree(root, object : SimpleFileVisitor<Path>() {
            override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                val ext = file.extension
                if (ext == JAVA_EXTENSION) {
                    collectedJava.add(file)
                }

                if (ext == KOTLIN_EXTENSION) {
                    collectedKotlin.add(file)
                }
                return FileVisitResult.CONTINUE
            }

            override fun visitFileFailed(file: Path, exc: IOException): FileVisitResult {
                logger.warn { "Skipping inaccessible path: $file (${exc.javaClass.simpleName}: ${exc.message})" }
                return FileVisitResult.SKIP_SUBTREE
            }

            override fun postVisitDirectory(dir: Path?, exc: IOException?): FileVisitResult {
                if (exc != null) {
                    logger.warn { "Skipping inaccessible path: $dir (${exc.javaClass.simpleName}: ${exc.message})" }
                    return FileVisitResult.CONTINUE
                }
                return super.postVisitDirectory(dir, exc)
            }
        })

        val javaLocations = hashMapOf<String, MutableList<Path>>()
        for (jFile in collectedJava) {
            val classNames = JavaClassNameExtractor.extractClassNames(jFile)
            classNames.forEach {
                javaLocations.getOrPut(it, ::mutableListOf).add(jFile)
            }
        }

        val kotlinLocations = collectedKotlin.groupBy { it.nameWithoutExtension }

        val allSourcesByFileName = collectedJava.groupByTo(hashMapOf()) { it.fileName }
        collectedKotlin.groupByTo(allSourcesByFileName) { it.fileName }

        @Suppress("UNCHECKED_CAST")
        return SourceLocations(
            allSourcesByFileName as Map<String, List<Path>>,
            javaLocations,
            kotlinLocations
        )
    }

    override fun relativeToRoot(path: Path): String =
        (projectSourceRoot?.let { path.relativeTo(it) } ?: path).toString()

    private val sourcesCache = hashMapOf<Pair<String, String>, Path?>()
    override fun resolveByName(inst: CommonInst, pkg: String, name: String): Path? =
        sourcesCache.computeIfAbsent(pkg to name) {
            computeByName(inst, pkg, name)
        }

    private fun computeByName(inst: CommonInst, pkg: String, name: String): Path? {
        check(inst is JIRInst) { "Expected inst to be JIRInst" }
        val instLocationCls = inst.location.method.enclosingClass

        val location = instLocationCls.declaration.location
        if (location.isRuntime) return null

        val sources = locationSources[location] ?: return null

        val relatedSourceFiles = sources.allSourceByFileName[name] ?: return null
        val sourceFilesWithCorrectPackage = relatedSourceFiles.filter { packageMatches(it, pkg) }

        if (sourceFilesWithCorrectPackage.size != 1) {
            logger.warn { "Source file was not resolved for: $name" }
            return null
        }

        return sourceFilesWithCorrectPackage[0]
    }

    private val locationsCache = hashMapOf<CommonInst, Path?>()
    override fun resolveByInst(inst: CommonInst): Path? =
        locationsCache.computeIfAbsent(inst) {
            computeByInst(inst)
        }

    private fun computeByInst(inst: CommonInst): Path? {
        check(inst is JIRInst) { "Expected inst to be JIRInst" }
        val instLocationCls = inst.location.method.enclosingClass

        val location = instLocationCls.declaration.location
        if (location.isRuntime) return null

        val sources = locationSources[location] ?: return null

        val mostOuterCls = instLocationCls.mostOuterClass()

        val outerClsPath = sources.tryResolveSourceFileQuery(
            mostOuterCls.simpleName, mostOuterCls, isKotlin = false
        )
        val sourceLocations = when (outerClsPath.size) {
            1 -> outerClsPath
            0 -> {
                val kotlinClsPath = sources.tryResolveSourceFileQuery(
                    mostOuterCls.simpleName, mostOuterCls, isKotlin = true
                )

                when (kotlinClsPath.size) {
                    0 -> sources.tryResolveSourceFileQuery(
                        mostOuterCls.simpleName.removeSuffix("Kt"), mostOuterCls, isKotlin = true
                    )

                    else -> kotlinClsPath
                }
            }

            else -> {
                val innerClassFiles = sources.tryResolveSourceFileQuery(
                    instLocationCls.simpleName, instLocationCls, isKotlin = false
                )
                val intersect = innerClassFiles.filter { it in outerClsPath }
                if (intersect.isEmpty()) outerClsPath else intersect
            }
        }

        if (sourceLocations.isEmpty()) {
            logger.warn { "Source file was not resolved for: ${instLocationCls.name}" }
            return null
        }

        if (sourceLocations.size > 1) {
            logger.warn { "Ambiguous source file for class ${instLocationCls.name}: $sourceLocations" }
        }

        return sourceLocations.firstOrNull()
    }

    private fun SourceLocations.tryResolveSourceFileQuery(
        className: String,
        cls: JIRClassOrInterface,
        isKotlin: Boolean
    ): List<Path> {
        val paths = if (!isKotlin) {
            javaLocations[className]
        } else {
            kotlinLocations[className]
        }

        return paths?.filter { packageMatches(it, cls) }.orEmpty()
    }

    private fun packageMatches(sourceFile: Path, cls: JIRClassOrInterface) =
        packageMatches(sourceFile, cls.packageName.split(".").reversed())

    private fun packageMatches(sourceFile: Path, pkg: String) =
        packageMatches(sourceFile, pkg.split("/").reversed().drop(1))

    private fun packageMatches(sourceFile: Path, parts: List<String>): Boolean {
        val filePathParts = sourceFile.toList().reversed().drop(1)

        if (filePathParts.size < parts.size) return false

        return parts.zip(filePathParts).all { it.first == it.second.toString() }
    }

    companion object {
        private const val JAVA_EXTENSION = "java"
        private const val KOTLIN_EXTENSION = "kt"

        private val logger = object : KLogging() {}.logger
    }
}
