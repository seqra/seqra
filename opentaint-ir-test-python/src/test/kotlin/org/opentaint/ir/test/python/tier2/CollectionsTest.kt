package org.opentaint.ir.test.python.tier2

import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Tag
import org.opentaint.ir.api.python.*
import org.opentaint.ir.test.python.PIRTestBase

@Tag("tier2")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CollectionsTest : PIRTestBase() {

    private lateinit var cp: PIRClasspath

    companion object {
        val SOURCE = """
def col_list() -> list:
    return [1, 2, 3]

def col_tuple() -> tuple:
    return (1, 2, 3)

def col_set() -> set:
    return {1, 2, 3}

def col_dict() -> dict:
    return {"a": 1, "b": 2}

def col_empty_list() -> list:
    return []

def col_empty_dict() -> dict:
    return {}

def col_subscript_load(items: list) -> int:
    return items[0]

def col_subscript_store(items: list) -> None:
    items[0] = 99

def col_slice(items: list) -> list:
    return items[1:3]

def col_unpack(pair: tuple) -> int:
    a, b = pair
    return a

def col_extended_unpack(items: list) -> list:
    first, *rest = items
    return rest

def col_delete_local() -> None:
    x = 1
    del x

def col_delete_attr(obj: object) -> None:
    del obj.attr  # type: ignore

def col_delete_subscript(d: dict) -> None:
    del d["key"]

def col_attr_load(obj: object) -> object:
    return obj.__class__  # type: ignore

def col_attr_store(obj: object) -> None:
    obj.x = 42  # type: ignore
        """.trimIndent()
    }

    @BeforeAll fun setup() { cp = buildFromSource(SOURCE) }
    @AfterAll fun tearDown() { cp.close() }
    private fun insts(name: String) = cp.findFunctionOrNull("__test__.$name")!!
        .cfg.blocks.flatMap { it.instructions }

    @Test fun `list literal`() {
        val builds = insts("col_list").filterIsInstance<PIRBuildList>()
        assertTrue(builds.isNotEmpty())
        assertTrue(builds.any { it.elements.size == 3 })
    }

    @Test fun `tuple literal`() {
        val builds = insts("col_tuple").filterIsInstance<PIRBuildTuple>()
        assertTrue(builds.isNotEmpty())
    }

    @Test fun `set literal`() {
        val builds = insts("col_set").filterIsInstance<PIRBuildSet>()
        assertTrue(builds.isNotEmpty())
    }

    @Test fun `dict literal`() {
        val builds = insts("col_dict").filterIsInstance<PIRBuildDict>()
        assertTrue(builds.isNotEmpty())
        assertTrue(builds.any { it.keys.size == 2 })
    }

    @Test fun `empty list`() {
        val builds = insts("col_empty_list").filterIsInstance<PIRBuildList>()
        assertTrue(builds.any { it.elements.isEmpty() })
    }

    @Test fun `empty dict`() {
        val builds = insts("col_empty_dict").filterIsInstance<PIRBuildDict>()
        assertTrue(builds.any { it.keys.isEmpty() })
    }

    @Test fun `subscript load`() {
        assertTrue(insts("col_subscript_load").any { it is PIRLoadSubscript })
    }

    @Test fun `subscript store`() {
        assertTrue(insts("col_subscript_store").any { it is PIRStoreSubscript })
    }

    @Test fun `slice produces BuildSlice`() {
        assertTrue(insts("col_slice").any { it is PIRBuildSlice })
    }

    @Test fun `tuple unpack`() {
        val unpacks = insts("col_unpack").filterIsInstance<PIRUnpack>()
        assertTrue(unpacks.isNotEmpty())
        assertTrue(unpacks.any { it.targets.size == 2 })
    }

    @Test fun `extended unpack with star`() {
        val unpacks = insts("col_extended_unpack").filterIsInstance<PIRUnpack>()
        assertTrue(unpacks.isNotEmpty())
        assertTrue(unpacks.any { it.starIndex >= 0 })
    }

    @Test fun `delete local`() {
        assertTrue(insts("col_delete_local").any { it is PIRDeleteLocal })
    }

    @Test fun `delete attr`() {
        assertTrue(insts("col_delete_attr").any { it is PIRDeleteAttr })
    }

    @Test fun `delete subscript`() {
        assertTrue(insts("col_delete_subscript").any { it is PIRDeleteSubscript })
    }

    @Test fun `load attr`() {
        assertTrue(insts("col_attr_load").any { it is PIRLoadAttr })
    }

    @Test fun `store attr`() {
        assertTrue(insts("col_attr_store").any { it is PIRStoreAttr })
    }
}
