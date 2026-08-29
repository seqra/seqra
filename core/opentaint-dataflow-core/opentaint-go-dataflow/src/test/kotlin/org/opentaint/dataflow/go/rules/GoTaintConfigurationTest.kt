package org.opentaint.dataflow.go.rules

import org.opentaint.dataflow.configuration.go.serialized.GoNameMatcher
import org.opentaint.dataflow.configuration.go.serialized.GoSerializedPassAction
import org.opentaint.dataflow.configuration.go.serialized.GoSerializedRule
import org.opentaint.dataflow.configuration.go.serialized.GoSerializedTaintConfig
import org.opentaint.dataflow.configuration.jvm.serialized.PositionBase.Argument
import org.opentaint.dataflow.configuration.jvm.serialized.PositionBase.Result
import org.opentaint.dataflow.configuration.jvm.serialized.PositionBaseWithModifiers.BaseOnly
import org.opentaint.dataflow.go.GoFunctionSignature
import org.opentaint.ir.go.type.GoIRBasicType
import org.opentaint.ir.go.type.GoIRBasicTypeKind.STRING
import org.opentaint.ir.go.type.GoIRPointerType
import kotlin.test.Test
import kotlin.test.assertEquals

class GoTaintConfigurationTest {
    @Test
    fun `simple rule name matches a generic function instance`() {
        val stringType = GoIRBasicType(STRING)
        val rule = GoSerializedRule.PassThrough(
            pkg = GoNameMatcher.Simple("github.com/google/go-github/v89/github"),
            function = GoNameMatcher.Simple("Ptr"),
            copy = listOf(
                GoSerializedPassAction(
                    from = BaseOnly(Argument(0)),
                    to = BaseOnly(Result),
                ),
            ),
        )
        val configuration = GoTaintConfiguration().also {
            it.loadConfig(GoSerializedTaintConfig(passThrough = listOf(rule)))
        }
        val signature = GoFunctionSignature(
            name = "github.com/google/go-github/v89/github.Ptr[string]",
            receiverType = null,
            paramTypes = listOf(stringType),
            resultType = GoIRPointerType(stringType),
            pkgName = "github.com/google/go-github/v89/github",
        )

        assertEquals(1, configuration.passThroughForFunction(signature).size)
    }
}
