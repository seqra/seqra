package org.opentaint.ir.test.python.protoToFlat

import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.opentaint.ir.impl.python.flat.FlatFunctionIR
import org.opentaint.ir.impl.python.flat.FlatModuleIR
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Verifies that `nonlocal` / `global` declarations in function bodies are
 * collected into `FlatFunctionIR.nonlocalNames` / `globalNames` during
 * proto-to-flat lowering, and that the raw output never carries any
 * closureVars (those are populated only by the closure transform).
 */
@Tag("tier2")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class NonlocalGlobalCollectionTest : RawFlatModuleTestBase() {

    private val source = """
        count = 0
        g = 0

        def outer():
            a = 1
            b = 2
            def inner():
                nonlocal a, b
                global g
                a = 10
                b = 20
                g = 30
            return inner
    """

    private fun allFunctions(module: FlatModuleIR): List<FlatFunctionIR> =
        module.functions + module.moduleInit +
            module.classes.flatMap { it.methods }

    @Test
    fun `inner function nonlocalNames contains a and b`() {
        val module = lowerSourceToFlat(source)
        val inner = module.functions.first { it.qualifiedName.endsWith(".outer.inner") }
        assertEquals(setOf("a", "b"), inner.nonlocalNames)
    }

    @Test
    fun `inner function globalNames contains g`() {
        val module = lowerSourceToFlat(source)
        val inner = module.functions.first { it.qualifiedName.endsWith(".outer.inner") }
        assertEquals(setOf("g"), inner.globalNames)
    }

    @Test
    fun `outer function has no nonlocal or global declarations`() {
        val module = lowerSourceToFlat(source)
        val outer = module.functions.first { it.qualifiedName.endsWith(".outer") }
        assertEquals(emptySet<String>(), outer.nonlocalNames)
        assertEquals(emptySet<String>(), outer.globalNames)
    }

    @Test
    fun `raw flat module has no closureVars on any function`() {
        val module = lowerSourceToFlat(source)
        for (f in allFunctions(module)) {
            assertTrue(
                f.closureVars.isEmpty(),
                "${f.qualifiedName} should have empty closureVars on raw IR, got ${f.closureVars}",
            )
        }
    }
}
