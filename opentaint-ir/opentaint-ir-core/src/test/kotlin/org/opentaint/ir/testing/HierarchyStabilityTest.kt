package org.opentaint.ir.testing

import kotlinx.coroutines.runBlocking
import org.opentaint.ir.api.JIRClasspath
import org.opentaint.ir.api.JIRFeature
import org.opentaint.ir.api.ext.HierarchyExtension
import org.opentaint.ir.impl.features.InMemoryHierarchy
import org.opentaint.ir.impl.features.hierarchyExt
import org.opentaint.ir.impl.opentaint-ir
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.RepeatedTest
import org.junit.jupiter.api.Test

class HierarchyStabilityTest {

    companion object {
        private var listInheritorsCount: Int = 0
        private var setInheritorsCount: Int = 0

        @BeforeAll
        @JvmStatic
        fun setup() {
            val (sets, lists) = run()
            listInheritorsCount = lists
            setInheritorsCount = sets
        }

        private fun run(vararg features: JIRFeature<*,*>): Pair<Int, Int> {
            val jIRClasspath: JIRClasspath
            val hierarchy: HierarchyExtension

            runBlocking {
                val db = opentaint-ir {
                    useProcessJavaRuntime()
                    loadByteCode(allJars)
                    installFeatures(*features)
                }
                jIRClasspath = db.classpath(allJars)

                hierarchy = jIRClasspath.hierarchyExt()
            }

            val setSubclasses = hierarchy.findSubClasses("java.util.Set",
                allHierarchy = true, includeOwn = true).toSet()
            val listSubclasses = hierarchy.findSubClasses("java.util.List",
                allHierarchy = true, includeOwn = true).toSet()

            jIRClasspath.db.close()
            return setSubclasses.size to listSubclasses.size
        }

    }

    @RepeatedTest(3)
    fun `should be stable`() {
        val (sets, lists) = run()
        assertEquals(listInheritorsCount, lists)
        assertEquals(setInheritorsCount, sets)
    }

    @Test
    fun `should ok with in-memory feature`() {
        val (sets, lists) = run(InMemoryHierarchy)
        assertEquals(listInheritorsCount, lists)
        assertEquals(setInheritorsCount, sets)
    }

}