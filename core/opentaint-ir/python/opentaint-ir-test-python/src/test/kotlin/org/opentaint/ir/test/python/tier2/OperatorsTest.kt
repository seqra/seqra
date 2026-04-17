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
        .instList.filterAssignOf<PIRBinaryExpr>()
    private fun unaryOps(name: String) = cp.findFunctionOrNull("__test__.$name")!!
        .instList.filterAssignOf<PIRUnaryExpr>()
    private fun compares(name: String) = cp.findFunctionOrNull("__test__.$name")!!
        .instList.filterAssignOf<PIRCompareExpr>()

    @Test fun `ADD operator`() = assertTrue(binOps("op_add").any { it.binaryExpr is PIRAddExpr })
    @Test fun `SUB operator`() = assertTrue(binOps("op_sub").any { it.binaryExpr is PIRSubExpr })
    @Test fun `MUL operator`() = assertTrue(binOps("op_mul").any { it.binaryExpr is PIRMulExpr })
    @Test fun `DIV operator`() = assertTrue(binOps("op_div").any { it.binaryExpr is PIRDivExpr })
    @Test fun `FLOOR_DIV operator`() = assertTrue(binOps("op_floordiv").any { it.binaryExpr is PIRFloorDivExpr })
    @Test fun `MOD operator`() = assertTrue(binOps("op_mod").any { it.binaryExpr is PIRModExpr })
    @Test fun `POW operator`() = assertTrue(binOps("op_pow").any { it.binaryExpr is PIRPowExpr })
    @Test fun `BIT_AND operator`() = assertTrue(binOps("op_bitand").any { it.binaryExpr is PIRBitAndExpr })
    @Test fun `BIT_OR operator`() = assertTrue(binOps("op_bitor").any { it.binaryExpr is PIRBitOrExpr })
    @Test fun `BIT_XOR operator`() = assertTrue(binOps("op_bitxor").any { it.binaryExpr is PIRBitXorExpr })
    @Test fun `LSHIFT operator`() = assertTrue(binOps("op_lshift").any { it.binaryExpr is PIRLShiftExpr })
    @Test fun `RSHIFT operator`() = assertTrue(binOps("op_rshift").any { it.binaryExpr is PIRRShiftExpr })
    @Test fun `NEG unary`() = assertTrue(unaryOps("op_neg").any { it.unaryExpr is PIRNegExpr })
    @Test fun `POS unary`() = assertTrue(unaryOps("op_pos").any { it.unaryExpr is PIRPosExpr })
    @Test fun `NOT unary`() = assertTrue(unaryOps("op_not").any { it.unaryExpr is PIRNotExpr })
    @Test fun `INVERT unary`() = assertTrue(unaryOps("op_invert").any { it.unaryExpr is PIRInvertExpr })
    @Test fun `EQ compare`() = assertTrue(compares("op_eq").any { it.compareExpr is PIREqExpr })
    @Test fun `NE compare`() = assertTrue(compares("op_ne").any { it.compareExpr is PIRNeExpr })
    @Test fun `LT compare`() = assertTrue(compares("op_lt").any { it.compareExpr is PIRLtExpr })
    @Test fun `LE compare`() = assertTrue(compares("op_le").any { it.compareExpr is PIRLeExpr })
    @Test fun `GT compare`() = assertTrue(compares("op_gt").any { it.compareExpr is PIRGtExpr })
    @Test fun `GE compare`() = assertTrue(compares("op_ge").any { it.compareExpr is PIRGeExpr })
    @Test fun `IS compare`() = assertTrue(compares("op_is").any { it.compareExpr is PIRIsExpr })
    @Test fun `IS_NOT compare`() = assertTrue(compares("op_is_not").any { it.compareExpr is PIRIsNotExpr })
    @Test fun `IN compare`() = assertTrue(compares("op_in").any { it.compareExpr is PIRInExpr })
    @Test fun `NOT_IN compare`() = assertTrue(compares("op_not_in").any { it.compareExpr is PIRNotInExpr })
}
