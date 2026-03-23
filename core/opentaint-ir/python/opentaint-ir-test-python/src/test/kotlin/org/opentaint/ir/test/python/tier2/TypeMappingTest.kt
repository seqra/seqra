package org.opentaint.ir.test.python.tier2

import org.junit.jupiter.api.*
import org.junit.jupiter.api.Tag
import org.opentaint.ir.api.python.*
import org.opentaint.ir.test.python.PIRTestBase

@Tag("tier2")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TypeMappingTest : PIRTestBase() {

    private lateinit var cp: PIRClasspath

    companion object {
        val SOURCE = """
def tm_int_param(x: int) -> int:
    return x

def tm_str_return() -> str:
    return "hello"

def tm_no_annot(x):
    return x
        """.trimIndent()
    }

    @BeforeAll fun setup() { cp = buildFromSource(SOURCE) }
    @AfterAll fun tearDown() { cp.close() }

    private fun func(name: String) = cp.findFunctionOrNull("__test__.$name")!!

    @Test
    fun `int parameter has class type builtins_int`() {
        val param = func("tm_int_param").parameters.first { p -> p.name == "x" }
        val type = param.type
        Assertions.assertTrue(type is PIRClassType,
            "Expected PIRClassType, got ${type::class.simpleName}")
        Assertions.assertEquals("builtins.int", (type as PIRClassType).qualifiedName)
    }

    @Test
    fun `str return type maps correctly`() {
        val retType = func("tm_str_return").returnType
        Assertions.assertTrue(retType is PIRClassType,
            "Expected PIRClassType, got ${retType::class.simpleName}")
        Assertions.assertEquals("builtins.str", (retType as PIRClassType).qualifiedName)
    }

    @Test
    fun `function with no type annotations returns Any`() {
        val param = func("tm_no_annot").parameters.first { p -> p.name == "x" }
        Assertions.assertNotNull(param.type)
    }
}
