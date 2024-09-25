package org.opentaint.ir.impl

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.opentaint.ir.api.FieldUsageMode
import org.opentaint.ir.api.findClass
import org.opentaint.ir.compilationDatabase
import org.opentaint.ir.impl.index.ReversedUsages
import org.opentaint.ir.impl.index.reversedUsagesExt
import org.opentaint.ir.impl.usages.fields.FieldA
import org.opentaint.ir.impl.usages.fields.FieldB
import org.opentaint.ir.impl.usages.methods.MethodA

class SearchReversedUsagesTest : LibrariesMixin {

    private val db = runBlocking {
        compilationDatabase {
            predefinedDirOrJars = allClasspath
            useProcessJavaRuntime()
            installIndexes(ReversedUsages)
        }
    }

    private val cp = runBlocking { db.classpathSet(allClasspath) }

    @Test
    fun `classes read fields`() {
        val usages = fieldsUsages<FieldA>(FieldUsageMode.READ)

        assertEquals(
            sortedMapOf(
                "a" to setOf(
                    "org.opentaint.ir.impl.usages.fields.FieldA#<init>",
                    "org.opentaint.ir.impl.usages.fields.FieldA#isPositive",
                    "org.opentaint.ir.impl.usages.fields.FieldA#useCPrivate",
                    "org.opentaint.ir.impl.usages.fields.FieldAImpl#hello"
                ),
                "b" to setOf(
                    "org.opentaint.ir.impl.usages.fields.FieldA#isPositive",
                    "org.opentaint.ir.impl.usages.fields.FieldA#useA",
                ),
                "fieldB" to setOf(
                    "org.opentaint.ir.impl.usages.fields.FieldA#useCPrivate",
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
                    "org.opentaint.ir.impl.usages.fields.FieldA#<init>",
                    "org.opentaint.ir.impl.usages.fields.FieldA#useA",
                ),
                "b" to setOf(
                    "org.opentaint.ir.impl.usages.fields.FieldA#<init>"
                ),
                "fieldB" to setOf(
                    "org.opentaint.ir.impl.usages.fields.FieldA#<init>",
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
                    "org.opentaint.ir.impl.usages.fields.FakeFieldA#useCPrivate",
                    "org.opentaint.ir.impl.usages.fields.FieldA#useCPrivate",
                    "org.opentaint.ir.impl.usages.fields.FieldB#<init>",
                    "org.opentaint.ir.impl.usages.fields.FieldB#useCPrivate",
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
                "<init>" to setOf(
                    "org.opentaint.ir.impl.usages.methods.MethodB#hoho",
                    "org.opentaint.ir.impl.usages.methods.MethodC#<init>"
                ),
                "hello" to setOf(
                    "org.opentaint.ir.impl.usages.methods.MethodB#hoho",
                    "org.opentaint.ir.impl.usages.methods.MethodC#hello",
                ),
                "hello1" to setOf(
                    "org.opentaint.ir.impl.usages.methods.MethodB#hoho"
                )
            ),
            usages
        )
    }

    private inline fun <reified T> fieldsUsages(mode: FieldUsageMode = FieldUsageMode.WRITE): Map<String, Set<String>> {
        return runBlocking {
            val classId = cp.findClass<T>()

            val fields = classId.fields()
            val usageExt = cp.reversedUsagesExt

            fields.map {
                it.name to usageExt.findUsages(it, mode).map { it.classId.name + "#" + it.name }.toSortedSet()
            }
                .toMap()
                .filterNot { it.value.isEmpty() }
                .toSortedMap()
        }
    }

    private inline fun <reified T> methodsUsages(): Map<String, Set<String>> {
        return runBlocking {
            val classId = cp.findClass<T>()
            val methods = classId.methods()
            val usageExt = cp.reversedUsagesExt

            methods.map {
                it.name to usageExt.findUsages(it).map { it.classId.name + "#" + it.name }.toSortedSet()
            }
                .toMap()
                .filterNot { it.value.isEmpty() }
                .toSortedMap()
        }
    }

    @AfterEach
    fun cleanup() {
        cp.close()
        db.close()
    }
}