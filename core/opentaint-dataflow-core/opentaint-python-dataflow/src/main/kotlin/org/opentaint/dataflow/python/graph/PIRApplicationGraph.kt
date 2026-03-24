package org.opentaint.dataflow.python.graph

import org.opentaint.ir.api.python.PIRClasspath
import org.opentaint.ir.api.python.PIRFunction
import org.opentaint.ir.api.python.PIRInstruction
import org.opentaint.util.analysis.ApplicationGraph

class PIRApplicationGraph(
    val cp: PIRClasspath
) : ApplicationGraph<PIRFunction, PIRInstruction> {
    override fun callees(node: PIRInstruction): Sequence<PIRFunction> {
        TODO("Not yet implemented")
    }

    override fun callers(method: PIRFunction): Sequence<PIRInstruction> {
        TODO("Not yet implemented")
    }

    override fun methodOf(node: PIRInstruction): PIRFunction {
        TODO("Not yet implemented")
    }

    override fun methodGraph(method: PIRFunction): ApplicationGraph.MethodGraph<PIRFunction, PIRInstruction> =
        PIRFunctionGraph(method, this)

    class PIRFunctionGraph(
        override val method: PIRFunction,
        override val applicationGraph: ApplicationGraph<PIRFunction, PIRInstruction>,
    ) : ApplicationGraph.MethodGraph<PIRFunction, PIRInstruction> {
        override fun predecessors(node: PIRInstruction): Sequence<PIRInstruction> {
            TODO("Not yet implemented")
        }

        override fun successors(node: PIRInstruction): Sequence<PIRInstruction> {
            TODO("Not yet implemented")
        }

        override fun entryPoints(): Sequence<PIRInstruction> {
            TODO("Not yet implemented")
        }

        override fun exitPoints(): Sequence<PIRInstruction> {
            TODO("Not yet implemented")
        }

        override fun statements(): Sequence<PIRInstruction> {
            TODO("Not yet implemented")
        }
    }
}
