package org.opentaint.python.config

import org.opentaint.config.DefaultConfigLoader
import org.opentaint.dataflow.configuration.ConfigurationLoader
import org.opentaint.dataflow.configuration.python.serialized.PythonConfigurationLoader
import org.opentaint.dataflow.configuration.python.serialized.SerializedPythonTaintConfig

object PythonDefaultConfigLoader : DefaultConfigLoader<SerializedPythonTaintConfig> {
    override val configRoot: String get() = "/model/python/config"
    override val configLoader: ConfigurationLoader<SerializedPythonTaintConfig> get() = PythonConfigurationLoader()
}
