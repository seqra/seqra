package org.opentaint.opentaint-ir.impl

import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.opentaint.opentaint-ir.api.JIRClasspath
import org.opentaint.opentaint-ir.api.ext.findClass
import org.opentaint.opentaint-ir.api.ext.findFieldsUsedIn
import org.opentaint.opentaint-ir.api.ext.findMethodsUsedIn
import org.opentaint.opentaint-ir.impl.features.Usages

class DirectUsagesTest : BaseTest() {

    companion object : WithDB(Usages)

    @Test
    fun `find methods used in method`() {
        val usages = cp.methodsUsages<org.opentaint.opentaint-ir.impl.usages.direct.DirectA>()

        assertEquals(
            listOf(
                "<init>" to listOf("java.lang.Object#<init>"),
                "setCalled" to listOf(
                    "java.io.PrintStream#println",
                ),
                "newSmth" to listOf(
                    "com.google.common.collect.Lists#newArrayList",
                    "java.lang.Integer#valueOf",
                    "java.util.ArrayList#add",
                    "java.io.PrintStream#println",
                )
            ),
            usages
        )
    }

    @Test
    fun `find methods used in method with broken classpath`() {
        val cp = runBlocking {
            db.classpath(allClasspath - guavaLib)
        }
        cp.use {
            val usages = cp.methodsUsages<org.opentaint.opentaint-ir.impl.usages.direct.DirectA>()

            assertEquals(
                listOf(
                    "<init>" to listOf("java.lang.Object#<init>"),
                    "setCalled" to listOf(
                        "java.io.PrintStream#println",
                    ),
                    "newSmth" to listOf(
                        "java.lang.Integer#valueOf",
                        "java.util.ArrayList#add",
                        "java.io.PrintStream#println",
                    )
                ),
                usages
            )
        }
    }

    @Test
    fun `find fields used in method`() {
        val usages = cp.fieldsUsages<org.opentaint.opentaint-ir.impl.usages.direct.DirectA>()

        assertEquals(
            listOf(
                "<init>" to listOf(
                    "reads" to listOf(),
                    "writes" to listOf()
                ),
                "newSmth" to listOf(
                    "reads" to listOf(
                        "java.lang.System#out",
                        "org.opentaint.opentaint-ir.impl.usages.direct.DirectA#result",
                        "org.opentaint.opentaint-ir.impl.usages.direct.DirectA#called",
                    ),
                    "writes" to listOf(
                        "org.opentaint.opentaint-ir.impl.usages.direct.DirectA#result",
                        "org.opentaint.opentaint-ir.impl.usages.direct.DirectA#called",
                    )
                ),
                "setCalled" to listOf(
                    "reads" to listOf(
                        "java.lang.System#out",
                        "org.opentaint.opentaint-ir.impl.usages.direct.DirectA#called",
                    ),
                    "writes" to listOf(
                        "org.opentaint.opentaint-ir.impl.usages.direct.DirectA#called",
                    )
                )
            ),
            usages
        )
    }

    private inline fun <reified T> JIRClasspath.fieldsUsages(): List<Pair<String, List<Pair<String, List<String>>>>> {
        return runBlocking {
            val classId = cp.findClass<T>()

            classId.declaredMethods.map {
                val usages = findFieldsUsedIn(it)
                it.name to listOf(
                    "reads" to usages.reads.map { it.enclosingClass.name + "#" + it.name },
                    "writes" to usages.writes.map { it.enclosingClass.name + "#" + it.name }
                )
            }
                .toMap()
                .filterNot { it.value.isEmpty() }
                .toSortedMap().toList()
        }
    }

    private inline fun <reified T> JIRClasspath.methodsUsages(): List<Pair<String, List<String>>> {
        return runBlocking {
            val jIRClass = cp.findClass<T>()

            val methods = jIRClass.declaredMethods

            methods.map {
                it.name to findMethodsUsedIn(it).map { it.enclosingClass.name + "#" + it.name }.toImmutableList()
            }.filterNot { it.second.isEmpty() }
        }
    }

    @AfterEach
    fun cleanup() {
        cp.close()
    }

}