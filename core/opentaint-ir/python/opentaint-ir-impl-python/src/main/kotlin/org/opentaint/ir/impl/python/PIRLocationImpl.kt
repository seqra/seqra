package org.opentaint.ir.impl.python

import org.opentaint.ir.api.python.PIRFunction
import org.opentaint.ir.api.python.PIRLocation

data class PIRLocationImpl(
    override val method: PIRFunction,
    override val index: Int,
    override val lineNumber: Int = -1,
    override val colOffset: Int = -1,
) : PIRLocation
