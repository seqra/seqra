package org.opentaint.dataflow.configuration.python.serialized

sealed interface PythonTarget {
    data class Function(
        val function: String,
        val signature: SerializedPythonSignatureMatcher? = null,
    ) : PythonTarget

    data class Attribute(
        val attribute: String,
    ) : PythonTarget
}
