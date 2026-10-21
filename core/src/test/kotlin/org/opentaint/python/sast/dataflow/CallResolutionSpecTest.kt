package org.opentaint.python.sast.dataflow

import org.junit.jupiter.api.TestInstance
import org.opentaint.dataflow.configuration.python.Argument
import org.opentaint.dataflow.configuration.python.Result
import kotlin.test.Test

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CallResolutionSpecTest : AnalysisTest() {

    private fun source() = source("CallResolutionSpec.source", "taint", Result)
    private fun sink() = sink("CallResolutionSpec.sink", "taint", Argument(0), "call-resolution-spec")
    private fun externalSink(function: String) = sink(function, "taint", Argument(0), "call-resolution-spec")

    private fun reachable(entryPoint: String) =
        assertSinkReachable(source = source(), sink = sink(), entryPointFunction = "CallResolutionSpec.$entryPoint")

    private fun notReachable(entryPoint: String) =
        assertSinkNotReachable(source = source(), sink = sink(), entryPointFunction = "CallResolutionSpec.$entryPoint")

    @Test
    fun testSimpleNameFallbackOnUnknownReceiver() = reachable("fallback_unknown_receiver")

    @Test
    fun testInheritedMethodResolvesToDefiningClass() = reachable("inherited_method_sinking")

    @Test
    fun testInheritedMethodDoesNotFallBack() = notReachable("inherited_method_safe")

    @Test
    fun testPropertyResolvesToItsType() = reachable("property_sinking")

    @Test
    fun testPropertyDoesNotFallBack() = notReachable("property_safe")

    @Test
    fun testInstanceAttributeWithExternalBaseFallsBack() = reachable("instance_attribute_with_external_base")

    @Test
    fun testProjectMemberFoundPastExternalMixin() = reachable("external_mixin_before_project_base")

    @Test
    fun testExternalBaseAttributeIsNamedByBase() = assertSinkReachable(
        source = attributeSource("threading.Thread.name", "taint"),
        sink = sink(),
        entryPointFunction = "CallResolutionSpec.external_base_attribute",
    )

    @Test
    fun testOwnAttributeReadIsNamedByReceiver() = assertSinkReachable(
        source = attributeSource("CallResolutionSpec.Cfg.secret", "taint"),
        sink = sink(),
        entryPointFunction = "CallResolutionSpec.read_own_attribute",
    )

    @Test
    fun testInheritedAttributeReadIsNamedByOwner() = assertSinkReachable(
        source = attributeSource("CallResolutionSpec.Cfg.secret", "taint"),
        sink = sink(),
        entryPointFunction = "CallResolutionSpec.read_inherited_attribute",
    )

    @Test
    fun testInstanceCallResolvesDunderCall() = reachable("instance_call")

    @Test
    fun testInheritedInitResolvesToBaseInit() = reachable("inherited_init")

    @Test
    fun testExternalConstructorIsNamedByExternalBase() = assertSinkReachable(
        source = source(),
        sink = externalSink("builtins.ValueError"),
        entryPointFunction = "CallResolutionSpec.external_constructor",
    )

    @Test
    fun testNamespacePackageFunctionResolves() = reachable("namespace_package_call")

    @Test
    fun testRealModuleMissFallsBack() = reachable("real_module_miss")

    @Test
    fun testCallKeepsChainNameNextToResolvedCallee() = assertSinkReachable(
        source = source(),
        sink = externalSink("os.path.join"),
        entryPointFunction = "CallResolutionSpec.resolved_callee_chain_name",
    )

    @Test
    fun testExternalAnnotatedFieldDoesNotFallBack() = notReachable("external_annotated_field")

    @Test
    fun testExternalAnnotatedFieldChainsExternally() = assertSinkReachable(
        source = source(),
        sink = externalSink("_io.StringIO.write"),
        entryPointFunction = "CallResolutionSpec.external_annotated_field",
    )
}
