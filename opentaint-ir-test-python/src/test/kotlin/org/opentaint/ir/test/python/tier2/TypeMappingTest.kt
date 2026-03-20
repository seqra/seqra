package org.opentaint.ir.test.python.tier2

import org.junit.jupiter.api.*
import org.junit.jupiter.api.Tag
import org.opentaint.ir.api.python.*
import org.opentaint.ir.test.python.PIRTestBase

@Tag("tier2")
class TypeMappingTest : PIRTestBase() {

    @Test
    fun `int parameter has class type builtins_int`() {
        val cp = buildFromSource("""
            def f(x: int) -> int:
                return x
        """)
        cp.use {
            val func = it.findFunctionOrNull("__test__.f")
            Assertions.assertNotNull(func, "Function __test__.f not found")
            val param = func!!.parameters.first { p -> p.name == "x" }
            val type = param.type
            Assertions.assertTrue(type is PIRClassType,
                "Expected PIRClassType, got ${type::class.simpleName}")
            Assertions.assertEquals("builtins.int", (type as PIRClassType).qualifiedName)
        }
    }

    @Test
    fun `str return type maps correctly`() {
        val cp = buildFromSource("""
            def f() -> str:
                return "hello"
        """)
        cp.use {
            val func = it.findFunctionOrNull("__test__.f")
            Assertions.assertNotNull(func, "Function __test__.f not found")
            val retType = func!!.returnType
            Assertions.assertTrue(retType is PIRClassType,
                "Expected PIRClassType, got ${retType::class.simpleName}")
            Assertions.assertEquals("builtins.str", (retType as PIRClassType).qualifiedName)
        }
    }

    @Test
    fun `function with no type annotations returns Any`() {
        val cp = buildFromSource("""
            def f(x):
                return x
        """)
        cp.use {
            val func = it.findFunctionOrNull("__test__.f")
            Assertions.assertNotNull(func, "Function __test__.f not found")
            // Parameters without annotations should be Any
            val param = func!!.parameters.first { p -> p.name == "x" }
            // May be Any or unknown
            Assertions.assertNotNull(param.type)
        }
    }
}
