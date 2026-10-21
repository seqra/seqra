package org.opentaint.python.sast.dataflow

import org.opentaint.dataflow.configuration.python.Argument
import org.opentaint.dataflow.configuration.python.serialized.PythonPosition
import org.opentaint.dataflow.configuration.python.serialized.PythonPositionBase
import org.opentaint.dataflow.configuration.python.serialized.PythonTarget
import org.opentaint.dataflow.configuration.python.serialized.SerializedPythonEntryPointSource
import org.opentaint.dataflow.configuration.python.serialized.SerializedPythonTaintAssignAction
import org.opentaint.dataflow.configuration.python.serialized.SerializedPythonTaintConfig
import org.opentaint.dataflow.python.rules.PIRTaintConfiguration
import org.opentaint.ir.api.common.cfg.CommonInst
import org.opentaint.ir.api.common.cfg.ControlFlowGraph
import org.opentaint.ir.api.python.PIRAnyType
import org.opentaint.ir.api.python.PIRClass
import org.opentaint.ir.api.python.PIRDecorator
import org.opentaint.ir.api.python.PIRFunction
import org.opentaint.ir.api.python.PIRParameter
import org.opentaint.ir.api.python.PIRType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

class PIRTaintConfigurationTest {

    @Test
    fun `unmatched FQN returns empty source list`() {
        val config = loadDefaultConfig()
        val other = stubMethod(qualifiedName = "some.unknown.func", shortName = "func")
        assertTrue(config.sourcesForMethod(other).isEmpty())
    }

    @Test
    fun `entry-point drops a concrete arg index the method lacks`() {
        val sink = stubMethod(
            qualifiedName = "sample.sink",
            shortName = "sink",
            parameters = listOf(stubParam("data", index = 0)),
        )
        val serializedConfig = SerializedPythonTaintConfig(
            entryPoint = listOf(
                entryPoint(
                    "sample.sink",
                    assignAction("remote", PythonPositionBase.Argument(0)),
                    assignAction("remote", PythonPositionBase.Argument(1)),
                ),
            )
        )
        val config = PIRTaintConfiguration().apply { loadConfig(serializedConfig) }

        val positions = config.entryPointSourcesForMethod(sink).flatMap { it.taint }.map { it.pos }
        assertEquals(listOf(Argument(0)), positions,
            "out-of-range arg(1) must be dropped; only the in-range arg(0) survives")
    }

    @Test
    fun `entry-point wildcard expands to declared args only`() {
        val sink = stubMethod(
            qualifiedName = "sample.sink",
            shortName = "sink",
            parameters = listOf(stubParam("data", index = 0)),
        )
        val serializedConfig = SerializedPythonTaintConfig(
            entryPoint = listOf(entryPoint("sample.sink", assignAction("remote", PythonPositionBase.Argument(null)))),
        )
        val config = PIRTaintConfiguration().apply { loadConfig(serializedConfig) }

        val positions = config.entryPointSourcesForMethod(sink).flatMap { it.taint }.map { it.pos }
        assertEquals(listOf(Argument(0)), positions, "arg(*) expands to exactly arg(0) for a single-arg method")
    }

    @Test
    fun `per-method lookup result is cached`() {
        val config = loadDefaultConfig()
        val m = stubMethod(qualifiedName = "os.getenv", shortName = "getenv")
        val first = config.sourcesForMethod(m)
        val second = config.sourcesForMethod(m)
        assertSame(first, second, "lookup result must be cached identity-equal")
    }
}

private fun stubMethod(
    qualifiedName: String,
    shortName: String,
    parameters: List<PIRParameter> = emptyList(),
    returnType: PIRType = PIRAnyType,
    decorators: List<PIRDecorator> = emptyList(),
    enclosingClass: PIRClass? = null,
): PIRFunction = object : PIRFunction {
    override val name: String = shortName
    override val qualifiedName: String = qualifiedName
    override val parameters: List<PIRParameter> = parameters
    override val returnType: PIRType = returnType
    override val decorators: List<PIRDecorator> = decorators
    override val enclosingClass: PIRClass? = enclosingClass
    override val cfg get() = error("not stubbed")
    override val instList get() = error("not stubbed")
    override val isAsync: Boolean get() = false
    override val isGenerator: Boolean get() = false
    override val isStaticMethod: Boolean get() = false
    override val isClassMethod: Boolean get() = false
    override val isProperty: Boolean get() = false
    override val closureVars: List<String> get() = emptyList()
    override val module get() = error("not stubbed")
    override fun flowGraph(): ControlFlowGraph<CommonInst> = error("not stubbed")
}

private fun entryPoint(function: String, vararg taint: SerializedPythonTaintAssignAction): SerializedPythonEntryPointSource =
    SerializedPythonEntryPointSource(target = PythonTarget.Function(function), taint = taint.toList())

private fun assignAction(kind: String, base: PythonPositionBase): SerializedPythonTaintAssignAction =
    SerializedPythonTaintAssignAction(kind = kind, pos = PythonPosition.BaseOnly(base))

private fun stubParam(name: String, index: Int, type: PIRType = PIRAnyType): PIRParameter =
    object : PIRParameter {
        override val name: String = name
        override val type: PIRType = type
        override val kind get() = error("not stubbed")
        override val hasDefault: Boolean get() = false
        override val defaultValue get() = null
        override val index: Int = index
    }
