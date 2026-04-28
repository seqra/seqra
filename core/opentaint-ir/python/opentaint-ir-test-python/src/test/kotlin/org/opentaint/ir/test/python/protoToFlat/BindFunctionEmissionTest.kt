package org.opentaint.ir.test.python.protoToFlat

import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.opentaint.ir.impl.python.flat.FlatAssign
import org.opentaint.ir.impl.python.flat.FlatBindFunction
import org.opentaint.ir.impl.python.flat.FlatFunctionIR
import org.opentaint.ir.impl.python.flat.FlatGlobalRef
import org.opentaint.ir.impl.python.flat.FlatInst
import org.opentaint.ir.impl.python.flat.FlatLocal
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Verifies that nested-def, decorated nested-def, and lambda binding sites
 * lower to [FlatBindFunction] (not the previous shape of
 * `FlatAssign(FlatLocal, FlatGlobalRef(...))` for nested defs / a bare
 * `FlatGlobalRef` for lambdas).
 */
@Tag("tier2")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class BindFunctionEmissionTest : RawFlatModuleTestBase() {

    private val source = """
        def outer():
            def inner(x):
                return x + 1
            fn = lambda y: y * 2
            return inner

        def my_decorator(f):
            return f

        def with_decorated():
            @my_decorator
            def helper():
                return 42
            return helper
    """

    private fun FlatFunctionIR.allInstructions(): List<FlatInst> =
        cfg.blocks.flatMap { it.instructions }

    @Test
    fun `nested def emits FlatBindFunction targeting the local name`() {
        val module = lowerSourceToFlat(source)
        val outer = module.functions.first { it.qualifiedName.endsWith(".outer") }
        val innerLifted = module.functions.first {
            it.qualifiedName.endsWith(".outer.inner")
        }

        val binds = outer.allInstructions().filterIsInstance<FlatBindFunction>()
            .filter { (it.target as? FlatLocal)?.name == "inner" }
        assertEquals(
            1,
            binds.size,
            "expected exactly one FlatBindFunction with target FlatLocal(\"inner\") in outer, got $binds",
        )
        val bind = binds.single()
        assertEquals(innerLifted.name, bind.function.name)
        assertTrue(
            innerLifted.name.startsWith("inner\$local"),
            "lifted nested name should follow inner\$local<n> convention, got ${innerLifted.name}",
        )
    }

    @Test
    fun `lambda emits FlatBindFunction targeting a synthetic temp`() {
        val module = lowerSourceToFlat(source)
        val outer = module.functions.first { it.qualifiedName.endsWith(".outer") }
        val lambdaLifted = module.functions.first {
            it.name.startsWith("<lambda>\$")
        }

        val lambdaBinds = outer.allInstructions().filterIsInstance<FlatBindFunction>()
            .filter { it.function.name == lambdaLifted.name }
        assertEquals(
            1,
            lambdaBinds.size,
            "expected exactly one FlatBindFunction pointing at the lifted lambda, got $lambdaBinds",
        )
        val bind = lambdaBinds.single()
        val targetLocal = bind.target as? FlatLocal
        assertNotNull(targetLocal, "lambda bind target must be a FlatLocal")
        assertTrue(
            targetLocal.name.startsWith("\$t"),
            "lambda bind target should be a \$tN temp, got ${targetLocal.name}",
        )
    }

    @Test
    fun `decorator-wrapped nested def also emits FlatBindFunction`() {
        val module = lowerSourceToFlat(source)
        val withDecorated = module.functions.first { it.qualifiedName.endsWith(".with_decorated") }
        val helperLifted = module.functions.first {
            it.qualifiedName.endsWith(".with_decorated.helper")
        }

        val binds = withDecorated.allInstructions().filterIsInstance<FlatBindFunction>()
            .filter { (it.target as? FlatLocal)?.name == "helper" }
        assertEquals(
            1,
            binds.size,
            "expected one FlatBindFunction binding the decorated nested def to local 'helper', got $binds",
        )
        assertEquals(helperLifted.name, binds.single().function.name)
    }

    @Test
    fun `no FlatAssign of FlatLocal from FlatGlobalRef remains at nested-def or lambda binding sites`() {
        val module = lowerSourceToFlat(source)
        val liftedNames = module.functions.map { it.name }.toSet()

        val parents = listOf(".outer", ".with_decorated").map { suffix ->
            module.functions.first { it.qualifiedName.endsWith(suffix) }
        }
        for (parent in parents) {
            val offending = parent.allInstructions().filter { inst ->
                inst is FlatAssign &&
                    inst.target is FlatLocal &&
                    inst.source is FlatGlobalRef &&
                    (inst.source as FlatGlobalRef).name in liftedNames
            }
            assertTrue(
                offending.isEmpty(),
                "parent ${parent.qualifiedName} still uses FlatAssign(FlatLocal, FlatGlobalRef) for a lifted function: $offending",
            )
        }
    }
}
