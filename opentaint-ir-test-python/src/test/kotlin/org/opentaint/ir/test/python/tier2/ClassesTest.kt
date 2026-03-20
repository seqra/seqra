package org.opentaint.ir.test.python.tier2

import org.junit.jupiter.api.*
import org.junit.jupiter.api.Tag
import org.opentaint.ir.api.python.*
import org.opentaint.ir.test.python.PIRTestBase

@Tag("tier2")
class ClassesTest : PIRTestBase() {

    @Test
    fun `class is extracted with methods`() {
        val cp = buildFromSource("""
            class MyClass:
                def __init__(self, x: int):
                    self.x = x

                def get_x(self) -> int:
                    return self.x
        """)
        cp.use {
            val cls = it.findClassOrNull("__test__.MyClass")
            Assertions.assertNotNull(cls, "Class __test__.MyClass not found")
            Assertions.assertTrue(cls!!.methods.size >= 2,
                "Expected at least 2 methods, got ${cls.methods.size}: ${cls.methods.map { m -> m.name }}")
        }
    }

    @Test
    fun `module-level function is found`() {
        val cp = buildFromSource("""
            def greet(name: str) -> str:
                return "Hello, " + name
        """)
        cp.use {
            val func = it.findFunctionOrNull("__test__.greet")
            Assertions.assertNotNull(func, "Function __test__.greet not found")
            Assertions.assertEquals(1, func!!.parameters.size,
                "Expected 1 parameter")
            Assertions.assertEquals("name", func.parameters[0].name)
        }
    }

    @Test
    fun `module fields are extracted`() {
        val cp = buildFromSource("""
            VERSION = "1.0"
            DEBUG = True
        """)
        cp.use {
            val module = it.findModuleOrNull("__test__")
            Assertions.assertNotNull(module, "Module __test__ not found")
            val fieldNames = module!!.fields.map { f -> f.name }
            Assertions.assertTrue("VERSION" in fieldNames,
                "Expected 'VERSION' field, got: $fieldNames")
            Assertions.assertTrue("DEBUG" in fieldNames,
                "Expected 'DEBUG' field, got: $fieldNames")
        }
    }
}
