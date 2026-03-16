package org.opentaint.dataflow.jvm.ap.ifds.alias

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.TestInstance
import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.ap.ifds.AccessPathBase.Companion.Argument
import org.opentaint.dataflow.ifds.SingletonUnit
import org.opentaint.dataflow.ifds.UnitType
import org.opentaint.dataflow.ifds.UnknownUnit
import org.opentaint.dataflow.jvm.BasicTestUtils
import org.opentaint.dataflow.jvm.ap.ifds.JIRCallResolver
import org.opentaint.dataflow.jvm.ap.ifds.JIRLocalAliasAnalysis
import org.opentaint.dataflow.jvm.ap.ifds.JIRLocalAliasAnalysis.AliasAccessor
import org.opentaint.dataflow.jvm.ap.ifds.JIRLocalAliasAnalysis.AliasApInfo
import org.opentaint.dataflow.jvm.ap.ifds.JIRLocalVariableReachability
import org.opentaint.dataflow.jvm.ap.ifds.analysis.JIRAnalysisManager
import org.opentaint.dataflow.jvm.ifds.JIRUnitResolver
import org.opentaint.ir.api.jvm.JIRMethod
import org.opentaint.ir.api.jvm.RegisteredLocation
import org.opentaint.ir.api.jvm.cfg.JIRCallInst
import org.opentaint.ir.api.jvm.cfg.JIRInst
import org.opentaint.ir.api.jvm.cfg.JIRLocalVar
import org.opentaint.ir.api.jvm.cfg.JIRValue
import org.opentaint.ir.impl.features.usagesExt
import org.opentaint.jvm.graph.JApplicationGraphImpl
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AliasSampleTest : BasicTestUtils() {
    private val manager by lazy { JIRAnalysisManager(cp) }

    @Test
    fun `test simple aliasing`() {
        val method = findMethod(SIMPLE_SAMPLE, "simpleArgAlias")
        val aa = aaForMethod(method)

        val sink = method.findSinkCall("testSimpleArgAlias")
        val apAliases = aa.sinkArgApAliases(sink)

        assertTrue { apAliases.any { it.isPlainBase(Argument(0)) } }
        assertTrue { apAliases.any { it.isPlainBase(Argument(1)) } }
    }

    @Test
    fun `test alias in while loop`() {
        val method = findMethod(LOOP_SAMPLE, "aliasInLoop")
        val aa = aaForMethod(method)

        val sink = method.findSinkCall("sinkOneValue")
        val apAliases = aa.sinkArgApAliases(sink)

        assertTrue { apAliases.any { it.isPlainBase(Argument(0)) } }
        assertTrue { apAliases.any { it.isPlainBase(Argument(1)) } }
    }

    @Test
    fun `test alias in for-each loop`() {
        val method = findMethod(LOOP_SAMPLE, "aliasInForEachLoop")
        val aa = aaForMethod(method)

        val sink = method.findSinkCall("sinkOneValue")
        val apAliases = aa.sinkArgApAliases(sink)

        assertFalse { apAliases.any { it.isPlainBase(Argument(0)) } }
    }

    @Test
    fun `test alias in try-catch both branches`() {
        val method = findMethod(LOOP_SAMPLE, "aliasInTryCatch")
        val aa = aaForMethod(method)

        val sink = method.findSinkCall("sinkOneValue")
        val apAliases = aa.sinkArgApAliases(sink)

        assertTrue { apAliases.any { it.isPlainBase(Argument(0)) } }
        assertTrue { apAliases.any { it.isPlainBase(Argument(1)) } }
    }

    @Test
    fun `test alias in try only`() {
        val method = findMethod(LOOP_SAMPLE, "aliasInTryOnly")
        val aa = aaForMethod(method)

        val sink = method.findSinkCall("sinkOneValue")
        val apAliases = aa.sinkArgApAliases(sink)

        assertTrue { apAliases.any { it.isPlainBase(Argument(0)) } }
    }

    @Test
    fun `test node next loop produces field chain`() {
        val method = findMethod(LOOP_SAMPLE, "nodeNextLoop")
        val aa = aaForMethod(method)

        val sink = method.findSinkCall("sinkOneValue")
        val apAliases = aa.sinkArgApAliases(sink)

        assertTrue { apAliases.any { it.base == Argument(0) && it.accessors.isNotEmpty() } }
        assertTrue {
            apAliases.any {
                it.base == Argument(0) && it.accessors.all { a -> a.isField(FIELD_NEXT) }
            }
        }
    }

    @Test
    fun `test node next loop data produces next chain ending with data`() {
        val method = findMethod(LOOP_SAMPLE, "nodeNextLoopData")
        val aa = aaForMethod(method)

        val sink = method.findSinkCall("sinkOneValue")
        val apAliases = aa.sinkArgApAliases(sink)

        assertTrue {
            apAliases.any {
                it.base == Argument(0)
                    && it.accessors.size >= 2
                    && it.accessors.last().isField(FIELD_DATA)
                    && it.accessors.dropLast(1).all { a -> a.isField(FIELD_NEXT) }
            }
        }

        assertTrue {
            apAliases.any {
                it.base == Argument(0)
                    && it.accessors.size == 1
                    && it.accessors.single().isField(FIELD_DATA)
            }
        }
    }

    @Test
    fun `test read argument field`() {
        val method = findMethod(HEAP_SAMPLE, "readArgField")
        val aa = aaForMethod(method)

        val sink = method.findSinkCall("sinkOneValue")
        val apAliases = aa.sinkArgApAliases(sink)

        assertTrue {
            apAliases.any {
                it.base == Argument(0) && it.accessors.singleFieldNamed(FIELD_VALUE)
            }
        }
    }

    @Test
    fun `test write then read argument field`() {
        val method = findMethod(HEAP_SAMPLE, "writeArgField")
        val aa = aaForMethod(method)

        val sink = method.findSinkCall("sinkOneValue")
        val apAliases = aa.sinkArgApAliases(sink)

        assertTrue { apAliases.any { it.isPlainBase(Argument(1)) } }
        assertTrue {
            apAliases.any {
                it.base == Argument(0) && it.accessors.singleFieldNamed(FIELD_VALUE)
            }
        }
    }

    @Test
    fun `test read argument deep field`() {
        val method = findMethod(HEAP_SAMPLE, "readArgDeepField")
        val aa = aaForMethod(method)

        val sink = method.findSinkCall("sinkOneValue")
        val apAliases = aa.sinkArgApAliases(sink)

        assertTrue {
            apAliases.any {
                it.base == Argument(0)
                    && it.accessors.size == 2
                    && it.accessors[0].isField(FIELD_BOX)
                    && it.accessors[1].isField(FIELD_VALUE)
            }
        }
    }

    @Test
    fun `test write then read argument deep field`() {
        val method = findMethod(HEAP_SAMPLE, "writeArgDeepField")
        val aa = aaForMethod(method)

        val sink = method.findSinkCall("sinkOneValue")
        val apAliases = aa.sinkArgApAliases(sink)

        assertTrue { apAliases.any { it.isPlainBase(Argument(1)) } }
        assertTrue {
            apAliases.any {
                it.base == Argument(0)
                    && it.accessors.size == 2
                    && it.accessors[0].isField(FIELD_BOX)
                    && it.accessors[1].isField(FIELD_VALUE)
            }
        }
    }

    @Test
    fun `test read argument array element`() {
        val method = findMethod(HEAP_SAMPLE, "readArgArrayElement")
        val aa = aaForMethod(method)

        val sink = method.findSinkCall("sinkOneValue")
        val apAliases = aa.sinkArgApAliases(sink)

        assertTrue {
            apAliases.any {
                it.base == Argument(0) && it.accessors.singleOrNull() == AliasAccessor.Array
            }
        }
    }

    @Test
    fun `test write then read argument array element`() {
        val method = findMethod(HEAP_SAMPLE, "writeArgArrayElement")
        val aa = aaForMethod(method)

        val sink = method.findSinkCall("sinkOneValue")
        val apAliases = aa.sinkArgApAliases(sink)

        assertTrue { apAliases.any { it.isPlainBase(Argument(1)) } }
        assertTrue {
            apAliases.any {
                it.base == Argument(0) && it.accessors.singleOrNull() == AliasAccessor.Array
            }
        }
    }

    @Test
    fun `test field to field copy`() {
        val method = findMethod(HEAP_SAMPLE, "fieldToField")
        val aa = aaForMethod(method)

        val sink = method.findSinkCall("sinkOneValue")
        val apAliases = aa.sinkArgApAliases(sink)

        assertTrue {
            apAliases.any {
                it.base == Argument(0) && it.accessors.singleFieldNamed(FIELD_VALUE)
            }
        }
        assertTrue {
            apAliases.any {
                it.base == Argument(1) && it.accessors.singleFieldNamed(FIELD_VALUE)
            }
        }
    }

    @Test
    fun `test swap fields`() {
        val method = findMethod(HEAP_SAMPLE, "swapFields")
        val aa = aaForMethod(method)

        val sink = method.findSinkCall("sinkTwoValues")

        val aValueAliases = aa.valueApAliases(sink.callExpr.args[0], sink)
        assertTrue {
            aValueAliases.any {
                it.base == Argument(0) && it.accessors.singleFieldNamed(FIELD_VALUE)
            }
        }
        assertTrue {
            aValueAliases.any {
                it.base == Argument(1) && it.accessors.singleFieldNamed(FIELD_VALUE)
            }
        }

        val bValueAliases = aa.valueApAliases(sink.callExpr.args[1], sink)
        assertTrue {
            bValueAliases.any {
                it.base == Argument(0) && it.accessors.singleFieldNamed(FIELD_VALUE)
            }
        }
        assertTrue {
            bValueAliases.any {
                it.base == Argument(1) && it.accessors.singleFieldNamed(FIELD_VALUE)
            }
        }
    }

    @Test
    fun `test array element to field`() {
        val method = findMethod(HEAP_SAMPLE, "arrayToField")
        val aa = aaForMethod(method)

        val sink = method.findSinkCall("sinkOneValue")
        val apAliases = aa.sinkArgApAliases(sink)

        assertTrue {
            apAliases.any {
                it.base == Argument(0) && it.accessors.singleOrNull() == AliasAccessor.Array
            }
        }
        assertTrue {
            apAliases.any {
                it.base == Argument(1) && it.accessors.singleFieldNamed(FIELD_VALUE)
            }
        }
    }

    @Test
    fun `test field to array element`() {
        val method = findMethod(HEAP_SAMPLE, "fieldToArray")
        val aa = aaForMethod(method)

        val sink = method.findSinkCall("sinkOneValue")
        val apAliases = aa.sinkArgApAliases(sink)

        assertTrue {
            apAliases.any {
                it.base == Argument(0) && it.accessors.singleFieldNamed(FIELD_VALUE)
            }
        }
        assertTrue {
            apAliases.any {
                it.base == Argument(1) && it.accessors.singleOrNull() == AliasAccessor.Array
            }
        }
    }

    @Test
    fun `test node traversal produces field chain`() {
        val method = findMethod(HEAP_SAMPLE, "nodeTraversal")
        val aa = aaForMethod(method)

        val sink = method.findSinkCall("sinkOneValue")
        val apAliases = aa.sinkArgApAliases(sink)

        assertTrue {
            apAliases.any {
                it.base == Argument(0)
                    && it.accessors.isNotEmpty()
                    && it.accessors.all { a -> a.isField(FIELD_NEXT) }
            }
        }
    }

    @Test
    fun `test node traversal data produces next chain ending with data`() {
        val method = findMethod(HEAP_SAMPLE, "nodeTraversalData")
        val aa = aaForMethod(method)

        val sink = method.findSinkCall("sinkOneValue")
        val apAliases = aa.sinkArgApAliases(sink)

        assertTrue {
            apAliases.any {
                it.base == Argument(0)
                    && it.accessors.size == 1
                    && it.accessors.single().isField(FIELD_DATA)
            }
        }
        assertTrue {
            apAliases.any {
                it.base == Argument(0)
                    && it.accessors.size >= 2
                    && it.accessors.last().isField(FIELD_DATA)
                    && it.accessors.dropLast(1).all { a -> a.isField(FIELD_NEXT) }
            }
        }
    }

    @Test
    fun `test field overwrite on argument receiver`() {
        val method = findMethod(HEAP_SAMPLE, "fieldOverwrite")
        val aa = aaForMethod(method)

        val sink = method.findSinkCall("sinkOneValue")
        val apAliases = aa.sinkArgApAliases(sink)

        assertTrue { apAliases.any { it.isPlainBase(Argument(2)) } }
        assertTrue {
            apAliases.any {
                it.base == Argument(0) && it.accessors.singleFieldNamed(FIELD_VALUE)
            }
        }
    }

    @Test
    fun `test conditional field write on argument receiver`() {
        val method = findMethod(HEAP_SAMPLE, "conditionalFieldWrite")
        val aa = aaForMethod(method)

        val sink = method.findSinkCall("sinkOneValue")
        val apAliases = aa.sinkArgApAliases(sink)

        assertTrue { apAliases.any { it.isPlainBase(Argument(1)) } }
        assertTrue { apAliases.any { it.isPlainBase(Argument(2)) } }
        assertTrue {
            apAliases.any {
                it.base == Argument(0) && it.accessors.singleFieldNamed(FIELD_VALUE)
            }
        }
    }

    @Test
    fun `test aliased receiver field write`() {
        val method = findMethod(HEAP_SAMPLE, "aliasedReceiverFieldWrite")
        val aa = aaForMethod(method)

        val sink = method.findSinkCall("sinkOneValue")
        val apAliases = aa.sinkArgApAliases(sink)

        assertTrue {
            apAliases.any {
                it.base == Argument(1) && it.accessors.singleFieldNamed(FIELD_VALUE)
            }
        }
    }

    @Test
    fun `test getter aliases this field`() {
        val method = findMethod(INTERPROC_SAMPLE, "testGetterAlias")
        val aa = aaForMethod(method, interProcParams(depth = 1))

        val sink = method.findSinkCall("sinkOneValue")
        val apAliases = aa.sinkArgApAliases(sink)

        assertTrue {
            apAliases.any {
                it.base == AccessPathBase.This && it.accessors.singleFieldNamed(FIELD_INTERPROC)
            }
        }
    }

    @Test
    fun `test setter then getter`() {
        val method = findMethod(INTERPROC_SAMPLE, "testSetterThenGetter")
        val aa = aaForMethod(method, interProcParams(depth = 1))

        val sink = method.findSinkCall("sinkOneValue")
        val apAliases = aa.sinkArgApAliases(sink)

        assertTrue { apAliases.any { it.base == Argument(0) } }
    }

    @Test
    fun `test identity same-class call`() {
        val method = findMethod(INTERPROC_SAMPLE, "testIdentityCall")
        val aa = aaForMethod(method, interProcParams(depth = 1))

        val sink = method.findSinkCall("sinkOneValue")
        val apAliases = aa.sinkArgApAliases(sink)

        assertTrue { apAliases.any { it.isPlainBase(Argument(0)) } }
    }

    @Test
    fun `test external call return is unknown`() {
        val method = findMethod(INTERPROC_SAMPLE, "testExternalCallReturn")
        val aa = aaForMethod(method)

        val sink = method.findSinkCall("sinkOneValue")
        val apAliases = aa.sinkArgApAliases(sink)

        assertFalse { apAliases.any { it.isPlainBase(Argument(0)) } }
    }

    @Test
    fun `test external call invalidates heap aliases`() {
        val method = findMethod(INTERPROC_SAMPLE, "testExternalCallInvalidatesHeap")
        val aa = aaForMethod(method)

        val sink = method.findSinkCall("sinkOneValue")
        val apAliases = aa.sinkArgApAliases(sink)

        assertFalse { apAliases.any { it.isPlainBase(Argument(0)) } }
    }

    private fun aaForMethod(
        method: JIRMethod,
        params: JIRLocalAliasAnalysis.Params = JIRLocalAliasAnalysis.Params()
    ): JIRLocalAliasAnalysis {
        val ep = method.instList.first()
        val usages = runBlocking { cp.usagesExt() }
        val graph = JApplicationGraphImpl(cp, usages)

        val callResolver = JIRCallResolver(cp, SingleLocationUnit(method.enclosingClass.declaration.location))
        val localReachability = JIRLocalVariableReachability(method, graph, manager)

        return JIRLocalAliasAnalysis(ep, graph, callResolver, localReachability, manager, params)
    }

    private fun interProcParams(depth: Int) =
        JIRLocalAliasAnalysis.Params(useAliasAnalysis = true, aliasAnalysisInterProcCallDepth = depth)

    private fun JIRMethod.findSinkCall(sinkName: String): JIRCallInst =
        instList.filterIsInstance<JIRCallInst>().first { it.callExpr.method.name == sinkName }

    private fun JIRLocalAliasAnalysis.valueApAliases(value: JIRValue, stmt: JIRInst): List<AliasApInfo> =
        valueAliases(value, stmt).filterIsInstance<AliasApInfo>()

    private fun JIRLocalAliasAnalysis.sinkArgApAliases(sink: JIRCallInst): List<AliasApInfo> =
        valueApAliases(sink.callExpr.args[0], sink)

    private fun JIRLocalAliasAnalysis.valueAliases(
        value: JIRValue,
        stmt: JIRInst
    ): List<JIRLocalAliasAnalysis.AliasInfo> {
        check(value is JIRLocalVar) { "Only local var aliases supported" }
        return findAlias(AccessPathBase.LocalVar(value.index), stmt).orEmpty()
    }

    private fun AliasApInfo.isPlainBase(expected: AccessPathBase): Boolean =
        accessors.isEmpty() && base == expected

    private fun AliasAccessor.isField(name: String): Boolean =
        this is AliasAccessor.Field && this.fieldName == name

    private fun List<AliasAccessor>.singleFieldNamed(name: String): Boolean =
        size == 1 && single().isField(name)

    private class SingleLocationUnit(val loc: RegisteredLocation) : JIRUnitResolver {
        override fun resolve(method: JIRMethod): UnitType =
            if (method.enclosingClass.declaration.location == loc) SingletonUnit else UnknownUnit

        override fun locationIsUnknown(loc: RegisteredLocation): Boolean = loc != this.loc
    }

    companion object {
        const val ALIAS_SAMPLE_PKG = "sample.alias"
        const val SIMPLE_SAMPLE = "$ALIAS_SAMPLE_PKG.SimpleAliasSample"
        const val LOOP_SAMPLE = "$ALIAS_SAMPLE_PKG.LoopAliasSample"
        const val HEAP_SAMPLE = "$ALIAS_SAMPLE_PKG.HeapAliasSample"
        const val INTERPROC_SAMPLE = "$ALIAS_SAMPLE_PKG.InterProcAliasSample"

        private const val FIELD_VALUE = "value"
        private const val FIELD_BOX = "box"
        private const val FIELD_NEXT = "next"
        private const val FIELD_DATA = "data"
        private const val FIELD_INTERPROC = "field"
    }
}
