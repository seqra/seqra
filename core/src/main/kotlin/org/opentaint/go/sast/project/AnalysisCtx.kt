package org.opentaint.go.sast.project

import mu.KLogging
import org.opentaint.go.config.GoDefaultModelLoader
import org.opentaint.ir.go.api.GoIRProgram
import org.opentaint.ir.go.client.GoIRClient
import org.opentaint.ir.go.client.GoIRLoadConfig
import org.opentaint.ir.go.client.GoIRLoadMode
import org.opentaint.project.GoProject
import java.nio.file.Path

class AnalysisCtx(
    private val prj: GoProject,
    val client: GoIRClient,
    private val modelPaths: List<Path> = emptyList(),
    private val useDefaultModels: Boolean = true,
) : AutoCloseable by client {
    val cp: GoIRProgram by lazy {
        logger.info { "Building Go IR for project: ${prj.projectDir}" }
        client.buildFromDir(
            prj.projectDir,
            GoIRLoadConfig(
                mode = GoIRLoadMode.PROJECT,
                modelDirs = defaultModelPaths() + modelPaths,
            ),
        ).program
    }

    private fun defaultModelPaths(): List<Path> =
        if (useDefaultModels) GoDefaultModelLoader.modelPaths() else emptyList()

    companion object {
        private val logger = object : KLogging() {}.logger
    }
}
