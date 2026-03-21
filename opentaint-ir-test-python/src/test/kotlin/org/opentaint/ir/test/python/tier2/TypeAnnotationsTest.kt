package org.opentaint.ir.test.python.tier2

import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Tag
import org.opentaint.ir.api.python.*
import org.opentaint.ir.test.python.PIRTestBase

@Tag("tier2")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TypeAnnotationsTest : PIRTestBase() {

    private lateinit var cp: PIRClasspath

    companion object {
        val SOURCE = """
from typing import Optional, Union, Callable, Any, Tuple

def ta_int(x: int) -> int:
    return x

def ta_str(x: str) -> str:
    return x

def ta_float(x: float) -> float:
    return x

def ta_bool(x: bool) -> bool:
    return x

def ta_list_of_int(x: list[int]) -> list[int]:
    return x

def ta_optional(x: Optional[int]) -> Optional[int]:
    return x

def ta_any(x: Any) -> Any:
    return x

def ta_none_return(x: int) -> None:
    pass

def ta_union(x: Union[int, str]) -> Union[int, str]:
    return x

def ta_callable(f: Callable[[int], str]) -> str:
    return f(0)

def ta_tuple(x: Tuple[int, str]) -> Tuple[int, str]:
    return x

def ta_no_annotation(x):
    return x
        """.trimIndent()
    }

    @BeforeAll fun setup() { cp = buildFromSource(SOURCE) }
    @AfterAll fun tearDown() { cp.close() }
    private fun func(name: String) = cp.findFunctionOrNull("__test__.$name")!!

    @Test fun `int type maps to builtins_int`() {
        val p = func("ta_int").parameters[0]
        assertTrue(p.type is PIRClassType)
        assertEquals("builtins.int", (p.type as PIRClassType).qualifiedName)
    }

    @Test fun `str type maps to builtins_str`() {
        val p = func("ta_str").parameters[0]
        assertTrue(p.type is PIRClassType)
        assertEquals("builtins.str", (p.type as PIRClassType).qualifiedName)
    }

    @Test fun `float type maps to builtins_float`() {
        val p = func("ta_float").parameters[0]
        assertTrue(p.type is PIRClassType)
        assertEquals("builtins.float", (p.type as PIRClassType).qualifiedName)
    }

    @Test fun `bool type maps to builtins_bool`() {
        val p = func("ta_bool").parameters[0]
        assertTrue(p.type is PIRClassType)
        assertEquals("builtins.bool", (p.type as PIRClassType).qualifiedName)
    }

    @Test fun `list of int has type args`() {
        val p = func("ta_list_of_int").parameters[0]
        assertTrue(p.type is PIRClassType)
        val ct = p.type as PIRClassType
        assertEquals("builtins.list", ct.qualifiedName)
        assertTrue(ct.typeArgs.isNotEmpty(), "list[int] should have type args")
    }

    @Test fun `Optional maps to union with None or optional flag`() {
        val p = func("ta_optional").parameters[0]
        // Optional[int] can be either PIRUnionType(int, None) or PIRClassType with isOptional=true
        val isOptional = when (p.type) {
            is PIRUnionType -> true
            is PIRClassType -> (p.type as PIRClassType).isOptional
            else -> false
        }
        assertTrue(isOptional, "Optional[int] should be Union or have isOptional=true, got ${p.type}")
    }

    @Test fun `Any type`() {
        val p = func("ta_any").parameters[0]
        assertTrue(p.type is PIRAnyType, "Any should map to PIRAnyType, got ${p.type}")
    }

    @Test fun `None return type`() {
        val f = func("ta_none_return")
        assertTrue(f.returnType is PIRNoneType, "None return should be PIRNoneType, got ${f.returnType}")
    }

    @Test fun `Union type`() {
        val p = func("ta_union").parameters[0]
        assertTrue(p.type is PIRUnionType, "Union should map to PIRUnionType, got ${p.type}")
        val u = p.type as PIRUnionType
        assertTrue(u.members.size >= 2, "Union should have >= 2 members")
    }

    @Test fun `Callable type`() {
        val p = func("ta_callable").parameters[0]
        assertTrue(p.type is PIRFunctionType, "Callable should map to PIRFunctionType, got ${p.type}")
    }

    @Test fun `Tuple type`() {
        val p = func("ta_tuple").parameters[0]
        assertTrue(p.type is PIRTupleType, "Tuple should map to PIRTupleType, got ${p.type}")
    }

    @Test fun `no annotation maps to Any`() {
        val p = func("ta_no_annotation").parameters[0]
        assertTrue(p.type is PIRAnyType, "No annotation should be PIRAnyType, got ${p.type}")
    }
}
