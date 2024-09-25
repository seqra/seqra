package org.opentaint.ir.impl

import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.opentaint.ir.api.ClasspathSet
import org.opentaint.ir.api.findClass
import org.opentaint.ir.compilationDatabase
import org.opentaint.ir.impl.index.ReversedUsages
import org.opentaint.ir.impl.index.findFieldsUsedIn
import org.opentaint.ir.impl.index.findMethodsUsedIn
import org.opentaint.ir.impl.usages.direct.DirectA

class DirectUsagesTest : LibrariesMixin {

    private val db = runBlocking {
        compilationDatabase {
            predefinedDirOrJars = allClasspath
            useProcessJavaRuntime()
            installIndexes(ReversedUsages)
        }
    }

    private val cp = runBlocking { db.classpathSet(allClasspath) }

    @Test
    fun `find methods used in method`() {
        val usages = cp.methodsUsages<DirectA>()

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
            db.classpathSet(allClasspath - guavaLib)
        }
        cp.use {
            val usages = cp.methodsUsages<DirectA>()

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
        val usages = cp.fieldsUsages<DirectA>()

        assertEquals(
            listOf(
                "<init>" to listOf(
                    "reads" to listOf(),
                    "writes" to listOf()
                ),
                "newSmth" to listOf(
                    "reads" to listOf(
                        "java.lang.System#out",
                        "org.opentaint.ir.impl.usages.direct.DirectA#result",
                        "org.opentaint.ir.impl.usages.direct.DirectA#called",
                    ),
                    "writes" to listOf(
                        "org.opentaint.ir.impl.usages.direct.DirectA#result",
                        "org.opentaint.ir.impl.usages.direct.DirectA#called",
                    )
                ),
                "setCalled" to listOf(
                    "reads" to listOf(
                        "java.lang.System#out",
                        "org.opentaint.ir.impl.usages.direct.DirectA#called",
                    ),
                    "writes" to listOf(
                        "org.opentaint.ir.impl.usages.direct.DirectA#called",
                    )
                )
            ),
            usages
        )
    }

    private inline fun <reified T> ClasspathSet.fieldsUsages(): List<Pair<String, List<Pair<String, List<String>>>>> {
        return runBlocking {
            val classId = cp.findClass<T>()

            classId.methods().map {
                val usages = findFieldsUsedIn(it)
                it.name to listOf(
                    "reads" to usages.reads.map { it.classId.name + "#" + it.name },
                    "writes" to usages.writes.map { it.classId.name + "#" + it.name }
                )
            }
                .toMap()
                .filterNot { it.value.isEmpty() }
                .toSortedMap().toList()
        }
    }

    private inline fun <reified T> ClasspathSet.methodsUsages(): List<Pair<String, List<String>>> {
        return runBlocking {
            val classId = cp.findClass<T>()

            val methods = classId.methods()

            methods.map {
                it.name to findMethodsUsedIn(it).map { it.classId.name + "#" + it.name }.toImmutableList()
            }.filterNot { it.second.isEmpty() }
        }
    }

    @AfterEach
    fun cleanup() {
        cp.close()
        db.close()
    }

}