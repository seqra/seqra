package org.opentaint.jvm.sast

import mu.KLogging
import org.opentaint.ir.api.common.cfg.CommonInst
import org.opentaint.ir.api.jvm.JIRClassOrInterface
import org.opentaint.ir.api.jvm.RegisteredLocation
import org.opentaint.ir.api.jvm.cfg.JIRInst
import org.opentaint.ir.api.jvm.ext.packageName
import org.opentaint.dataflow.sarif.SourceFileResolver
import java.nio.file.Path
import kotlin.io.path.extension
import kotlin.io.path.relativeTo
import kotlin.io.path.walk

fun JIRClassOrInterface.mostOuterClass(): JIRClassOrInterface {
    var result = this
    while (true) {
        result = result.outerClass ?: break
    }
    return result
}

class JIRSourceFileResolver(
    private val projectSourceRoot: Path,
    private val projectLocationsSourceRoots: Map<RegisteredLocation, Path>
) : SourceFileResolver<CommonInst> {
    private val locationSources: Map<RegisteredLocation, Map<String, List<Path>>> by lazy {
        projectLocationsSourceRoots.mapValues { (_, sourcesRoot) ->
            val allJavaAndKotlinFiles = sourcesRoot.walk().filter { file ->
                file.extension.let { it == JAVA_EXTENSION || it == KOTLIN_EXTENSION }
            }
            allJavaAndKotlinFiles.toList().groupBy { it.fileName.toString() }
        }
    }

    override fun resolveByName(inst: CommonInst, pkg: String, name: String): String? {
        check(inst is JIRInst) { "Expected inst to be JIRInst" }
        val instLocationCls = inst.location.method.enclosingClass

        val location = instLocationCls.declaration.location
        if (location.isRuntime) return null

        val sources = locationSources[location] ?: return null

        val relatedSourceFiles = sources[name] ?: return null
        val sourceFilesWithCorrectPackage = relatedSourceFiles.filter { packageMatches(it, pkg) }

        if (sourceFilesWithCorrectPackage.size != 1) {
            logger.warn { "Source file was not resolved for: $name" }
            return null
        }

        return sourceFilesWithCorrectPackage[0].relativeTo(projectSourceRoot).toString()
    }

    override fun resolveByInst(inst: CommonInst): String? {
        check(inst is JIRInst) { "Expected inst to be JIRInst" }
        val instLocationCls = inst.location.method.enclosingClass

        val location = instLocationCls.declaration.location
        if (location.isRuntime) return null

        val sources = locationSources[location] ?: return null

        val locationCls = instLocationCls.mostOuterClass()
        // using split for abstract/virtual classes, where continuation after the symbol specifies exact nameless class
        val clsName = locationCls.simpleName.split('$').first()
        val sourceFileNameVariants = mutableListOf<String>()

        if (clsName.endsWith("Kt")) {
            sourceFileNameVariants += clsName.removeSuffix("Kt") + ".$KOTLIN_EXTENSION"
        }

        sourceFileNameVariants += "$clsName.$JAVA_EXTENSION"
        sourceFileNameVariants += "$clsName.$KOTLIN_EXTENSION"

        for (sourceFileName in sourceFileNameVariants) {
            val resolved = tryResolveSourceFile(sources, locationCls, sourceFileName) ?: continue
            return resolved.relativeTo(projectSourceRoot).toString()
        }

        logger.warn { "Source file was not resolved for: ${instLocationCls.name}" }
        return null
    }

    private fun tryResolveSourceFile(
        sources: Map<String, List<Path>>,
        locationCls: JIRClassOrInterface,
        sourceFileName: String
    ): Path? {
        val relatedSourceFiles = sources[sourceFileName] ?: return null
        val sourceFilesWithCorrectPackage = relatedSourceFiles.filter { packageMatches(it, locationCls) }
        return sourceFilesWithCorrectPackage.singleOrNull()
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
