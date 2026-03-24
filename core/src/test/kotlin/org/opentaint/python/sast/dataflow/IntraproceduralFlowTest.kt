package org.opentaint.python.sast.dataflow

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.TestInstance
import org.opentaint.dataflow.configuration.jvm.serialized.PositionBase
import org.opentaint.dataflow.python.rules.TaintRules.Sink
import org.opentaint.dataflow.python.rules.TaintRules.Source
import kotlin.test.Test

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class IntraproceduralFlowTest : AnalysisTest() {

    // --- AssignmentFlow.py ---

    @Test
    fun testAssignDirect() = assertSinkReachable(
        source = Source("AssignmentFlow.source", "taint", PositionBase.Result),
        sink = Sink("AssignmentFlow.sink", "taint", PositionBase.Argument(0), "assign"),
        entryPointFunction = "AssignmentFlow.assign_direct"
    )

    @Test
    fun testAssignChain() = assertSinkReachable(
        source = Source("AssignmentFlow.source", "taint", PositionBase.Result),
        sink = Sink("AssignmentFlow.sink", "taint", PositionBase.Argument(0), "assign"),
        entryPointFunction = "AssignmentFlow.assign_chain"
    )

    @Test
    fun testAssignLongChain() = assertSinkReachable(
        source = Source("AssignmentFlow.source", "taint", PositionBase.Result),
        sink = Sink("AssignmentFlow.sink", "taint", PositionBase.Argument(0), "assign"),
        entryPointFunction = "AssignmentFlow.assign_long_chain"
    )

    @Test
    fun testAssignOverwrite() = assertSinkNotReachable(
        source = Source("AssignmentFlow.source", "taint", PositionBase.Result),
        sink = Sink("AssignmentFlow.sink", "taint", PositionBase.Argument(0), "assign"),
        entryPointFunction = "AssignmentFlow.assign_overwrite"
    )

    @Test
    fun testAssignOverwriteOther() = assertSinkReachable(
        source = Source("AssignmentFlow.source", "taint", PositionBase.Result),
        sink = Sink("AssignmentFlow.sink", "taint", PositionBase.Argument(0), "assign"),
        entryPointFunction = "AssignmentFlow.assign_overwrite_other"
    )

    // --- BranchFlow.py ---

    @Test
    fun testBranchIfTrue() = assertSinkReachable(
        source = Source("BranchFlow.source", "taint", PositionBase.Result),
        sink = Sink("BranchFlow.sink", "taint", PositionBase.Argument(0), "branch"),
        entryPointFunction = "BranchFlow.branch_if_true"
    )

    @Test
    fun testBranchIfElseBoth() = assertSinkReachable(
        source = Source("BranchFlow.source", "taint", PositionBase.Result),
        sink = Sink("BranchFlow.sink", "taint", PositionBase.Argument(0), "branch"),
        entryPointFunction = "BranchFlow.branch_if_else_both"
    )

    @Test
    fun testBranchIfElseOne() = assertSinkReachable(
        source = Source("BranchFlow.source", "taint", PositionBase.Result),
        sink = Sink("BranchFlow.sink", "taint", PositionBase.Argument(0), "branch"),
        entryPointFunction = "BranchFlow.branch_if_else_one"
    )

    @Test
    fun testBranchOverwriteInBranch() = assertSinkReachable(
        source = Source("BranchFlow.source", "taint", PositionBase.Result),
        sink = Sink("BranchFlow.sink", "taint", PositionBase.Argument(0), "branch"),
        entryPointFunction = "BranchFlow.branch_overwrite_in_branch"
    )

    // --- LoopFlow.py ---

    @Test
    fun testLoopWhileBody() = assertSinkReachable(
        source = Source("LoopFlow.source", "taint", PositionBase.Result),
        sink = Sink("LoopFlow.sink", "taint", PositionBase.Argument(0), "loop"),
        entryPointFunction = "LoopFlow.loop_while_body"
    )

    @Test
    fun testLoopForBody() = assertSinkReachable(
        source = Source("LoopFlow.source", "taint", PositionBase.Result),
        sink = Sink("LoopFlow.sink", "taint", PositionBase.Argument(0), "loop"),
        entryPointFunction = "LoopFlow.loop_for_body"
    )
}
