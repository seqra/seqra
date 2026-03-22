package org.opentaint.ir.test.python.tier2

import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Tag
import org.opentaint.ir.api.python.*
import org.opentaint.ir.test.python.PIRTestBase

@Tag("tier2")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AsyncConstructsTest : PIRTestBase() {

    private lateinit var cp: PIRClasspath

    companion object {
        val SOURCE = """
async def fetch_data(url):
    result = await do_fetch(url)
    return result

async def multi_await(a, b):
    x = await process(a)
    y = await process(b)
    return x + y

async def async_for_loop(source):
    total = 0
    async for item in source:
        total = total + item
    return total

async def async_with_mgr(resource):
    async with open_resource(resource) as r:
        data = r.read()
    return data

async def async_generator(items):
    for item in items:
        result = await transform(item)
        yield result

async def await_in_conditional(flag, a, b):
    if flag:
        result = await process(a)
    else:
        result = await process(b)
    return result

async def await_in_try(url):
    try:
        result = await do_fetch(url)
    except Exception as e:
        result = None
    return result

async def async_with_normal_code(x, y):
    total = x + y
    label = "sum"
    return total

async def nested_await(a, b, c):
    x = await step_one(a)
    y = await step_two(x, b)
    z = await step_three(y, c)
    return z

async def await_in_loop(items):
    results = []
    for item in items:
        r = await process(item)
        results.append(r)
    return results

async def async_with_no_as(resource):
    async with acquire_lock(resource):
        do_work(resource)

async def async_yield_multiple(items):
    for item in items:
        yield item
    yield None
        """.trimIndent()
    }

    @BeforeAll fun setup() { cp = buildFromSource(SOURCE) }
    @AfterAll fun tearDown() { cp.close() }

    private fun findFunc(name: String): PIRFunction {
        for (m in cp.modules) {
            for (f in m.functions) {
                if (f.qualifiedName.endsWith(name)) return f
            }
            for (c in m.classes) {
                for (f in c.methods) {
                    if (f.qualifiedName.endsWith(name)) return f
                }
            }
        }
        throw AssertionError("Function not found: $name")
    }

    private fun insts(name: String) = findFunc(name).cfg.blocks.flatMap { it.instructions }
    private inline fun <reified T : PIRInstruction> allOf(name: String): List<T> =
        insts(name).filterIsInstance<T>()

    // ─── 1. async def with await ────────────────────────────

    @Test
    fun `fetch_data - isAsync is true`() {
        assertTrue(findFunc("fetch_data").isAsync)
    }

    @Test
    fun `fetch_data - emits PIRAwait instruction`() {
        val awaits = allOf<PIRAwait>("fetch_data")
        assertTrue(awaits.isNotEmpty(), "Expected PIRAwait for 'await do_fetch(url)'")
    }

    @Test
    fun `fetch_data - has non-empty CFG`() {
        val blocks = findFunc("fetch_data").cfg.blocks
        assertTrue(blocks.isNotEmpty(), "async def should have non-empty CFG")
        assertTrue(blocks.flatMap { it.instructions }.isNotEmpty(), "CFG should contain instructions")
    }

    // ─── 2. Multiple awaits ─────────────────────────────────

    @Test
    fun `multi_await - emits at least two PIRAwait instructions`() {
        val awaits = allOf<PIRAwait>("multi_await")
        assertTrue(awaits.size >= 2,
            "Expected >= 2 PIRAwait for two await expressions, got ${awaits.size}")
    }

    @Test
    fun `multi_await - isAsync is true`() {
        assertTrue(findFunc("multi_await").isAsync)
    }

    // ─── 3. async for loop ──────────────────────────────────

    @Test
    fun `async_for_loop - isAsync is true`() {
        assertTrue(findFunc("async_for_loop").isAsync)
    }

    @Test
    fun `async_for_loop - has iteration structure`() {
        val getIters = allOf<PIRGetIter>("async_for_loop")
        val nextIters = allOf<PIRNextIter>("async_for_loop")
        assertTrue(getIters.isNotEmpty() || nextIters.isNotEmpty(),
            "async for should produce iteration instructions (PIRGetIter/PIRNextIter)")
    }

    @Test
    fun `async_for_loop - has multiple blocks for loop structure`() {
        val blocks = findFunc("async_for_loop").cfg.blocks
        assertTrue(blocks.size >= 3,
            "async for loop should produce >= 3 blocks (header, body, exit), got ${blocks.size}")
    }

    // ─── 4. async with context manager ──────────────────────

    @Test
    fun `async_with_mgr - isAsync is true`() {
        assertTrue(findFunc("async_with_mgr").isAsync)
    }

    @Test
    fun `async_with_mgr - has enter dunder calls`() {
        val attrs = allOf<PIRLoadAttr>("async_with_mgr")
        val hasAsyncEnter = attrs.any { it.attribute == "__aenter__" }
        val hasSyncEnter = attrs.any { it.attribute == "__enter__" }
        assertTrue(hasAsyncEnter || hasSyncEnter,
            "async with should produce __aenter__ or __enter__ LoadAttr, found: ${attrs.map { it.attribute }}")
    }

    @Test
    fun `async_with_mgr - has exit dunder calls`() {
        val attrs = allOf<PIRLoadAttr>("async_with_mgr")
        val hasAsyncExit = attrs.any { it.attribute == "__aexit__" }
        val hasSyncExit = attrs.any { it.attribute == "__exit__" }
        assertTrue(hasAsyncExit || hasSyncExit,
            "async with should produce __aexit__ or __exit__ LoadAttr, found: ${attrs.map { it.attribute }}")
    }

    // ─── 5. async generator (yield inside async def) ────────

    @Test
    fun `async_generator - isAsync and isGenerator`() {
        val f = findFunc("async_generator")
        assertTrue(f.isAsync, "async def with yield should be async")
        assertTrue(f.isGenerator, "async def with yield should be a generator")
    }

    @Test
    fun `async_generator - has PIRYield instructions`() {
        val yields = allOf<PIRYield>("async_generator")
        assertTrue(yields.isNotEmpty(), "async generator should emit PIRYield")
    }

    @Test
    fun `async_generator - has PIRAwait for await inside body`() {
        val awaits = allOf<PIRAwait>("async_generator")
        assertTrue(awaits.isNotEmpty(), "async generator with await should emit PIRAwait")
    }

    // ─── 6. await in conditional bodies ─────────────────────

    @Test
    fun `await_in_conditional - has branch and two awaits`() {
        val branches = allOf<PIRBranch>("await_in_conditional")
        assertTrue(branches.isNotEmpty(), "if/else should produce PIRBranch")
        val awaits = allOf<PIRAwait>("await_in_conditional")
        assertTrue(awaits.size >= 2,
            "Expected >= 2 PIRAwait (one per branch), got ${awaits.size}")
    }

    @Test
    fun `await_in_conditional - isAsync is true`() {
        assertTrue(findFunc("await_in_conditional").isAsync)
    }

    // ─── 7. await in try/except ─────────────────────────────

    @Test
    fun `await_in_try - isAsync is true`() {
        assertTrue(findFunc("await_in_try").isAsync)
    }

    @Test
    fun `await_in_try - has PIRAwait instruction`() {
        val awaits = allOf<PIRAwait>("await_in_try")
        assertTrue(awaits.isNotEmpty(), "await inside try should emit PIRAwait")
    }

    @Test
    fun `await_in_try - has PIRExceptHandler`() {
        val handlers = allOf<PIRExceptHandler>("await_in_try")
        assertTrue(handlers.isNotEmpty(), "try/except should emit PIRExceptHandler")
    }

    // ─── 8. async def with only normal code ─────────────────

    @Test
    fun `async_with_normal_code - isAsync even without await`() {
        assertTrue(findFunc("async_with_normal_code").isAsync)
    }

    @Test
    fun `async_with_normal_code - has no PIRAwait`() {
        val awaits = allOf<PIRAwait>("async_with_normal_code")
        assertTrue(awaits.isEmpty(),
            "async def without await should have no PIRAwait, got ${awaits.size}")
    }

    @Test
    fun `async_with_normal_code - has normal arithmetic and return`() {
        val all = insts("async_with_normal_code")
        assertTrue(all.any { it is PIRBinOp }, "Expected arithmetic (PIRBinOp)")
        assertTrue(all.any { it is PIRReturn }, "Expected PIRReturn")
    }

    // ─── Additional: chained awaits ─────────────────────────

    @Test
    fun `nested_await - emits three PIRAwait instructions`() {
        val awaits = allOf<PIRAwait>("nested_await")
        assertTrue(awaits.size >= 3,
            "Expected >= 3 PIRAwait for chained await calls, got ${awaits.size}")
    }

    // ─── Additional: await in regular for loop ──────────────

    @Test
    fun `await_in_loop - has iteration and PIRAwait`() {
        val getIters = allOf<PIRGetIter>("await_in_loop")
        val nextIters = allOf<PIRNextIter>("await_in_loop")
        assertTrue(getIters.isNotEmpty() && nextIters.isNotEmpty(),
            "for loop should produce PIRGetIter/PIRNextIter")
        val awaits = allOf<PIRAwait>("await_in_loop")
        assertTrue(awaits.isNotEmpty(), "await inside for loop body should emit PIRAwait")
    }

    // ─── Additional: async with without as target ───────────

    @Test
    fun `async_with_no_as - has enter and exit dunders without as target`() {
        val attrs = allOf<PIRLoadAttr>("async_with_no_as")
        val hasEnter = attrs.any { it.attribute == "__aenter__" || it.attribute == "__enter__" }
        val hasExit = attrs.any { it.attribute == "__aexit__" || it.attribute == "__exit__" }
        assertTrue(hasEnter, "async with (no as) should still produce enter dunder, found: ${attrs.map { it.attribute }}")
        assertTrue(hasExit, "async with (no as) should still produce exit dunder, found: ${attrs.map { it.attribute }}")
    }

    // ─── Additional: async generator with multiple yields ───

    @Test
    fun `async_yield_multiple - has multiple PIRYield instructions`() {
        val yields = allOf<PIRYield>("async_yield_multiple")
        assertTrue(yields.size >= 2,
            "Expected >= 2 PIRYield for async generator with multiple yields, got ${yields.size}")
    }
}
