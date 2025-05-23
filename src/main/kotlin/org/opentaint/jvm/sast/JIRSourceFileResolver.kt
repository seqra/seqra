package org.opentaint.jvm.sast.dataflow

import org.opentaint.ir.analysis.sarif.SourceFileResolver
import org.opentaint.ir.api.JIRClassOrInterface
import org.opentaint.ir.api.RegisteredLocation
import org.opentaint.ir.api.cfg.JIRInst
import org.opentaint.ir.api.ext.packageName
import java.nio.file.Path
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.extension
import kotlin.io.path.relativeTo
import kotlin.io.path.walk

class JIRSourceFileResolver(
    private val projectLocations: Set<RegisteredLocation>,
    private val sourcesRoot: Path
) : SourceFileResolver {
    private val allJavaSources: Map<String, List<Path>> by lazy {
        @OptIn(ExperimentalPathApi::class)
        val allJavaFiles = sourcesRoot.walk().filter { it.extension == "java" }
        allJavaFiles.toList().groupBy { it.fileName.toString() }
    }

    override fun resolve(inst: JIRInst): String? {
        val instLocationCls = inst.location.method.enclosingClass

        if (instLocationCls.declaration.location.isRuntime) return null
        if (instLocationCls.declaration.location !in projectLocations) return null

        val sourceFileName = classSourceFileName(instLocationCls)
        val relatedSourceFiles = allJavaSources[sourceFileName].orEmpty()

        val sourceFilesWithCorrectPackage = relatedSourceFiles.filter { packageMatches(it, instLocationCls) }

        if (sourceFilesWithCorrectPackage.size == 1) {
            return sourceFilesWithCorrectPackage.single().relativeTo(sourcesRoot).toString()
        }

        TODO("Source not resolved")
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
