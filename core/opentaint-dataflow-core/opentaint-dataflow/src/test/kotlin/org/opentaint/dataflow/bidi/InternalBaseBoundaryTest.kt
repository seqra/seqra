package org.opentaint.dataflow.bidi

import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.ap.ifds.AccessPathBaseStorage
import org.opentaint.dataflow.ap.ifds.serialization.AccessPathBaseSerializer
import org.opentaint.ir.api.common.CommonMethod
import org.opentaint.ir.api.common.CommonMethodParameter
import org.opentaint.ir.api.common.CommonTypeName
import org.opentaint.ir.api.common.cfg.CommonInst
import org.opentaint.ir.api.common.cfg.CommonInstLocation
import org.opentaint.ir.api.common.cfg.ControlFlowGraph
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals

class InternalBaseBoundaryTest {
    private fun internalBases(): List<AccessPathBase> =
        listOf(AccessPathBase.Zero, AccessPathBase.CleanerTemporary())

    @Test
    fun `A01 cleaner temporaries have object identity`() {
        val first = AccessPathBase.CleanerTemporary()
        val second = AccessPathBase.CleanerTemporary()
        assertNotEquals(first, second)
        assertEquals("<cleaner-temporary>", first.toString())
        for (base in listOf(AccessPathBase.Zero, AccessPathBase.This, AccessPathBase.Argument(0),
            AccessPathBase.LocalVar(0), AccessPathBase.ClassStatic)) {
            assertNotEquals<AccessPathBase>(base, first)
        }
    }

    @Test
    fun `A01 old base storage rejects internal bases`() {
        val storage = TestStorage()
        for (base in internalBases()) {
            assertFailsWith<IllegalStateException>("getOrCreate($base)") { storage.getOrCreate(base) }
            assertFailsWith<IllegalStateException>("find($base)") { storage.find(base) }
        }
        assertEquals(0, storage.created)
    }

    @Test
    fun `A01 serializer rejects internal bases before it writes bytes`() {
        for (base in internalBases()) {
            val bytes = ByteArrayOutputStream()
            val output = DataOutputStream(bytes)
            assertFailsWith<IllegalStateException>("serialize $base") {
                with(AccessPathBaseSerializer) { output.writeAccessPathBase(base) }
            }
            assertEquals(0, bytes.size())
        }
    }

    @Test
    fun `A01 existing base values keep their wire tags and round trip`() {
        val bases = listOf(
            AccessPathBase.This,
            AccessPathBase.LocalVar(129),
            AccessPathBase.Argument(17),
            AccessPathBase.Constant("java.lang.String", "sample"),
            AccessPathBase.ClassStatic,
            AccessPathBase.Return,
            AccessPathBase.Exception,
        )
        for ((tag, base) in bases.withIndex()) {
            val bytes = ByteArrayOutputStream()
            val output = DataOutputStream(bytes)
            with(AccessPathBaseSerializer) { output.writeAccessPathBase(base) }
            assertEquals(tag, bytes.toByteArray()[0].toInt())
            val input = DataInputStream(ByteArrayInputStream(bytes.toByteArray()))
            assertEquals(base, with(AccessPathBaseSerializer) { input.readAccessPathBase() })
            assertEquals(0, input.available())
        }
    }

    private class TestStorage : AccessPathBaseStorage<Any>(statement()) {
        var created = 0
        override fun createStorage(): Any = Any().also { created++ }
        override fun getOrCreateLocal(idx: Int): Any = error("unused")
        override fun findLocal(idx: Int): Any? = error("unused")
        override fun forEachLocalValue(body: (AccessPathBase, Any) -> Unit) = Unit
        override fun getOrCreateConstant(base: AccessPathBase.Constant): Any = error("unused")
        override fun findConstant(base: AccessPathBase.Constant): Any? = error("unused")
        override fun forEachConstantValue(body: (AccessPathBase, Any) -> Unit) = Unit
    }

    private companion object {
        fun statement(): CommonInst = object : CommonInst {
            override val location: CommonInstLocation = object : CommonInstLocation {
                override val method: CommonMethod = object : CommonMethod {
                    override val name: String = "boundary"
                    override val parameters: List<CommonMethodParameter> = emptyList()
                    override val returnType: CommonTypeName = object : CommonTypeName {
                        override val typeName: String = "void"
                    }
                    override fun flowGraph(): ControlFlowGraph<CommonInst> = error("unused")
                }
                override val index: Int = 0
            }
        }
    }
}
