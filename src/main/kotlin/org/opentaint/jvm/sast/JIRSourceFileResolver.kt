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

    override fun resolve(inst: CommonInst): String? {
        check(inst is JIRInst) { "Expected inst to be JIRInst" }
        val instLocationCls = inst.location.method.enclosingClass

        val location = instLocationCls.declaration.location
        if (location.isRuntime) return null

        val sources = locationSources[location] ?: return null

        val locationCls = mostOuterClass(instLocationCls)
        val clsName = locationCls.simpleName
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

    private fun mostOuterClass(cls: JIRClassOrInterface): JIRClassOrInterface {
        var result = cls
        while (true) {
            result = result.outerClass ?: break
        }
        return result
    }

    private fun packageMatches(sourceFile: Path, cls: JIRClassOrInterface): Boolean {
        val packageParts = cls.packageName.split(".").reversed()
        val filePathParts = sourceFile.toList().reversed().drop(1)

        if (filePathParts.size < packageParts.size) return false

        return packageParts.zip(filePathParts).all { it.first == it.second.toString() }
    }

    companion object {
        private const val JAVA_EXTENSION = "java"
        private const val KOTLIN_EXTENSION = "kt"

        private val logger = object : KLogging() {}.logger
    }
}
