package org.opentaint.python.sast.dataflow

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.TestInstance
import org.opentaint.dataflow.configuration.python.Argument
import org.opentaint.dataflow.configuration.python.Result
import kotlin.test.Test

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class IntraproceduralFlowTest : AnalysisTest() {

    @Test
    fun testAssignDirect() = assertSinkReachable(
        source = source("AssignmentFlow.source", "taint", Result),
        sink = sink("AssignmentFlow.sink", "taint", Argument(0), "assign"),
        entryPointFunction = "AssignmentFlow.assign_direct"
    )

    @Test
    fun testAssignChain() = assertSinkReachable(
        source = source("AssignmentFlow.source", "taint", Result),
        sink = sink("AssignmentFlow.sink", "taint", Argument(0), "assign"),
        entryPointFunction = "AssignmentFlow.assign_chain"
    )

    @Test
    fun testAssignLongChain() = assertSinkReachable(
        source = source("AssignmentFlow.source", "taint", Result),
        sink = sink("AssignmentFlow.sink", "taint", Argument(0), "assign"),
        entryPointFunction = "AssignmentFlow.assign_long_chain"
    )

    @Test
    fun testAssignOverwrite() = assertSinkNotReachable(
        source = source("AssignmentFlow.source", "taint", Result),
        sink = sink("AssignmentFlow.sink", "taint", Argument(0), "assign"),
        entryPointFunction = "AssignmentFlow.assign_overwrite"
    )

    @Test
    fun testAssignOverwriteOther() = assertSinkReachable(
        source = source("AssignmentFlow.source", "taint", Result),
        sink = sink("AssignmentFlow.sink", "taint", Argument(0), "assign"),
        entryPointFunction = "AssignmentFlow.assign_overwrite_other"
    )

    @Test
    fun testBranchIfTrue() = assertSinkReachable(
        source = source("BranchFlow.source", "taint", Result),
        sink = sink("BranchFlow.sink", "taint", Argument(0), "branch"),
        entryPointFunction = "BranchFlow.branch_if_true"
    )

    @Test
    fun testBranchIfElseBoth() = assertSinkReachable(
        source = source("BranchFlow.source", "taint", Result),
        sink = sink("BranchFlow.sink", "taint", Argument(0), "branch"),
        entryPointFunction = "BranchFlow.branch_if_else_both"
    )

    @Test
    fun testBranchIfElseOne() = assertSinkReachable(
        source = source("BranchFlow.source", "taint", Result),
        sink = sink("BranchFlow.sink", "taint", Argument(0), "branch"),
        entryPointFunction = "BranchFlow.branch_if_else_one"
    )

    @Test
    fun testBranchOverwriteInBranch() = assertSinkReachable(
        source = source("BranchFlow.source", "taint", Result),
        sink = sink("BranchFlow.sink", "taint", Argument(0), "branch"),
        entryPointFunction = "BranchFlow.branch_overwrite_in_branch"
    )

    @Test
    fun testLoopWhileBody() = assertSinkReachable(
        source = source("LoopFlow.source", "taint", Result),
        sink = sink("LoopFlow.sink", "taint", Argument(0), "loop"),
        entryPointFunction = "LoopFlow.loop_while_body"
    )

    @Test
    fun testLoopForBody() = assertSinkReachable(
        source = source("LoopFlow.source", "taint", Result),
        sink = sink("LoopFlow.sink", "taint", Argument(0), "loop"),
        entryPointFunction = "LoopFlow.loop_for_body"
    )
}
