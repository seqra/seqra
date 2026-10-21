package org.opentaint.python.sast.dataflow

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.TestInstance
import org.opentaint.dataflow.configuration.python.Argument
import org.opentaint.dataflow.configuration.python.Result
import kotlin.test.Test

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ClosureAdapterFlowTest : AnalysisTest() {
    private fun reach(ep: String) = assertSinkReachable(
        source = source("ClosureAdapterFlow.source", "taint", Result),
        sink = sink("ClosureAdapterFlow.sink", "taint", Argument(0), "closure-adapter"),
        entryPointFunction = "ClosureAdapterFlow.$ep"
    )

    @Test fun dictScalar() = reach("dict_scalar")
    @Test fun dictObjField() = reach("dict_obj_field")
    @Test fun ctorField() = reach("ctor_field")
    @Test fun ctorDictObjField() = reach("ctor_dict_obj_field")
    @Disabled("Instance call self binding is not supported")
    @Test fun fullChain() = reach("full_chain")
    @Test fun fullChainDirectImpl() = reach("full_chain_direct_impl")
    @Test fun fullChainExplicitCall() = reach("full_chain_explicit_call")
    @Test fun callableArg() = reach("callable_arg")
    @Disabled("Instance call self binding is not supported")
    @Test fun callableField() = reach("callable_field")
    @Test fun methodField() = reach("method_field")
    @Test fun d2Intra() = reach("d2_intra")
    @Test fun d2OneCall() = reach("d2_one_call")
    @Test fun d2OneCallStoreAfter() = reach("d2_one_call_store_after")
    @Test fun d2TwoCallsDirectStore() = reach("d2_two_calls_direct_store")
    @Test fun fwdDepth2NoctorCopy() = reach("fwd_depth2_noctor_copy")
    @Test fun fwdDepth2NoctorCalleeCopy() = reach("fwd_depth2_noctor_callee_copy")
    @Test fun fwdDepth2PlainFn() = reach("fwd_depth2_plain_fn")
    @Test fun fwdDepth2() = reach("fwd_depth2")
    @Test fun fwdDict() = reach("fwd_dict")
    @Test fun fwdDepth2Noctor() = reach("fwd_depth2_noctor")
    @Test fun adapterRun() = reach("adapter_run")
    @Test fun adapterNoReturn() = reach("adapter_no_return")
    @Test fun adapterV() = reach("adapter_v")
    @Test fun methodFieldDeep() = reach("method_field_deep")
    @Test fun methodFieldDepth2() = reach("method_field_depth2")
    @Test fun methodFieldDict() = reach("method_field_dict")
    @Test fun methodForwardsSelf() = reach("method_forwards_self")
    @Test fun methodForwardsSelfCopy() = reach("method_forwards_self_copy")
    @Test fun callableFieldExplicit() = reach("callable_field_explicit")
}
