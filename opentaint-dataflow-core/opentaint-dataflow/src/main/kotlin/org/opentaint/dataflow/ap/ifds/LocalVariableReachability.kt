package org.opentaint.dataflow.ap.ifds

import org.opentaint.ir.api.common.cfg.CommonInst

interface LocalVariableReachability {
    fun isReachable(base: AccessPathBase, statement: CommonInst): Boolean
}