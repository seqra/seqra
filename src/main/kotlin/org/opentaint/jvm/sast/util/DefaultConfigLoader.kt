package org.opentaint.jvm.sast.util

import org.opentaint.dataflow.configuration.jvm.serialized.SerializedTaintConfig
import org.opentaint.dataflow.configuration.jvm.serialized.loadSerializedTaintConfig
import java.nio.file.Path
import kotlin.io.path.Path

private fun getPathFromEnv(envVar: String): Path =
    System.getenv(envVar)?.let { Path(it) } ?: error("$envVar not provided")

fun loadDefaultConfig(): SerializedTaintConfig =
    ConfigUtils.loadEncrypted(getPathFromEnv("opentaint_taint_config_path")) {
        loadSerializedTaintConfig(this)
    }
