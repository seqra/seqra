package org.opentaint.ir.test.python.tier2

import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import org.opentaint.ir.api.python.*
import org.opentaint.ir.test.python.PIRTestBase

/**
 * Tests for special constant types: complex numbers, bytes, ellipsis,
 * very large ints, and other literal edge cases.
 */
@Tag("tier2")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ConstantTypesTest : PIRTestBase() {

    private lateinit var cp: PIRClasspath

    companion object {
        val SOURCE = """
def ct_complex_literal():
    return 3+4j

def ct_complex_pure():
    return 1j

def ct_complex_zero():
    return 0j

def ct_bytes_literal():
    return b"hello"

def ct_bytes_empty():
    return b""

def ct_bytes_escape():
    return b"\\x00\\xff"

def ct_ellipsis():
    return ...

def ct_large_int():
    return 99999999999999999999999999999

def ct_negative_large_int():
    return -99999999999999999999999999999

def ct_zero():
    return 0

def ct_float_inf():
    return float('inf')

def ct_none():
    return None

def ct_true():
    return True

def ct_false():
    return False

def ct_string_empty():
    return ""

def ct_string_unicode():
    return "hello \\u00e9 world"

def ct_multiline():
    return '''line1
line2
line3'''

def ct_tuple_literal():
    return (1, 2, 3)

def ct_empty_tuple():
    return ()

def ct_nested_literal():
    return [1, [2, [3, 4]], 5]

def ct_dict_literal():
    return {"a": 1, "b": 2}

def ct_set_literal():
    return {1, 2, 3}

def ct_mixed_types():
    return [1, "two", 3.0, True, None]
        """.trimIndent()
    }

    @BeforeAll fun setup() { cp = buildFromSource(SOURCE) }
    @AfterAll fun tearDown() { cp.close() }

    private fun func(name: String): PIRFunction =
        cp.findFunctionOrNull("__test__.$name")
            ?: fail("Function $name not found")

    private fun insts(name: String) = func(name).cfg.blocks.flatMap { it.instructions }

    // ─── Complex number tests ──────────────────────────────

    @Test fun `complex literal has CFG`() {
        val f = func("ct_complex_literal")
        assertTrue(f.cfg.blocks.isNotEmpty())
        assertTrue(insts("ct_complex_literal").any { it is PIRReturn })
    }

    @Test fun `pure imaginary has CFG`() {
        val f = func("ct_complex_pure")
        assertTrue(f.cfg.blocks.isNotEmpty())
    }

    @Test fun `zero imaginary has CFG`() {
        val f = func("ct_complex_zero")
        assertTrue(f.cfg.blocks.isNotEmpty())
    }

    // ─── Bytes tests ───────────────────────────────────────

    @Test fun `bytes literal has CFG`() {
        val f = func("ct_bytes_literal")
        assertTrue(f.cfg.blocks.isNotEmpty())
        assertTrue(insts("ct_bytes_literal").any { it is PIRReturn })
    }

    @Test fun `empty bytes has CFG`() {
        assertTrue(func("ct_bytes_empty").cfg.blocks.isNotEmpty())
    }

    @Test fun `bytes with escapes has CFG`() {
        assertTrue(func("ct_bytes_escape").cfg.blocks.isNotEmpty())
    }

    // ─── Ellipsis tests ────────────────────────────────────

    @Test fun `ellipsis literal has CFG`() {
        val f = func("ct_ellipsis")
        assertTrue(f.cfg.blocks.isNotEmpty())
        assertTrue(insts("ct_ellipsis").any { it is PIRReturn })
    }

    // ─── Large int tests ───────────────────────────────────

    @Test fun `large int has CFG`() {
        assertTrue(func("ct_large_int").cfg.blocks.isNotEmpty())
    }

    @Test fun `negative large int has CFG`() {
        assertTrue(func("ct_negative_large_int").cfg.blocks.isNotEmpty())
    }

    // ─── Basic type tests ──────────────────────────────────

    @Test fun `zero returns correctly`() {
        assertTrue(insts("ct_zero").any { it is PIRReturn })
    }

    @Test fun `None constant returns`() {
        assertTrue(insts("ct_none").any { it is PIRReturn })
    }

    @Test fun `True constant returns`() {
        assertTrue(insts("ct_true").any { it is PIRReturn })
    }

    @Test fun `False constant returns`() {
        assertTrue(insts("ct_false").any { it is PIRReturn })
    }

    // ─── String tests ──────────────────────────────────────

    @Test fun `empty string has CFG`() {
        assertTrue(func("ct_string_empty").cfg.blocks.isNotEmpty())
    }

    @Test fun `unicode string has CFG`() {
        assertTrue(func("ct_string_unicode").cfg.blocks.isNotEmpty())
    }

    @Test fun `multiline string has CFG`() {
        assertTrue(func("ct_multiline").cfg.blocks.isNotEmpty())
    }

    // ─── Collection literal tests ──────────────────────────

    @Test fun `tuple literal produces BuildTuple`() {
        val builds = insts("ct_tuple_literal").filterAssignOf<PIRTupleExpr>()
        assertTrue(builds.isNotEmpty(), "Expected PIRBuildTuple for (1, 2, 3)")
    }

    @Test fun `empty tuple produces BuildTuple`() {
        val builds = insts("ct_empty_tuple").filterAssignOf<PIRTupleExpr>()
        assertTrue(builds.isNotEmpty(), "Expected PIRBuildTuple for ()")
    }

    @Test fun `nested list produces BuildList`() {
        val builds = insts("ct_nested_literal").filterAssignOf<PIRListExpr>()
        assertTrue(builds.size >= 2,
            "Expected >= 2 PIRBuildList for nested lists, got ${builds.size}")
    }

    @Test fun `dict literal produces BuildDict`() {
        val builds = insts("ct_dict_literal").filterAssignOf<PIRDictExpr>()
        assertTrue(builds.isNotEmpty(), "Expected PIRBuildDict for dict literal")
    }

    @Test fun `set literal produces BuildSet`() {
        val builds = insts("ct_set_literal").filterAssignOf<PIRSetExpr>()
        assertTrue(builds.isNotEmpty(), "Expected PIRBuildSet for set literal")
    }

    @Test fun `mixed type list produces BuildList`() {
        val builds = insts("ct_mixed_types").filterAssignOf<PIRListExpr>()
        assertTrue(builds.isNotEmpty(), "Expected PIRBuildList for mixed type list")
    }

    // ─── Float inf test ────────────────────────────────────

    @Test fun `float inf produces call`() {
        val calls = insts("ct_float_inf").filterIsInstance<PIRCall>()
        assertTrue(calls.isNotEmpty(), "Expected PIRCall for float('inf')")
    }

    // ─── Structural validity ───────────────────────────────

    @Test fun `all constant functions have valid CFGs`() {
        val funcNames = listOf(
            "ct_complex_literal", "ct_complex_pure", "ct_complex_zero",
            "ct_bytes_literal", "ct_bytes_empty", "ct_bytes_escape",
            "ct_ellipsis", "ct_large_int", "ct_negative_large_int",
            "ct_zero", "ct_float_inf", "ct_none", "ct_true", "ct_false",
            "ct_string_empty", "ct_string_unicode", "ct_multiline",
            "ct_tuple_literal", "ct_empty_tuple", "ct_nested_literal",
            "ct_dict_literal", "ct_set_literal", "ct_mixed_types"
        )
        for (name in funcNames) {
            val f = func(name)
            assertTrue(f.cfg.blocks.isNotEmpty(),
                "Function $name should have non-empty CFG")
            assertNotNull(f.cfg.entry, "Function $name should have entry block")
        }
    }
}
