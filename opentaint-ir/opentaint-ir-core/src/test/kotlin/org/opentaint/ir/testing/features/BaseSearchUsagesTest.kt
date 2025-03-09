package org.opentaint.ir.testing.features

import kotlinx.coroutines.runBlocking
import org.opentaint.ir.api.FieldUsageMode
import org.opentaint.ir.api.ext.CONSTRUCTOR
import org.opentaint.ir.api.ext.findClass
import org.opentaint.ir.impl.features.InMemoryHierarchy
import org.opentaint.ir.impl.features.Usages
import org.opentaint.ir.impl.features.usagesExt
import org.opentaint.ir.testing.BaseTest
import org.opentaint.ir.testing.WithDB
import org.opentaint.ir.testing.usages.fields.FieldA
import org.opentaint.ir.testing.usages.fields.FieldB
import org.opentaint.ir.testing.usages.methods.MethodA
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.system.measureTimeMillis

abstract class BaseSearchUsagesTest : BaseTest() {

    @Test
    fun `classes read fields`() {
        val usages = fieldsUsages<FieldA>(FieldUsageMode.READ)
        assertEquals(
            sortedMapOf(
                "a" to setOf(
                    "org.opentaint.ir.testing.usages.fields.FieldA#<init>",
                    "org.opentaint.ir.testing.usages.fields.FieldA#isPositive",
                    "org.opentaint.ir.testing.usages.fields.FieldA#useCPrivate",
                    "org.opentaint.ir.testing.usages.fields.FieldAImpl#hello"
                ),
                "b" to setOf(
                    "org.opentaint.ir.testing.usages.fields.FieldA#isPositive",
                    "org.opentaint.ir.testing.usages.fields.FieldA#useA",
                ),
                "fieldB" to setOf(
                    "org.opentaint.ir.testing.usages.fields.FieldA#useCPrivate",
                )
            ),
            usages
        )
    }

    @Test
    fun `classes write fields`() {
        val usages = fieldsUsages<FieldA>()
        assertEquals(
            sortedMapOf(
                "a" to setOf(
                    "org.opentaint.ir.testing.usages.fields.FieldA#<init>",
                    "org.opentaint.ir.testing.usages.fields.FieldA#useA",
                ),
                "b" to setOf(
                    "org.opentaint.ir.testing.usages.fields.FieldA#<init>"
                ),
                "fieldB" to setOf(
                    "org.opentaint.ir.testing.usages.fields.FieldA#<init>",
                )
            ),
            usages
        )
    }

    @Test
    fun `classes write fields with rebuild`() {
        val time = measureTimeMillis {
            runBlocking {
                cp.db.rebuildFeatures()
            }
        }
        println("Features rebuild in ${time}ms")
        val usages = fieldsUsages<FieldA>()
        assertEquals(
            sortedMapOf(
                "a" to setOf(
                    "org.opentaint.ir.testing.usages.fields.FieldA#<init>",
                    "org.opentaint.ir.testing.usages.fields.FieldA#useA",
                ),
                "b" to setOf(
                    "org.opentaint.ir.testing.usages.fields.FieldA#<init>"
                ),
                "fieldB" to setOf(
                    "org.opentaint.ir.testing.usages.fields.FieldA#<init>",
                )
            ),
            usages
        )
    }

    @Test
    fun `classes write fields coupled`() {
        val usages = fieldsUsages<FieldB>()
        assertEquals(
            sortedMapOf(
                "c" to setOf(
                    "org.opentaint.ir.testing.usages.fields.FakeFieldA#useCPrivate",
                    "org.opentaint.ir.testing.usages.fields.FieldA#useCPrivate",
                    "org.opentaint.ir.testing.usages.fields.FieldB#<init>",
                    "org.opentaint.ir.testing.usages.fields.FieldB#useCPrivate",
                )
            ),
            usages
        )
    }

    @Test
    fun `classes methods usages`() {
        val usages = methodsUsages<MethodA>()
        assertEquals(
            sortedMapOf(
                CONSTRUCTOR to setOf(
                    "org.opentaint.ir.testing.usages.methods.MethodB#hoho",
                    "org.opentaint.ir.testing.usages.methods.MethodC#<init>"
                ),
                "hello" to setOf(
                    "org.opentaint.ir.testing.usages.methods.MethodB#hoho",
                    "org.opentaint.ir.testing.usages.methods.MethodC#hello",
                )
            ),
            usages
        )
    }

    @Test
    fun `find usages of Runnable#run method`() {
        runBlocking {
            val ext = cp.usagesExt()
            val runMethod = cp.findClass<Runnable>().declaredMethods.first()
            assertEquals("run", runMethod.name)
            val result = ext.findUsages(runMethod).toList()
            assertTrue(result.size > 50)
        }
    }

    @Test
    fun `find usages of System#out field`() {
        runBlocking {
            val ext = cp.usagesExt()
            val invokeStaticField = cp.findClass<System>().declaredFields.first { it.name == "out" }
            val result = ext.findUsages(invokeStaticField, FieldUsageMode.READ).toList()
            assertTrue(result.size > 500)
        }
    }

    private inline fun <reified T> fieldsUsages(mode: FieldUsageMode = FieldUsageMode.WRITE): Map<String, Set<String>> {
        return runBlocking {
            with(cp.usagesExt()) {
                val classId = cp.findClass<T>()

                val fields = classId.declaredFields

                fields.associate {
                    it.name to findUsages(it, mode).map { it.enclosingClass.name + "#" + it.name }.toSortedSet()
                }.filterNot { it.value.isEmpty() }.toSortedMap()
            }
        }
    }

    private inline fun <reified T> methodsUsages(): Map<String, Set<String>> {
        return runBlocking {
            with(cp.usagesExt()) {
                val classId = cp.findClass<T>()
                val methods = classId.declaredMethods

                methods.associate {
                    it.name to findUsages(it).map { it.enclosingClass.name + "#" + it.name }.toSortedSet()
                }.filterNot { it.value.isEmpty() }.toSortedMap()
            }
        }
    }

}

class InMemoryHierarchySearchUsagesTest : BaseSearchUsagesTest() {
    companion object : WithDB(Usages, InMemoryHierarchy)
}

class SearchUsagesTest : BaseSearchUsagesTest() {
    companion object : WithDB(Usages)
}
