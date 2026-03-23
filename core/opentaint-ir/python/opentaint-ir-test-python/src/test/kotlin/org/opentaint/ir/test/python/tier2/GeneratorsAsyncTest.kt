package org.opentaint.ir.test.python.tier2

import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Tag
import org.opentaint.ir.api.python.*
import org.opentaint.ir.test.python.PIRTestBase

@Tag("tier2")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class GeneratorsAsyncTest : PIRTestBase() {

    private lateinit var cp: PIRClasspath

    companion object {
        val SOURCE = """
def gen_simple():
    yield 1
    yield 2
    yield 3

def gen_yield_value(items: list):
    for item in items:
        yield item

def gen_yield_from(inner: list):
    yield from inner

async def async_simple(x: int) -> int:
    return x + 1

async def async_await(x: int) -> int:
    y = await async_simple(x)
    return y

def gen_with_return():
    yield 1
    return

def gen_conditional(items: list):
    for item in items:
        if item > 0:
            yield item
        """.trimIndent()
    }

    @BeforeAll fun setup() { cp = buildFromSource(SOURCE) }
    @AfterAll fun tearDown() { cp.close() }
    private fun func(name: String) = cp.findFunctionOrNull("__test__.$name")!!
    private fun insts(name: String) = func(name).cfg.blocks.flatMap { it.instructions }

    @Test fun `simple generator has isGenerator`() {
        assertTrue(func("gen_simple").isGenerator)
    }

    @Test fun `generator yields PIRYield`() {
        val yields = insts("gen_simple").filterIsInstance<PIRYield>()
        assertTrue(yields.size >= 2, "Expected multiple yields, got ${yields.size}")
    }

    @Test fun `generator yield value`() {
        val yields = insts("gen_yield_value").filterIsInstance<PIRYield>()
        assertTrue(yields.isNotEmpty())
        assertTrue(yields.any { it.value != null })
    }

    @Test fun `yield from produces PIRYieldFrom`() {
        val yfs = insts("gen_yield_from").filterIsInstance<PIRYieldFrom>()
        assertTrue(yfs.isNotEmpty(), "Expected PIRYieldFrom")
    }

    @Test fun `async function has isAsync`() {
        assertTrue(func("async_simple").isAsync)
    }

    @Test fun `await produces PIRAwait`() {
        val awaits = insts("async_await").filterIsInstance<PIRAwait>()
        assertTrue(awaits.isNotEmpty(), "Expected PIRAwait instruction")
    }

    @Test fun `generator with return`() {
        val f = func("gen_with_return")
        assertTrue(f.isGenerator)
        assertTrue(insts("gen_with_return").any { it is PIRYield })
        assertTrue(insts("gen_with_return").any { it is PIRReturn })
    }

    @Test fun `conditional yield in loop`() {
        val f = func("gen_conditional")
        assertTrue(f.isGenerator)
        assertTrue(insts("gen_conditional").any { it is PIRYield })
        assertTrue(insts("gen_conditional").any { it is PIRBranch })
    }
}
