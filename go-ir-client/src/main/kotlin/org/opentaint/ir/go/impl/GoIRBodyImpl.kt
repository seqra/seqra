package org.opentaint.ir.go.impl

import org.opentaint.ir.go.api.GoIRBody
import org.opentaint.ir.go.api.GoIRFunction
import org.opentaint.ir.go.cfg.GoIRBasicBlock
import org.opentaint.ir.go.cfg.GoIRBlockGraph
import org.opentaint.ir.go.cfg.GoIRInstGraph
import org.opentaint.ir.go.inst.GoIRInst
import org.opentaint.ir.go.inst.GoIRInstRef

class GoIRBodyImpl(
    override val function: GoIRFunction,
    override val blocks: List<GoIRBasicBlock>,
    override val recoverBlock: GoIRBasicBlock?,
) : GoIRBody {
    override val instructions: List<GoIRInst> by lazy {
        blocks.flatMap { it.instructions }
    }

    override val blockGraph: GoIRBlockGraph by lazy {
        GoIRBlockGraphImpl(this)
    }

    override val instGraph: GoIRInstGraph by lazy {
        GoIRInstGraphImpl(this)
    }
}
