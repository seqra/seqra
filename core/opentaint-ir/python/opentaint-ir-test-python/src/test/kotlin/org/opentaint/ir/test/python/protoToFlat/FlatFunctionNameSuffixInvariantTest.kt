package org.opentaint.ir.test.python.protoToFlat

import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.opentaint.ir.impl.python.flat.FlatClass
import org.opentaint.ir.impl.python.flat.FlatFunctionIR
import org.opentaint.ir.impl.python.flat.FlatModuleIR
import kotlin.test.assertTrue

/**
 * Suffix invariant: every `FlatFunctionIR` in a lowered module satisfies
 * `qualifiedName.endsWith(name)`. This is the user-facing guarantee of the
 * `FlatGlobalRef` / `PIRGlobalRef` collapse refactor — the bare `name`
 * field is always the suffix of the canonical `qualifiedName`, regardless
 * of whether the function is top-level, a method, a lifted nested def, a
 * lambda, or the synthetic module-init.
 *
 * The fixture exercises every shape (including same-name shadowing
 * siblings, which stress the collision counter).
 */
@Tag("tier2")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class FlatFunctionNameSuffixInvariantTest : RawFlatModuleTestBase() {

    private val source = """
        def top_level():
            return 1

        def outer():
            def inner():
                return 1
            def deeper_outer():
                def inner():
                    return 2
                return inner
            f = lambda y: y + 1
            return inner

        def shadowing():
            def f():
                return 1
            def f():
                return 2
            return f

        class Cls:
            def method(self):
                return 1

            def method_with_nested(self):
                def helper():
                    return 1
                return helper
    """

    private fun allFunctions(module: FlatModuleIR): List<FlatFunctionIR> {
        fun classMethods(cls: FlatClass): List<FlatFunctionIR> =
            cls.methods + cls.nestedClasses.flatMap(::classMethods)
        return module.functions + module.moduleInit +
            module.classes.flatMap(::classMethods)
    }

    @Test
    fun `qualifiedName ends with name for every FlatFunctionIR`() {
        val module = lowerSourceToFlat(source)
        val functions = allFunctions(module)
        assertTrue(functions.isNotEmpty(), "fixture must produce at least one function")
        for (f in functions) {
            assertTrue(
                f.qualifiedName.endsWith(f.name),
                "qualifiedName '${f.qualifiedName}' must end with name '${f.name}' " +
                    "(kind=${f.kind})",
            )
        }
    }

    @Test
    fun `nested def name is a $-flattened path of its lexical scope`() {
        val module = lowerSourceToFlat(source)
        val inner = module.functions.first {
            it.qualifiedName.endsWith(".outer\$inner")
        }
        // `name` matches the suffix of qualifiedName after the module prefix.
        assertTrue(inner.name == "outer\$inner",
            "nested def short name should be 'outer\$inner', got '${inner.name}'")
    }

    @Test
    fun `triple-nested def name encodes its full lexical path`() {
        val module = lowerSourceToFlat(source)
        // outer.deeper_outer.inner — an inner shadowing the sibling of outer.
        val deeplyNested = module.functions.firstOrNull {
            it.name == "outer\$deeper_outer\$inner"
        }
        assertTrue(deeplyNested != null,
            "expected a function named 'outer\$deeper_outer\$inner'; got: " +
                module.functions.map { it.name })
    }

    @Test
    fun `same-parent shadowing siblings get distinct module-flat names`() {
        val module = lowerSourceToFlat(source)
        val fs = module.functions.filter {
            it.name == "shadowing\$f" || it.name.startsWith("shadowing\$f\$")
        }
        // Two `def f` inside `shadowing` must produce two distinct
        // FlatFunctionIRs (else module.functions would have a duplicate name).
        assertTrue(fs.size == 2,
            "expected two shadowing 'f' functions, got ${fs.size}: ${fs.map { it.name }}")
        assertTrue(fs.map { it.name }.toSet().size == 2,
            "shadowing siblings must have distinct names: ${fs.map { it.name }}")
    }
}
