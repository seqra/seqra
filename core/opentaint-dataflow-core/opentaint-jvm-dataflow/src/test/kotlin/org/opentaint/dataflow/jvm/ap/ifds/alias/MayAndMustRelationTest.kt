package org.opentaint.dataflow.jvm.ap.ifds.alias

import org.junit.jupiter.api.TestInstance
import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.ap.ifds.AccessPathBase.Companion.Argument
import org.opentaint.dataflow.jvm.BasicTestUtils
import org.opentaint.dataflow.jvm.ap.ifds.JIRLocalAliasAnalysis
import org.opentaint.dataflow.jvm.ap.ifds.JIRLocalAliasAnalysis.AliasAccessor
import org.opentaint.ir.api.jvm.JIRMethod
import org.opentaint.ir.api.jvm.cfg.JIRCallInst
import org.opentaint.ir.api.jvm.cfg.JIRInst
import org.opentaint.ir.api.jvm.cfg.JIRLocalVar
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.fail

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MayAndMustRelationTest : BasicTestUtils() {
    override fun JIRLocalAliasAnalysis.getAliases(
        base: AccessPathBase.LocalVar,
        statement: JIRInst
    ): List<JIRLocalAliasAnalysis.AliasInfo> = error("unreachable")

    @Test
    fun `check must alias inclusion in may alias`() {
        val allClasses = cp.locations.flatMap { it.classNames.orEmpty() }
        val sampleClasses = allClasses.filter { it.startsWith(ALIAS_SAMPLE_PKG) }
        val methods = sampleClasses.flatMap { findClass(it).declaredMethods }

        methods.forEach { method ->
            val aa = aaForMethod(method)
            val sink = method.findOneOfSinkCalls() ?: return@forEach

            sink.callExpr.args.filterIsInstance<JIRLocalVar>().forEach { arg ->
                val simpleLoc = AccessPathBase.LocalVar(arg.index)

                val mayResult = aa.findAlias(simpleLoc, sink).orEmpty().toSet()
                val mustResult = aa.findMustAlias(simpleLoc, sink).orEmpty()

                mustResult.forEach { alias ->
                    if (alias !in mayResult) {
                        fail("Must alias diverged with May at `${method.enclosingClass.name}.${method.name}`!")
                    }
                }
            }
        }
    }

    private fun JIRMethod.findOneOfSinkCalls(): JIRCallInst? =
        instList.filterIsInstance<JIRCallInst>().firstOrNull { it.callExpr.method.name in SINKS }

    companion object {
        private val SINKS = listOf(
            "sinkOneValue",
            "sinkTwoValues",
            "testSimpleArgAlias",
        )
    }
}
