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
        val kotlinFileLocations: Map<String, List<Path>>,
        val kotlinClassLocations: Map<String, List<Path>>,
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

            override fun postVisitDirectory(dir: Path, exc: IOException?): FileVisitResult {
                if (exc != null) {
                    logger.warn { "Skipping inaccessible path: $dir (${exc.javaClass.simpleName}: ${exc.message})" }
                    return FileVisitResult.CONTINUE
                }
                return super.postVisitDirectory(dir, exc)
            }
        })

        val javaLocations = hashMapOf<String, MutableList<Path>>()
        for (jFile in collectedJava) {
            val classNames = JavaClassNameExtractor.extractClassNames(jFile, isKotlin = false)
            classNames.forEach {
                javaLocations.getOrPut(it, ::mutableListOf).add(jFile)
            }
        }

        val kotlinClassLocations = hashMapOf<String, MutableList<Path>>()
        val kotlinFileLocations = hashMapOf<String, MutableList<Path>>()

        for (kFile in collectedKotlin) {
            kotlinFileLocations.getOrPut(kFile.nameWithoutExtension, ::mutableListOf).add(kFile)

            val classNames = JavaClassNameExtractor.extractClassNames(kFile, isKotlin = true)
            classNames.forEach {
                kotlinClassLocations.getOrPut(it, ::mutableListOf).add(kFile)
            }
        }

        val allSourcesByFileName = collectedJava.groupByTo(hashMapOf()) { it.fileName.toString() }
        collectedKotlin.groupByTo(allSourcesByFileName) { it.fileName.toString() }

        return SourceLocations(
            allSourcesByFileName,
            javaLocations,
            kotlinFileLocations,
            kotlinClassLocations
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

        val outerClsPath = sources.javaLocations[mostOuterCls.name].orEmpty()
        val sourceLocations = when (outerClsPath.size) {
            1 -> outerClsPath
            0 -> {
                var kotlinClsPath = sources.kotlinClassLocations[mostOuterCls.name].orEmpty()
                if (kotlinClsPath.isEmpty()) {
                    kotlinClsPath = sources.kotlinFileLocations[mostOuterCls.simpleName].orEmpty()
                }
                if (kotlinClsPath.isEmpty()) {
                    kotlinClsPath = sources.kotlinFileLocations[mostOuterCls.simpleName.removeSuffix("Kt")].orEmpty()
                }
                kotlinClsPath
            }

            else -> {
                // note: try to find inner class name
                val classQueryName = "${mostOuterCls.packageName}.${instLocationCls.simpleName}"
                val innerClassFiles = sources.javaLocations[classQueryName].orEmpty()
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
