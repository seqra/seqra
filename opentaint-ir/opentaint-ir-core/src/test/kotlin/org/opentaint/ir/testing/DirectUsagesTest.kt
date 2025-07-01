package org.opentaint.ir.testing

import kotlinx.coroutines.runBlocking
import org.opentaint.ir.api.jvm.JIRClasspath
import org.opentaint.ir.api.jvm.ext.CONSTRUCTOR
import org.opentaint.ir.api.jvm.ext.findClass
import org.opentaint.ir.api.jvm.ext.usedFields
import org.opentaint.ir.api.jvm.ext.usedMethods
import org.opentaint.ir.testing.usages.direct.DirectA
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class DirectUsagesTest : BaseTest() {

    companion object : WithGlobalDB()

    @Test
    fun `find methods used in method`() {
        val usages = cp.methodsUsages<DirectA>()

        assertEquals(
            listOf(
                CONSTRUCTOR to listOf("java.lang.Object#<init>"),
                "setCalled" to listOf(
                    "java.io.PrintStream#println",
                ),
                "newSmth" to listOf(
                    "com.google.common.collect.Lists#newArrayList",
                    "java.lang.Integer#valueOf",
                    "java.util.ArrayList#add",
                    "java.io.PrintStream#println",
                )
            ).sortedBy { it.first },
            usages
        )
    }

    @Test
    fun `find methods used in method with broken classpath`() {
        val cp = runBlocking {
            db.classpath(allClasspath - guavaLib)
        }
        cp.use {
            val usages = cp.methodsUsages<DirectA>()

            assertEquals(
                listOf(
                    CONSTRUCTOR to listOf("java.lang.Object#<init>"),
                    "setCalled" to listOf(
                        "java.io.PrintStream#println",
                    ),
                    "newSmth" to listOf(
                        "java.lang.Integer#valueOf",
                        "java.util.ArrayList#add",
                        "java.io.PrintStream#println",
                    )
                ).sortedBy { it.first },
                usages
            )
        }
    }

    @Test
    fun `find fields used in method`() {
        val usages = cp.fieldsUsages<DirectA>()

        assertEquals(
            listOf(
                CONSTRUCTOR to listOf(
                    "reads" to listOf(),
                    "writes" to listOf()
                ),
                "newSmth" to listOf(
                    "reads" to listOf(
                        "java.lang.System#out",
                        "org.opentaint.ir.testing.usages.direct.DirectA#called",
                        "org.opentaint.ir.testing.usages.direct.DirectA#result",
                    ),
                    "writes" to listOf(
                        "org.opentaint.ir.testing.usages.direct.DirectA#called",
                        "org.opentaint.ir.testing.usages.direct.DirectA#result",
                    )
                ),
                "setCalled" to listOf(
                    "reads" to listOf(
                        "java.lang.System#out",
                        "org.opentaint.ir.testing.usages.direct.DirectA#called",
                    ),
                    "writes" to listOf(
                        "org.opentaint.ir.testing.usages.direct.DirectA#called",
                    )
                )
            ).sortedBy { it.first },
            usages
        )
    }

    private inline fun <reified T> JIRClasspath.fieldsUsages(): List<Pair<String, List<Pair<String, List<String>>>>> {
        return runBlocking {
            val classId = findClass<T>()

            classId.declaredMethods.map {
                val usages = it.usedFields
                it.name to listOf(
                    "reads" to usages.reads.map { it.enclosingClass.name + "#" + it.name }.sortedBy { it },
                    "writes" to usages.writes.map { it.enclosingClass.name + "#" + it.name }.sortedBy { it }
                )
            }
                .toMap()
                .filterNot { it.value.isEmpty() }
                .toSortedMap().toList()
        }
    }

    private inline fun <reified T> JIRClasspath.methodsUsages(): List<Pair<String, List<String>>> {
        return runBlocking {
            val jIRClass = findClass<T>()

            val methods = jIRClass.declaredMethods

            methods.map {
                it.name to it.usedMethods.map { it.enclosingClass.name + "#" + it.name }
            }.filterNot { it.second.isEmpty() }.sortedBy { it.first }
        }
    }

    @AfterEach
    fun cleanup() {
        cp.close()
    }

}
