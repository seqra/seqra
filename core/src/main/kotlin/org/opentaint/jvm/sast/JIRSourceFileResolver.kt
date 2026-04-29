package org.opentaint.jvm.sast

import mu.KLogging
import org.opentaint.ir.api.common.cfg.CommonInst
import org.opentaint.ir.api.jvm.JIRClassOrInterface
import org.opentaint.ir.api.jvm.RegisteredLocation
import org.opentaint.ir.api.jvm.cfg.JIRInst
import org.opentaint.ir.api.jvm.ext.packageName
import org.opentaint.jvm.sast.ast.ClassIndex
import org.opentaint.jvm.sast.ast.JavaClassNameIndexer
import org.opentaint.jvm.sast.ast.KotlinClassNameIndexer
import java.io.IOException
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes
import kotlin.io.path.extension
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
) {
    data class SourceLocation(val path: Path, val language: ClassIndex.Language)

    private class SourceLocations(
        val javaIndex: JavaClassNameIndexer.JavaClassIndex,
        val kotlinIndex: KotlinClassNameIndexer.KotlinClassIndex,
    )

    private val locationSources: Map<RegisteredLocation, SourceLocations> by lazy {
        val allSourceRoots = projectLocationsSourceRoots.mapTo(hashSetOf()) { it.value }
        val indexedRoots = allSourceRoots.associateWith { sourcesRoot ->
            logger.info { "Start source root indexing: $sourcesRoot" }
            collectAllSources(sourcesRoot).also {
                logger.info { "Finish source root indexing: $sourcesRoot" }
            }
        }

        projectLocationsSourceRoots.mapValues { (_, sourcesRoot) ->
            indexedRoots.getValue(sourcesRoot)
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

        val javaIndex = JavaClassNameIndexer.createIndex(collectedJava)
        val kotlinIndex = KotlinClassNameIndexer.createIndex(collectedKotlin)

        val allSourcesByFileName = collectedJava.groupByTo(hashMapOf()) { it.fileName.toString() }
        collectedKotlin.groupByTo(allSourcesByFileName) { it.fileName.toString() }

        return SourceLocations(javaIndex, kotlinIndex)
    }

    fun relativeToRoot(path: Path): String =
        (projectSourceRoot?.let { path.relativeTo(it) } ?: path).toString()

    private val sourcesCache = hashMapOf<Pair<String, String>, SourceLocation?>()
    fun resolveKotlinByName(inst: CommonInst, jvmClassName: String, fileName: String): SourceLocation? =
        sourcesCache.computeIfAbsent(jvmClassName to fileName) {
            computeKotlinByName(inst, jvmClassName, fileName)
        }

    private fun computeKotlinByName(inst: CommonInst, jvmClassName: String, fileName: String): SourceLocation? {
        check(inst is JIRInst) { "Expected inst to be JIRInst" }
        val instLocationCls = inst.location.method.enclosingClass

        val location = instLocationCls.declaration.location
        if (location.isRuntime) return null

        val sources = locationSources[location] ?: return null

        var relatedSourceFiles = sources.kotlinIndex.fileNameLocations[fileName] ?: return null
        if (relatedSourceFiles.isEmpty()) return null

        if (relatedSourceFiles.size == 1) {
            return SourceLocation(relatedSourceFiles.first(), ClassIndex.Language.Kotlin)
        }

        val className = jvmClassName.replace('/', '.')
        val lookupResult = sources.kotlinIndex.lookup(className)

        if (lookupResult != null) {
            val intersect = relatedSourceFiles.filterTo(mutableSetOf()) { it in lookupResult.sources }
            if (intersect.isNotEmpty()) {
                relatedSourceFiles = intersect
            }
        }

        if (relatedSourceFiles.size > 1) {
            logger.warn { "Ambiguous source file for class ${jvmClassName}: $relatedSourceFiles" }
        }

        return relatedSourceFiles.firstOrNull()
            ?.let { SourceLocation(it, ClassIndex.Language.Kotlin) }
    }

    private val locationsCache = hashMapOf<CommonInst, SourceLocation?>()
    fun resolveByInst(inst: CommonInst): SourceLocation? =
        locationsCache.computeIfAbsent(inst) {
            computeByInst(inst)
        }

    private fun computeByInst(inst: CommonInst): SourceLocation? {
        check(inst is JIRInst) { "Expected inst to be JIRInst" }
        val instLocationCls = inst.location.method.enclosingClass

        val location = instLocationCls.declaration.location
        if (location.isRuntime) return null

        val sources = locationSources[location] ?: return null

        val mostOuterCls = instLocationCls.mostOuterClass()

        val bestMatch = selectSourceLookup(listOf(sources.javaIndex, sources.kotlinIndex), mostOuterCls)

        var matchedSources = bestMatch?.second?.sources
        if (matchedSources.isNullOrEmpty()) {
            logger.warn { "Source file was not resolved for: ${instLocationCls.name}" }
            return null
        }

        val sourceIndex = bestMatch!!.first

        if (matchedSources.size > 1 && instLocationCls.simpleName != mostOuterCls.simpleName) {
            // note: try to find inner class name
            val classQueryName = "${mostOuterCls.packageName}.${instLocationCls.simpleName}"
            val innerClassFiles = sourceIndex.lookup(classQueryName)?.sources.orEmpty()

            val intersect = innerClassFiles.filterTo(mutableSetOf()) { it in matchedSources }
            if (intersect.isNotEmpty()) {
                matchedSources = intersect
            }
        }

        if (matchedSources.size > 1) {
            logger.warn { "Ambiguous source file for class ${instLocationCls.name}: $matchedSources" }
        }

        return matchedSources.firstOrNull()?.let { SourceLocation(it, sourceIndex.language) }
    }

    private fun selectSourceLookup(
        indices: List<ClassIndex>,
        cls: JIRClassOrInterface
    ): Pair<ClassIndex, ClassIndex.LookupResult>? {
        var bestLookup: Pair<ClassIndex, ClassIndex.LookupResult>? = null
        for (index in indices) {
            val lookup = index.lookup(cls.name)
                ?: continue

            if (lookup.priority == 0) return index to lookup
            if (bestLookup == null || bestLookup.second.priority > lookup.priority) {
                bestLookup = index to lookup
            }
        }
        return bestLookup
    }

    companion object {
        private const val JAVA_EXTENSION = "java"
        private const val KOTLIN_EXTENSION = "kt"

        private val logger = object : KLogging() {}.logger
    }
}
