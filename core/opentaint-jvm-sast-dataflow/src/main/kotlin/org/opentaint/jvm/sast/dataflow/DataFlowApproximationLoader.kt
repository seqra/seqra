package org.opentaint.jvm.sast.dataflow

import mu.KLogging
import org.opentaint.ir.api.jvm.JIRClasspath
import org.opentaint.ir.api.jvm.JIRClasspathFeature
import org.opentaint.ir.api.jvm.JIRDatabase
import org.opentaint.ir.api.jvm.JIRMethod
import org.opentaint.ir.api.jvm.JIRSettings
import org.opentaint.ir.approximation.Approximations
import org.opentaint.ir.approximation.JIREnrichedVirtualMethod
import org.opentaint.jvm.util.ApproximationPaths
import java.io.File
import java.nio.file.FileSystems
import java.nio.file.Path
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.copyToRecursively
import kotlin.io.path.createTempDirectory

object DataFlowApproximationLoader {
    data class Options(
        val useDataflowApproximation: Boolean = true,
        val useOpentaintApproximations: Boolean = false,
    )

    private const val APPROXIMATIONS_PATH = "/opentaint-dataflow-approximations"

    private val logger = object : KLogging() {}.logger

    @OptIn(ExperimentalPathApi::class)
    private fun unpackApproximations(): Path? = runCatching {
        val resources = javaClass.getResource("/opentaint-dataflow-approximations")
            ?: return null

        val unpacked = createTempDirectory("opentaint-dataflow-approximations")
        FileSystems.newFileSystem(resources.toURI(), emptyMap<String, Any>()).use { fs ->
            val path = fs.getPath(APPROXIMATIONS_PATH)
            path.copyToRecursively(unpacked, followLinks = false, overwrite = false)
        }
        return unpacked
    }.onFailure {
        logger.error(it) { "Error while unpacking approximations" }
    }.getOrNull()

    private val dataflowApproximationsPath: Path? by lazy {
        unpackApproximations()
    }

    private val approximationPaths by lazy {
        ApproximationPaths()
    }

    private fun approximationFiles(options: Options): List<File> {
        val result = mutableListOf<File>()
        if (options.useDataflowApproximation) {
            result += listOfNotNull(dataflowApproximationsPath?.toFile())
        }

        if (options.useOpentaintApproximations) {
            result += approximationPaths.presentPaths.map { File(it) }
        }

        return result
    }

    fun isApproximation(method: JIRMethod): Boolean = method is JIREnrichedVirtualMethod

    fun installApproximations(settings: JIRSettings, options: Options) {
        val approxFiles = approximationFiles(options)
        settings.installFeatures(Approximations(emptyList()))
        settings.loadByteCode(approxFiles)
    }

    suspend fun createCpWithApproximations(
        db: JIRDatabase,
        cp: List<File>,
        features: List<JIRClasspathFeature>,
        options: Options
    ): JIRClasspath {
        val approxFiles = approximationFiles(options)
        val cpWithApproximations = cp + approxFiles

        val approximationsFeature = db.features.filterIsInstance<Approximations>().singleOrNull()
            ?: error("Approximations feature not found in database features")

        val featuresWithApproximations = features + listOf(approximationsFeature)

        return db.classpath(cpWithApproximations, featuresWithApproximations)
    }
}
