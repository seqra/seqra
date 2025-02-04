package org.opentaint.ir.analysis.engine

import org.opentaint.ir.api.cfg.JIRInst

interface IFDSInstanceListener {
    fun onPropagate(e: IFDSEdge, pred: JIRInst?, factIsNew: Boolean) = Unit
    fun onExitPoint(e: IFDSEdge) = Unit
}