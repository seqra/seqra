package org.opentaint.dataflow.jvm.ap.ifds.alias

import org.junit.jupiter.api.TestInstance
import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.ap.ifds.AccessPathBase.Companion.Argument
import org.opentaint.dataflow.jvm.BasicTestUtils
import org.opentaint.dataflow.jvm.ap.ifds.JIRLocalAliasAnalysis
import org.opentaint.dataflow.jvm.ap.ifds.JIRLocalAliasAnalysis.AliasAccessor
import org.opentaint.ir.api.jvm.cfg.JIRInst
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MayAliasSampleTest : BasicTestUtils() {
    override fun JIRLocalAliasAnalysis.getAliases(
        base: AccessPathBase.LocalVar,
        statement: JIRInst
    ): List<JIRLocalAliasAnalysis.AliasInfo>? = findAlias(base, statement)

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
        val aa = aaForMethod(method)

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
        val aa = aaForMethod(method)

        val sink = method.findSinkCall("sinkOneValue")
        val apAliases = aa.sinkArgApAliases(sink)

        assertTrue { apAliases.any { it.base == Argument(0) } }
    }

    @Test
    fun `test identity same-class call`() {
        val method = findMethod(INTERPROC_SAMPLE, "testIdentityCall")
        val aa = aaForMethod(method)

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

    @Test
    fun `test combined write arg then touch heap`() {
        val method = findMethod(COMBINED_HEAP_SAMPLE, "writeArgThenTouchHeap")
        val aa = aaForMethod(method)

        val sink = method.findSinkCall("sinkOneValue")
        val apAliases = aa.sinkArgApAliases(sink)

        assertTrue { apAliases.any { it.isPlainBase(Argument(1)) } }
        assertFalse {
            apAliases.any {
                it.base == Argument(0) && it.accessors.singleFieldNamed(FIELD_VALUE)
            }
        }
    }

    @Test
    fun `test combined return argument field`() {
        val method = findMethod(COMBINED_HEAP_SAMPLE, "returnArgField")
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
    fun `test combined return identity then write field`() {
        val method = findMethod(COMBINED_HEAP_SAMPLE, "returnIdentityThenWriteField")
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
    fun `test combined fresh object carries returned arg`() {
        val method = findMethod(COMBINED_HEAP_SAMPLE, "freshObjectCarriesReturnedArg")
        val aa = aaForMethod(method)

        val sink = method.findSinkCall("sinkOneValue")
        val apAliases = aa.sinkArgApAliases(sink)

        assertTrue { apAliases.any { it.isPlainBase(Argument(0)) } }
    }

    @Test
    fun `test combined fresh object copies argument field`() {
        val method = findMethod(COMBINED_HEAP_SAMPLE, "freshObjectCopiesArgumentField")
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
    fun `test combined pass through receiver then read field`() {
        val method = findMethod(COMBINED_HEAP_SAMPLE, "passThroughReceiverThenReadField")
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
    fun `test combined nested write return and touch heap`() {
        val method = findMethod(COMBINED_HEAP_SAMPLE, "nestedWriteReturnAndTouchHeap")
        val aa = aaForMethod(method)

        val sink = method.findSinkCall("sinkOneValue")
        val apAliases = aa.sinkArgApAliases(sink)

        assertTrue { apAliases.any { it.isPlainBase(Argument(1)) } }
        assertFalse { apAliases.any { it.accessors.isNotEmpty() } }
    }

    @Test
    fun `test combined overwrite field with fresh object`() {
        val method = findMethod(COMBINED_HEAP_SAMPLE, "overwriteFieldWithFreshObject")
        val aa = aaForMethod(method)

        val sink = method.findSinkCall("sinkOneValue")
        val apAliases = aa.sinkArgApAliases(sink)

        // note: may want to remove this alias link for may analysis in the future
        assertTrue { apAliases.any { it.isPlainBase(Argument(1)) } }

        assertTrue {
            apAliases.any {
                it.base == Argument(0) && it.accessors.singleFieldNamed(FIELD_VALUE)
            }
        }
    }

    @Test
    fun `test combined return fresh box then alias field`() {
        val method = findMethod(COMBINED_HEAP_SAMPLE, "returnFreshBoxThenAliasField")
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
}
