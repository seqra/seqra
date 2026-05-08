package org.opentaint.ir.test.python.tier2

import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.opentaint.ir.api.python.PIRCall
import org.opentaint.ir.api.python.PIRClasspath
import org.opentaint.ir.api.python.PIRGlobalRef
import org.opentaint.ir.api.python.PIRLocalVar
import org.opentaint.ir.test.python.PIRTestBase
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * End-to-end test: lower real Python source through proto→Flat→closure
 * transform→PIR conversion and assert callable-shim invariants on the
 * resulting PIR module.
 */
@Tag("tier2")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CallableShimE2ETest : PIRTestBase() {

    private lateinit var cp: PIRClasspath

    companion object {
        // A capturing nested def + a user call site exercising the shim.
        val SOURCE = """
def cse_capturing(x):
    def inner(p):
        return x + p
    return inner(7)
""".trimIndent()
    }

    @BeforeAll
    fun setup() { cp = buildFromSource(SOURCE) }

    @AfterAll
    fun teardown() { cp.close() }

    @Test
    fun `capturing inner produces synthetic adapter class in module`() {
        val module = cp.modules.first()
        val adapters = module.classes.filter { it.name.startsWith("<closure_") }
        assertTrue(adapters.isNotEmpty(),
            "module should contain a synthesized adapter class; classes=${module.classes.map { it.name }}")
        // The adapter has __init__ and __call__.
        val adapter = adapters.first()
        val methodNames = adapter.methods.map { it.name }
        assertTrue("__init__" in methodNames, "adapter should expose __init__, got $methodNames")
        assertTrue("__call__" in methodNames, "adapter should expose __call__, got $methodNames")
    }

    @Test
    fun `outer's call site is a PIRCall on FlatLocal('inner')`() {
        val outer = cp.modules.flatMap { it.functions }.first { it.name == "cse_capturing" }
        val userCalls = outer.instList.filterIsInstance<PIRCall>().filter {
            (it.callee as? PIRLocalVar)?.name == "inner"
        }
        assertTrue(userCalls.isNotEmpty(), "expected a PIRCall on the bound inner local")
        // Call passes only user-supplied args (no implicit <self>).
        for (call in userCalls) {
            assertEquals(1, call.args.size, "expected one arg (7), got ${call.args}")
        }
    }

    @Test
    fun `bind site is a constructor call on the adapter class`() {
        val outer = cp.modules.flatMap { it.functions }.first { it.name == "cse_capturing" }
        val ctor = outer.instList.filterIsInstance<PIRCall>().firstOrNull { call ->
            val callee = call.callee
            callee is PIRGlobalRef &&
                callee.qualifiedName.substringAfterLast('.').let {
                    it.startsWith("<closure_") && !it.endsWith("_impl>")
                }
        }
        assertNotNull(ctor, "expected a PIRCall to the adapter class constructor")
        // Constructor receives one positional arg (the env dict).
        assertEquals(1, ctor!!.args.size)
    }
}
