package org.opentaint.ir.testing

import kotlinx.coroutines.runBlocking
import org.opentaint.ir.impl.features.hierarchyExt
import org.opentaint.ir.impl.opentaint-ir
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test

class HierarchyStabilityTest {

    companion object {
        private var listInheritorsCount: Int = 0
        private var setInheritorsCount: Int = 0

        @BeforeAll
        @JvmStatic
        fun setup() {
            val (sets, lists) = runBlocking { run(global = true) }
            listInheritorsCount = lists
            setInheritorsCount = sets
        }

        private suspend fun run(global: Boolean): Pair<Int, Int> {

            val db = when {
                global -> globalDb
                else -> opentaint-ir {
                    useProcessJavaRuntime()
                    loadByteCode(allJars)
                    installFeatures()
                }
            }
            val jIRClasspath = db.classpath(allJars)
            val hierarchy = jIRClasspath.hierarchyExt()

            val setSubclasses = hierarchy.findSubClasses(
                "java.util.Set",
                entireHierarchy = true, includeOwn = true
            ).toSet()
            val listSubclasses = hierarchy.findSubClasses(
                "java.util.List",
                entireHierarchy = true, includeOwn = true
            ).toSet()

            if (!global) {
                jIRClasspath.db.close()
            }
            return setSubclasses.size to listSubclasses.size
        }

    }

    @Test
    fun `should be ok`() {
        val (sets, lists) = runBlocking { run(global = false) }
        assertEquals(listInheritorsCount, lists)
        assertEquals(setInheritorsCount, sets)
    }

    @Test
    fun `should ok with in-memory feature`() {
        val (sets, lists) = runBlocking { run(global = true) }
        assertEquals(listInheritorsCount, lists)
        assertEquals(setInheritorsCount, sets)
    }

}