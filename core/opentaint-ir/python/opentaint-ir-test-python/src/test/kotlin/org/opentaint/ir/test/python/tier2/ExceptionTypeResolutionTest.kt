package org.opentaint.ir.test.python.tier2

import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Tag
import org.opentaint.ir.api.python.*
import org.opentaint.ir.test.python.PIRTestBase

@Tag("tier2")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ExceptionTypeResolutionTest : PIRTestBase() {

    private lateinit var cp: PIRClasspath

    companion object {
        val SOURCE = """
def etr_value_error() -> str:
    try:
        raise ValueError()
    except ValueError as e:
        return str(e)
    return ""

def etr_type_error() -> str:
    try:
        x = 1 + "a"  # type: ignore
    except TypeError:
        return "caught"
    return ""

def etr_multiple_except() -> int:
    try:
        x = int("abc")
    except ValueError:
        return 1
    except TypeError:
        return 2
    except RuntimeError:
        return 3
    return 0

def etr_bare_except() -> None:
    try:
        pass
    except:
        pass

def etr_tuple_except() -> None:
    try:
        pass
    except (ValueError, TypeError):
        pass

def etr_base_class() -> str:
    try:
        pass
    except Exception as e:
        return str(e)
    return ""
        """.trimIndent()
    }

    @BeforeAll fun setup() { cp = buildFromSource(SOURCE) }
    @AfterAll fun tearDown() { cp.close() }

    private fun func(name: String) = cp.findFunctionOrNull("__test__.$name")!!
    private fun insts(name: String) = func(name).cfg.blocks.flatMap { it.instructions }
    private fun handlers(name: String) = insts(name).filterIsInstance<PIRExceptHandler>()

    private fun hasExceptionType(handlerList: List<PIRExceptHandler>, typeName: String): Boolean =
        handlerList.any { handler ->
            handler.exceptionTypes.any { type ->
                (type as? PIRClassType)?.qualifiedName?.contains(typeName) == true
            }
        }

    @Test
    fun `etr_value_error - has except handler`() {
        assertTrue(handlers("etr_value_error").isNotEmpty())
    }

    @Test
    fun `etr_value_error - resolves ValueError type`() {
        assertTrue(hasExceptionType(handlers("etr_value_error"), "ValueError"),
            "Expected 'ValueError' in exceptionTypes")
    }

    @Test
    fun `etr_value_error - handler has target for as e`() {
        assertTrue(handlers("etr_value_error").any { it.target != null })
    }

    @Test
    fun `etr_value_error - has PIRRaise`() {
        assertTrue(insts("etr_value_error").filterIsInstance<PIRRaise>().isNotEmpty())
    }

    @Test
    fun `etr_type_error - has except handler`() {
        assertTrue(handlers("etr_type_error").isNotEmpty())
    }

    @Test
    fun `etr_type_error - resolves TypeError type`() {
        assertTrue(hasExceptionType(handlers("etr_type_error"), "TypeError"),
            "Expected 'TypeError' in exceptionTypes")
    }

    @Test
    fun `etr_type_error - handler has no target`() {
        assertTrue(handlers("etr_type_error").any { it.target == null })
    }

    @Test
    fun `etr_multiple_except - has three handlers`() {
        assertTrue(handlers("etr_multiple_except").size >= 3,
            "Expected >= 3 handlers, got ${handlers("etr_multiple_except").size}")
    }

    @Test
    fun `etr_multiple_except - has ValueError handler`() {
        assertTrue(hasExceptionType(handlers("etr_multiple_except"), "ValueError"))
    }

    @Test
    fun `etr_multiple_except - has TypeError handler`() {
        assertTrue(hasExceptionType(handlers("etr_multiple_except"), "TypeError"))
    }

    @Test
    fun `etr_multiple_except - has RuntimeError handler`() {
        assertTrue(hasExceptionType(handlers("etr_multiple_except"), "RuntimeError"))
    }

    @Test
    fun `etr_multiple_except - each handler has one type`() {
        for (handler in handlers("etr_multiple_except")) {
            assertEquals(1, handler.exceptionTypes.size,
                "Expected 1 exception type per handler")
        }
    }

    @Test
    fun `etr_bare_except - has handler`() {
        assertTrue(handlers("etr_bare_except").isNotEmpty())
    }

    @Test
    fun `etr_bare_except - has empty exceptionTypes`() {
        assertTrue(handlers("etr_bare_except").any { it.exceptionTypes.isEmpty() },
            "Expected empty exceptionTypes for bare except")
    }

    @Test
    fun `etr_bare_except - has no target`() {
        assertTrue(handlers("etr_bare_except").any { it.target == null })
    }

    @Test
    fun `etr_tuple_except - has handler`() {
        assertTrue(handlers("etr_tuple_except").isNotEmpty())
    }

    @Test
    fun `etr_tuple_except - has two exception types`() {
        assertTrue(handlers("etr_tuple_except").any { it.exceptionTypes.size == 2 },
            "Expected 2 exception types for tuple except")
    }

    @Test
    fun `etr_tuple_except - includes ValueError`() {
        assertTrue(hasExceptionType(handlers("etr_tuple_except"), "ValueError"))
    }

    @Test
    fun `etr_tuple_except - includes TypeError`() {
        assertTrue(hasExceptionType(handlers("etr_tuple_except"), "TypeError"))
    }

    @Test
    fun `etr_base_class - has handler`() {
        assertTrue(handlers("etr_base_class").isNotEmpty())
    }

    @Test
    fun `etr_base_class - resolves builtins Exception`() {
        assertTrue(hasExceptionType(handlers("etr_base_class"), "builtins.Exception"))
    }

    @Test
    fun `etr_base_class - has target for as e`() {
        assertTrue(handlers("etr_base_class").any { it.target != null })
    }

    @Test
    fun `etr_base_class - has one exception type`() {
        assertTrue(handlers("etr_base_class").any { it.exceptionTypes.size == 1 })
    }
}
