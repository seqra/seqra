package org.opentaint.ir.testing

import kotlinx.coroutines.runBlocking
import org.opentaint.ir.api.JIRClasspath
import org.opentaint.ir.api.ext.HierarchyExtension
import org.opentaint.ir.impl.features.duplicatedClasses
import org.opentaint.ir.impl.features.hierarchyExt
import org.opentaint.ir.testing.tests.DatabaseEnvTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(CleanDB::class)
class ClassesTest : DatabaseEnvTest() {

    companion object : WithDB()

    override val cp: JIRClasspath = runBlocking { db.classpath(allClasspath) }

    override val hierarchyExt: HierarchyExtension
        get() = runBlocking { cp.hierarchyExt() }

    @Test
    fun `diagnostics should work`() {
        val duplicates = runBlocking { cp.duplicatedClasses() }
        println(duplicates.entries.joinToString("\n") { it.key + " found " + it.value + " times"})
        assertTrue(duplicates.isNotEmpty())
        assertTrue(duplicates.values.all { it > 1 })
        duplicates.entries.forEach { (name, count) ->
            val classes = cp.findClasses(name)
            assertEquals(count, classes.size, "Expected count for $name is $count but was ${classes.size}")
        }
    }
}

