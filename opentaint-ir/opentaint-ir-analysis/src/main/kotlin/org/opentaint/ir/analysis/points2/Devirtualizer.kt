package org.opentaint.ir.analysis.points2

import org.opentaint.ir.api.JIRMethod
import org.opentaint.ir.api.cfg.JIRInst

interface Devirtualizer {
    /**
     * Returns all methods that could be called at sink statement.
     */
    // TODO: add source to signature
    fun findPossibleCallees(sink: JIRInst): Collection<JIRMethod>
}