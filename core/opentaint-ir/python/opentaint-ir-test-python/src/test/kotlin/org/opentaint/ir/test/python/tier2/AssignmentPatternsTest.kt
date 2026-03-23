package org.opentaint.ir.test.python.tier2

import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Tag
import org.opentaint.ir.api.python.*
import org.opentaint.ir.test.python.PIRTestBase

@Tag("tier2")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AssignmentPatternsTest : PIRTestBase() {

    private lateinit var cp: PIRClasspath

    companion object {
        val SOURCE = """
def ap_simple() -> int:
    x = 42
    return x

def ap_multi_target() -> int:
    a = b = 42
    return a + b

def ap_augmented_add() -> int:
    x = 10
    x += 5
    return x

def ap_augmented_mul() -> int:
    x = 10
    x *= 3
    return x

def ap_tuple_unpack() -> int:
    a, b, c = 1, 2, 3
    return a + b + c

def ap_nested_unpack() -> int:
    (a, b), c = (1, 2), 3
    return a + b + c

def ap_swap() -> tuple:
    a = 1
    b = 2
    a, b = b, a
    return (a, b)

def ap_starred() -> list:
    first, *rest = [1, 2, 3, 4]
    return rest

class Obj:
    x: int = 0
    b: int = 0

def ap_attr_assign(obj: Obj) -> None:
    obj.x = 42

def ap_subscript_assign(items: list) -> None:
    items[0] = 99

def ap_chained_attr(obj) -> None:
    obj.a.b = 42
        """.trimIndent()
    }

    @BeforeAll fun setup() { cp = buildFromSource(SOURCE) }
    @AfterAll fun tearDown() { cp.close() }

    private fun func(name: String) = cp.findFunctionOrNull("__test__.$name")!!
    private fun insts(name: String) = func(name).cfg.blocks.flatMap { it.instructions }
    private inline fun <reified T : PIRInstruction> allOf(name: String): List<T> =
        insts(name).filterIsInstance<T>()

    @Test
    fun `ap_simple - produces PIRAssign`() {
        assertTrue(allOf<PIRAssign>("ap_simple").isNotEmpty(), "Expected PIRAssign for 'x = 42'")
    }

    @Test
    fun `ap_simple - assigns integer constant`() {
        assertTrue(allOf<PIRAssign>("ap_simple").any { it.source is PIRIntConst },
            "Expected PIRAssign with PIRIntConst source")
    }

    @Test
    fun `ap_multi_target - produces multiple PIRAssign`() {
        assertTrue(allOf<PIRAssign>("ap_multi_target").size >= 2,
            "Expected >= 2 PIRAssign for 'a = b = 42'")
    }

    @Test
    fun `ap_augmented_add - produces PIRBinExpr ADD`() {
        assertTrue(insts("ap_augmented_add").filterAssignOf<PIRBinExpr>().any { it.binExpr.op == PIRBinaryOperator.ADD },
            "Expected PIRBinExpr(ADD) for 'x += 5'")
    }

    @Test
    fun `ap_augmented_add - produces PIRAssign after BinOp`() {
        assertTrue(allOf<PIRAssign>("ap_augmented_add").size >= 2,
            "Expected >= 2 PIRAssign for 'x = 10' and 'x += 5'")
    }

    @Test
    fun `ap_augmented_mul - produces PIRBinExpr MUL`() {
        assertTrue(insts("ap_augmented_mul").filterAssignOf<PIRBinExpr>().any { it.binExpr.op == PIRBinaryOperator.MUL },
            "Expected PIRBinExpr(MUL) for 'x *= 3'")
    }

    @Test
    fun `ap_tuple_unpack - produces PIRUnpack`() {
        assertTrue(allOf<PIRUnpack>("ap_tuple_unpack").isNotEmpty(),
            "Expected PIRUnpack for 'a, b, c = 1, 2, 3'")
    }

    @Test
    fun `ap_tuple_unpack - unpack has 3 targets`() {
        assertTrue(allOf<PIRUnpack>("ap_tuple_unpack").any { it.targets.size == 3 },
            "Expected PIRUnpack with 3 targets")
    }

    @Test
    fun `ap_nested_unpack - produces PIRUnpack`() {
        assertTrue(allOf<PIRUnpack>("ap_nested_unpack").isNotEmpty(),
            "Expected PIRUnpack for nested tuple unpack")
    }

    @Test
    fun `ap_nested_unpack - builds inner tuple`() {
        assertTrue(insts("ap_nested_unpack").filterAssignOf<PIRTupleExpr>().isNotEmpty(),
            "Expected PIRTupleExpr for inner (1, 2)")
    }

    @Test
    fun `ap_swap - produces PIRUnpack`() {
        assertTrue(allOf<PIRUnpack>("ap_swap").isNotEmpty(),
            "Expected PIRUnpack for 'a, b = b, a'")
    }

    @Test
    fun `ap_swap - unpack has 2 targets`() {
        assertTrue(allOf<PIRUnpack>("ap_swap").any { it.targets.size == 2 },
            "Expected PIRUnpack with 2 targets for swap")
    }

    @Test
    fun `ap_swap - builds tuple for RHS`() {
        assertTrue(insts("ap_swap").filterAssignOf<PIRTupleExpr>().isNotEmpty(),
            "Expected PIRTupleExpr for '(b, a)' on RHS")
    }

    @Test
    fun `ap_starred - produces PIRUnpack`() {
        assertTrue(allOf<PIRUnpack>("ap_starred").isNotEmpty(),
            "Expected PIRUnpack for 'first, *rest = ...'")
    }

    @Test
    fun `ap_starred - unpack has valid starIndex`() {
        assertTrue(allOf<PIRUnpack>("ap_starred").any { it.starIndex >= 0 },
            "Expected starIndex >= 0 for starred assignment")
    }

    @Test
    fun `ap_starred - starIndex is 1`() {
        assertTrue(allOf<PIRUnpack>("ap_starred").any { it.starIndex == 1 },
            "Expected starIndex == 1 for 'first, *rest'")
    }

    @Test
    fun `ap_attr_assign - produces PIRStoreAttr`() {
        assertTrue(allOf<PIRStoreAttr>("ap_attr_assign").isNotEmpty(),
            "Expected PIRStoreAttr for 'obj.x = 42'")
    }

    @Test
    fun `ap_attr_assign - stores to attribute x`() {
        assertTrue(allOf<PIRStoreAttr>("ap_attr_assign").any { it.attribute == "x" },
            "Expected PIRStoreAttr with attribute 'x'")
    }

    @Test
    fun `ap_subscript_assign - produces PIRStoreSubscript`() {
        assertTrue(allOf<PIRStoreSubscript>("ap_subscript_assign").isNotEmpty(),
            "Expected PIRStoreSubscript for 'items[0] = 99'")
    }

    @Test
    fun `ap_chained_attr - loads intermediate attribute`() {
        assertTrue(insts("ap_chained_attr").filterAssignOf<PIRAttrExpr>().any { it.attrExpr.attribute == "a" },
            "Expected PIRAttrExpr for 'obj.a'")
    }

    @Test
    fun `ap_chained_attr - stores final attribute`() {
        assertTrue(allOf<PIRStoreAttr>("ap_chained_attr").any { it.attribute == "b" },
            "Expected PIRStoreAttr for '.b = 42'")
    }
}
