package org.opentaint.jvm.sast.dataflow

import org.opentaint.ir.analysis.sarif.SourceFileResolver
import org.opentaint.ir.api.JIRClassOrInterface
import org.opentaint.ir.api.RegisteredLocation
import org.opentaint.ir.api.cfg.JIRInst
import org.opentaint.ir.api.ext.packageName
import org.opentaint.machine.logger
import java.nio.file.Path
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.extension
import kotlin.io.path.relativeTo
import kotlin.io.path.walk

class JIRSourceFileResolver(
    private val projectSourceRoot: Path,
    private val projectLocationsSourceRoots: Map<RegisteredLocation, Path>
) : SourceFileResolver {
    private val locationJavaSources: Map<RegisteredLocation, Map<String, List<Path>>> by lazy {
        projectLocationsSourceRoots.mapValues { (_, sourcesRoot) ->
            @OptIn(ExperimentalPathApi::class)
            val allJavaFiles = sourcesRoot.walk().filter { it.extension == "java" }
            allJavaFiles.toList().groupBy { it.fileName.toString() }
        }
    }

    override fun resolve(inst: JIRInst): String? {
        val instLocationCls = inst.location.method.enclosingClass

        val location = instLocationCls.declaration.location
        if (location.isRuntime) return null

        val javaSources = locationJavaSources[location] ?: return null
        val sourceFileName = classSourceFileName(instLocationCls)
        val relatedSourceFiles = javaSources[sourceFileName].orEmpty()

        val sourceFilesWithCorrectPackage = relatedSourceFiles.filter { packageMatches(it, instLocationCls) }

        if (sourceFilesWithCorrectPackage.size == 1) {
            return sourceFilesWithCorrectPackage.single().relativeTo(projectSourceRoot).toString()
        }

        logger.warn { "Source file was not resolved for: ${instLocationCls.name}" }
        return null
    }

    private fun classSourceFileName(cls: JIRClassOrInterface): String =
        cls.outerClass?.let { classSourceFileName(it) } ?: "${cls.simpleName}.java"

    private fun packageMatches(sourceFile: Path, cls: JIRClassOrInterface): Boolean {
        val packageParts = cls.packageName.split(".").reversed()
        val filePathParts = sourceFile.toList().reversed().drop(1)

        if (filePathParts.size < packageParts.size) return false

        return packageParts.zip(filePathParts).all { it.first == it.second.toString() }
    }
}
