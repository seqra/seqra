package org.opentaint.ir.test.python.protoToFlat

import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.opentaint.ir.impl.python.flat.FlatFunctionIR
import org.opentaint.ir.impl.python.flat.FlatModuleIR
import kotlin.test.assertTrue

/**
 * Sanity invariant: the raw output of `ProtoToFlat.lowerModule` always has
 * `closureVars = emptySet()` for every function, including module init,
 * lambdas, and class methods. The closure transform (later phase) is the
 * only producer of populated closureVars.
 */
@Tag("tier2")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RawFlatModuleClosureVarsEmptyTest : RawFlatModuleTestBase() {

    private val source = """
        x = 1

        def top_level(a):
            return a + x

        def with_nested():
            value = 10
            def reader():
                return value
            return reader()

        def with_nonlocal():
            count = 0
            def bump():
                nonlocal count
                count = count + 1
            return bump

        adder = lambda y: y + 1

        class Cls:
            attr = 1

            def method(self, n):
                return n + self.attr

            def method_with_nested(self):
                local = 5
                def helper():
                    return local
                return helper()
    """

    private fun allFunctions(module: FlatModuleIR): List<FlatFunctionIR> {
        fun classMethods(cls: org.opentaint.ir.impl.python.flat.FlatClass): List<FlatFunctionIR> =
            cls.methods + cls.nestedClasses.flatMap(::classMethods)
        return module.functions + module.moduleInit +
            module.classes.flatMap(::classMethods)
    }

    @Test
    fun `every function in raw flat module has empty closureVars`() {
        val module = lowerSourceToFlat(source)
        val functions = allFunctions(module)
        assertTrue(functions.isNotEmpty(), "fixture must produce at least one function")
        for (f in functions) {
            assertTrue(
                f.closureVars.isEmpty(),
                "${f.qualifiedName} (kind=${f.kind}) should have empty closureVars on raw IR, " +
                    "got ${f.closureVars}",
            )
        }
    }
}
