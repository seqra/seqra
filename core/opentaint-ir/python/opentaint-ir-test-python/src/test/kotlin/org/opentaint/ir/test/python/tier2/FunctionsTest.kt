package org.opentaint.ir.test.python.tier2

import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Tag
import org.opentaint.ir.api.python.*
import org.opentaint.ir.test.python.PIRTestBase

@Tag("tier2")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class FunctionsTest : PIRTestBase() {

    private lateinit var cp: PIRClasspath

    companion object {
        val SOURCE = """
def fn_simple(x: int) -> int:
    return x

def fn_default(x: int = 0) -> int:
    return x

def fn_star_args(*args: int) -> int:
    return 0

def fn_kwargs(**kwargs: str) -> int:
    return 0

def fn_kw_only(*, x: int) -> int:
    return x

def fn_all_kinds(a: int, b: int = 0, *args: int, c: int, **kwargs: str) -> int:
    return a

def fn_recursive(n: int) -> int:
    if n <= 1:
        return 1
    return fn_recursive(n - 1)

def fn_call_keyword(a: int, b: int) -> int:
    return a + b

def fn_caller() -> int:
    return fn_call_keyword(a=1, b=2)

def fn_multi_return(x: int) -> int:
    if x > 0:
        return 1
    return -1

class FnClass:
    def method(self, x: int) -> int:
        return x

    @staticmethod
    def static_method(x: int) -> int:
        return x

    @classmethod
    def class_method(cls, x: int) -> int:
        return x

def fn_no_annotation(x):
    return x

def fn_generator(n: int):
    i = 0
    while i < n:
        yield i
        i = i + 1

async def fn_async(x: int) -> int:
    return x
        """.trimIndent()
    }

    @BeforeAll fun setup() { cp = buildFromSource(SOURCE) }
    @AfterAll fun tearDown() { cp.close() }
    private fun func(name: String) = cp.findFunctionOrNull("__test__.$name")!!

    @Test fun `simple function has 1 param`() {
        val f = func("fn_simple")
        assertEquals(1, f.parameters.size)
        assertEquals("x", f.parameters[0].name)
    }

    @Test fun `default param has hasDefault=true`() {
        val f = func("fn_default")
        assertTrue(f.parameters[0].hasDefault, "Parameter with default should have hasDefault=true")
    }

    @Test fun `star args has VAR_POSITIONAL kind`() {
        val f = func("fn_star_args")
        assertTrue(f.parameters.any { it.kind == PIRParameterKind.VAR_POSITIONAL },
            "Expected VAR_POSITIONAL, got: ${f.parameters.map { "${it.name}:${it.kind}" }}")
    }

    @Test fun `kwargs has VAR_KEYWORD kind`() {
        val f = func("fn_kwargs")
        assertTrue(f.parameters.any { it.kind == PIRParameterKind.VAR_KEYWORD },
            "Expected VAR_KEYWORD, got: ${f.parameters.map { "${it.name}:${it.kind}" }}")
    }

    @Test fun `kw_only has KEYWORD_ONLY kind`() {
        val f = func("fn_kw_only")
        assertTrue(f.parameters.any { it.kind == PIRParameterKind.KEYWORD_ONLY },
            "Expected KEYWORD_ONLY, got: ${f.parameters.map { "${it.name}:${it.kind}" }}")
    }

    @Test fun `all param kinds present`() {
        val f = func("fn_all_kinds")
        val kinds = f.parameters.map { it.kind }.toSet()
        assertTrue(PIRParameterKind.POSITIONAL_OR_KEYWORD in kinds || PIRParameterKind.POSITIONAL_ONLY in kinds)
        assertTrue(PIRParameterKind.VAR_POSITIONAL in kinds)
        assertTrue(PIRParameterKind.KEYWORD_ONLY in kinds)
        assertTrue(PIRParameterKind.VAR_KEYWORD in kinds)
    }

    @Test fun `recursive call produces PIRCall`() {
        val insts = func("fn_recursive").cfg.blocks.flatMap { it.instructions }
        val calls = insts.filterIsInstance<PIRCall>()
        assertTrue(calls.isNotEmpty(), "Expected PIRCall for recursive call")
    }

    @Test fun `keyword call has KEYWORD arg kind`() {
        val insts = func("fn_caller").cfg.blocks.flatMap { it.instructions }
        val calls = insts.filterIsInstance<PIRCall>()
        assertTrue(calls.any { call ->
            call.args.any { it.kind == PIRCallArgKind.KEYWORD }
        }, "Expected KEYWORD arg kind in call")
    }

    @Test fun `method has enclosingClass`() {
        val cls = cp.findClassOrNull("__test__.FnClass")
        assertNotNull(cls, "FnClass should be found")
        val m = cls!!.methods.find { it.name == "method" }
        assertNotNull(m, "method should exist in FnClass")
        assertNotNull(m!!.enclosingClass, "Method should have enclosingClass")
    }

    @Test fun `static method`() {
        val cls = cp.findClassOrNull("__test__.FnClass")!!
        val m = cls.methods.find { it.name == "static_method" }
        assertNotNull(m, "static_method should exist")
        assertTrue(m!!.isStaticMethod, "Expected isStaticMethod=true")
    }

    @Test fun `class method`() {
        val cls = cp.findClassOrNull("__test__.FnClass")!!
        val m = cls.methods.find { it.name == "class_method" }
        assertNotNull(m, "class_method should exist")
        assertTrue(m!!.isClassMethod, "Expected isClassMethod=true")
    }

    @Test fun `function with no annotations returns Any`() {
        val f = func("fn_no_annotation")
        assertTrue(f.returnType is PIRAnyType, "Unannotated function should return Any, got ${f.returnType}")
    }

    @Test fun `generator function`() {
        val f = func("fn_generator")
        assertTrue(f.isGenerator, "Expected isGenerator=true")
        val insts = f.cfg.blocks.flatMap { it.instructions }
        assertTrue(insts.any { it is PIRYield }, "Expected PIRYield in generator")
    }

    @Test fun `async function`() {
        val f = func("fn_async")
        assertTrue(f.isAsync, "Expected isAsync=true")
    }
}
