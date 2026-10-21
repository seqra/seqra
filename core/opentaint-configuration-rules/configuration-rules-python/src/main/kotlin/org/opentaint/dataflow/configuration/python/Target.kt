package org.opentaint.dataflow.configuration.python

import org.opentaint.ir.api.python.PIRFunction

sealed interface Target {
    data class Function(val method: PIRFunction) : Target

    data class Attribute(val name: String) : Target
}
