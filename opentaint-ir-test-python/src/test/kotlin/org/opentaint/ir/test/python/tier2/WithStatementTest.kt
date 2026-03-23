package org.opentaint.ir.test.python.tier2

import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Tag
import org.opentaint.ir.api.python.*
import org.opentaint.ir.test.python.PIRTestBase

@Tag("tier2")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class WithStatementTest : PIRTestBase() {

    private lateinit var cp: PIRClasspath

    companion object {
        val SOURCE = """
def ws_basic():
    with open("test.txt") as f:
        data = f.read()
    return data

def ws_no_target():
    with open("test.txt"):
        pass

def ws_multiple():
    with open("a") as f1, open("b") as f2:
        data = f1.read() + f2.read()
    return data

def ws_nested():
    with open("a") as f:
        with open("b") as g:
            data = f.read() + g.read()
    return data
        """.trimIndent()
    }

    @BeforeAll fun setup() { cp = buildFromSource(SOURCE) }
    @AfterAll fun tearDown() { cp.close() }

    private fun func(name: String) = cp.findFunctionOrNull("__test__.$name")!!
    private fun insts(name: String) = func(name).cfg.blocks.flatMap { it.instructions }
    private inline fun <reified T : PIRInstruction> allOf(name: String): List<T> =
        insts(name).filterIsInstance<T>()

    @Test
    fun `ws_basic - produces LoadAttr for __enter__`() {
        val attrs = insts("ws_basic").filterAssignOf<PIRAttrExpr>()
        assertTrue(attrs.any { it.attrExpr.attribute == "__enter__" },
            "Expected PIRAttrExpr with '__enter__', found: ${attrs.map { it.attrExpr.attribute }}")
    }

    @Test
    fun `ws_basic - produces LoadAttr for __exit__`() {
        val attrs = insts("ws_basic").filterAssignOf<PIRAttrExpr>()
        assertTrue(attrs.any { it.attrExpr.attribute == "__exit__" },
            "Expected PIRAttrExpr with '__exit__', found: ${attrs.map { it.attrExpr.attribute }}")
    }

    @Test
    fun `ws_basic - produces calls for open, __enter__, __exit__`() {
        val calls = allOf<PIRCall>("ws_basic")
        assertTrue(calls.size >= 3, "Expected >= 3 PIRCall (open, __enter__, __exit__), got ${calls.size}")
    }

    @Test
    fun `ws_basic - has read attribute load`() {
        val attrs = insts("ws_basic").filterAssignOf<PIRAttrExpr>()
        assertTrue(attrs.any { it.attrExpr.attribute == "read" },
            "Expected PIRAttrExpr for 'read', found: ${attrs.map { it.attrExpr.attribute }}")
    }

    @Test
    fun `ws_no_target - produces __enter__ and __exit__`() {
        val attrs = insts("ws_no_target").filterAssignOf<PIRAttrExpr>()
        assertTrue(attrs.any { it.attrExpr.attribute == "__enter__" }, "Expected __enter__ even without 'as'")
        assertTrue(attrs.any { it.attrExpr.attribute == "__exit__" }, "Expected __exit__ even without 'as'")
    }

    @Test
    fun `ws_no_target - has context manager calls`() {
        val calls = allOf<PIRCall>("ws_no_target")
        assertTrue(calls.size >= 3, "Expected >= 3 calls (open, __enter__, __exit__), got ${calls.size}")
    }

    @Test
    fun `ws_multiple - produces two __enter__ calls`() {
        val attrs = insts("ws_multiple").filterAssignOf<PIRAttrExpr>()
        val enterAttrs = attrs.filter { it.attrExpr.attribute == "__enter__" }
        assertTrue(enterAttrs.size >= 2, "Expected >= 2 __enter__ for multiple with, got ${enterAttrs.size}")
    }

    @Test
    fun `ws_multiple - produces two __exit__ calls`() {
        val attrs = insts("ws_multiple").filterAssignOf<PIRAttrExpr>()
        val exitAttrs = attrs.filter { it.attrExpr.attribute == "__exit__" }
        assertTrue(exitAttrs.size >= 2, "Expected >= 2 __exit__ for multiple with, got ${exitAttrs.size}")
    }

    @Test
    fun `ws_nested - produces two __enter__ calls`() {
        val attrs = insts("ws_nested").filterAssignOf<PIRAttrExpr>()
        val enterAttrs = attrs.filter { it.attrExpr.attribute == "__enter__" }
        assertTrue(enterAttrs.size >= 2, "Expected >= 2 __enter__ for nested with, got ${enterAttrs.size}")
    }

    @Test
    fun `ws_nested - produces two __exit__ calls`() {
        val attrs = insts("ws_nested").filterAssignOf<PIRAttrExpr>()
        val exitAttrs = attrs.filter { it.attrExpr.attribute == "__exit__" }
        assertTrue(exitAttrs.size >= 2, "Expected >= 2 __exit__ for nested with, got ${exitAttrs.size}")
    }

    @Test
    fun `ws_nested - has read on both file handles`() {
        val attrs = insts("ws_nested").filterAssignOf<PIRAttrExpr>()
        val readAttrs = attrs.filter { it.attrExpr.attribute == "read" }
        assertTrue(readAttrs.size >= 2, "Expected >= 2 read for nested with, got ${readAttrs.size}")
    }
}
