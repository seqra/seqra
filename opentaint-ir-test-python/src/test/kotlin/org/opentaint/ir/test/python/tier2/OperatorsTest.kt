package org.opentaint.ir.test.python.tier2

import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Tag
import org.opentaint.ir.api.python.*
import org.opentaint.ir.test.python.PIRTestBase

@Tag("tier2")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class OperatorsTest : PIRTestBase() {

    private lateinit var cp: PIRClasspath

    companion object {
        val SOURCE = """
def op_add(a: int, b: int) -> int:
    return a + b
def op_sub(a: int, b: int) -> int:
    return a - b
def op_mul(a: int, b: int) -> int:
    return a * b
def op_div(a: float, b: float) -> float:
    return a / b
def op_floordiv(a: int, b: int) -> int:
    return a // b
def op_mod(a: int, b: int) -> int:
    return a % b
def op_pow(a: int, b: int) -> int:
    return a ** b
def op_neg(a: int) -> int:
    return -a
def op_pos(a: int) -> int:
    return +a
def op_not(a: bool) -> bool:
    return not a
def op_invert(a: int) -> int:
    return ~a
def op_eq(a: int, b: int) -> bool:
    return a == b
def op_ne(a: int, b: int) -> bool:
    return a != b
def op_lt(a: int, b: int) -> bool:
    return a < b
def op_le(a: int, b: int) -> bool:
    return a <= b
def op_gt(a: int, b: int) -> bool:
    return a > b
def op_ge(a: int, b: int) -> bool:
    return a >= b
def op_is(a: object, b: object) -> bool:
    return a is b
def op_is_not(a: object, b: object) -> bool:
    return a is not b
def op_in(a: int, b: list) -> bool:
    return a in b
def op_not_in(a: int, b: list) -> bool:
    return a not in b
def op_bitand(a: int, b: int) -> int:
    return a & b
def op_bitor(a: int, b: int) -> int:
    return a | b
def op_bitxor(a: int, b: int) -> int:
    return a ^ b
def op_lshift(a: int, b: int) -> int:
    return a << b
def op_rshift(a: int, b: int) -> int:
    return a >> b
        """.trimIndent()
    }

    @BeforeAll fun setup() { cp = buildFromSource(SOURCE) }
    @AfterAll fun tearDown() { cp.close() }
    private fun binOps(name: String) = cp.findFunctionOrNull("__test__.$name")!!
        .cfg.blocks.flatMap { it.instructions }.filterIsInstance<PIRBinOp>()
    private fun unaryOps(name: String) = cp.findFunctionOrNull("__test__.$name")!!
        .cfg.blocks.flatMap { it.instructions }.filterIsInstance<PIRUnaryOp>()
    private fun compares(name: String) = cp.findFunctionOrNull("__test__.$name")!!
        .cfg.blocks.flatMap { it.instructions }.filterIsInstance<PIRCompare>()

    @Test fun `ADD operator`() = assertTrue(binOps("op_add").any { it.op == PIRBinaryOperator.ADD })
    @Test fun `SUB operator`() = assertTrue(binOps("op_sub").any { it.op == PIRBinaryOperator.SUB })
    @Test fun `MUL operator`() = assertTrue(binOps("op_mul").any { it.op == PIRBinaryOperator.MUL })
    @Test fun `DIV operator`() = assertTrue(binOps("op_div").any { it.op == PIRBinaryOperator.DIV })
    @Test fun `FLOOR_DIV operator`() = assertTrue(binOps("op_floordiv").any { it.op == PIRBinaryOperator.FLOOR_DIV })
    @Test fun `MOD operator`() = assertTrue(binOps("op_mod").any { it.op == PIRBinaryOperator.MOD })
    @Test fun `POW operator`() = assertTrue(binOps("op_pow").any { it.op == PIRBinaryOperator.POW })
    @Test fun `BIT_AND operator`() = assertTrue(binOps("op_bitand").any { it.op == PIRBinaryOperator.BIT_AND })
    @Test fun `BIT_OR operator`() = assertTrue(binOps("op_bitor").any { it.op == PIRBinaryOperator.BIT_OR })
    @Test fun `BIT_XOR operator`() = assertTrue(binOps("op_bitxor").any { it.op == PIRBinaryOperator.BIT_XOR })
    @Test fun `LSHIFT operator`() = assertTrue(binOps("op_lshift").any { it.op == PIRBinaryOperator.LSHIFT })
    @Test fun `RSHIFT operator`() = assertTrue(binOps("op_rshift").any { it.op == PIRBinaryOperator.RSHIFT })
    @Test fun `NEG unary`() = assertTrue(unaryOps("op_neg").any { it.op == PIRUnaryOperator.NEG })
    @Test fun `POS unary`() = assertTrue(unaryOps("op_pos").any { it.op == PIRUnaryOperator.POS })
    @Test fun `NOT unary`() = assertTrue(unaryOps("op_not").any { it.op == PIRUnaryOperator.NOT })
    @Test fun `INVERT unary`() = assertTrue(unaryOps("op_invert").any { it.op == PIRUnaryOperator.INVERT })
    @Test fun `EQ compare`() = assertTrue(compares("op_eq").any { it.op == PIRCompareOperator.EQ })
    @Test fun `NE compare`() = assertTrue(compares("op_ne").any { it.op == PIRCompareOperator.NE })
    @Test fun `LT compare`() = assertTrue(compares("op_lt").any { it.op == PIRCompareOperator.LT })
    @Test fun `LE compare`() = assertTrue(compares("op_le").any { it.op == PIRCompareOperator.LE })
    @Test fun `GT compare`() = assertTrue(compares("op_gt").any { it.op == PIRCompareOperator.GT })
    @Test fun `GE compare`() = assertTrue(compares("op_ge").any { it.op == PIRCompareOperator.GE })
    @Test fun `IS compare`() = assertTrue(compares("op_is").any { it.op == PIRCompareOperator.IS })
    @Test fun `IS_NOT compare`() = assertTrue(compares("op_is_not").any { it.op == PIRCompareOperator.IS_NOT })
    @Test fun `IN compare`() = assertTrue(compares("op_in").any { it.op == PIRCompareOperator.IN })
    @Test fun `NOT_IN compare`() = assertTrue(compares("op_not_in").any { it.op == PIRCompareOperator.NOT_IN })
}
