package org.opentaint.ir.testing.features

import kotlinx.coroutines.runBlocking
import org.opentaint.ir.testing.BaseTest
import org.opentaint.ir.testing.WithDB
import org.opentaint.ir.testing.WithRestoredDB
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.opentaint.opentaint-ir.api.JIRClassOrInterface
import org.opentaint.opentaint-ir.api.JIRMethod
import org.opentaint.opentaint-ir.api.ext.findClass
import org.opentaint.opentaint-ir.impl.features.InMemoryHierarchy
import org.opentaint.opentaint-ir.impl.features.findSubclassesInMemory
import org.opentaint.opentaint-ir.impl.features.hierarchyExt
import org.w3c.dom.Document
import java.util.*
import java.util.concurrent.ConcurrentHashMap

abstract class BaseInMemoryHierarchyTest : BaseTest() {

    protected val ext = runBlocking { cp.hierarchyExt() }
    open val isInMemory = true

    @Test
    fun `find subclasses for class`() {
        with(findSubClasses<AbstractMap<*, *>>(allHierarchy = true).toList()) {
            assertTrue(size > 10) {
                "expected more then 10 but got only: ${joinToString { it.name }}"
            }

            assertNotNull(firstOrNull { it.name == EnumMap::class.java.name })
            assertNotNull(firstOrNull { it.name == HashMap::class.java.name })
            assertNotNull(firstOrNull { it.name == WeakHashMap::class.java.name })
            assertNotNull(firstOrNull { it.name == TreeMap::class.java.name })
            assertNotNull(firstOrNull { it.name == ConcurrentHashMap::class.java.name })
        }
    }

    @Test
    fun `find subclasses for interface`() {
        with(findSubClasses<Document>().toList()) {
            assertTrue(isNotEmpty(), "expect not empty result")
        }
    }

    @Test
    fun `find huge number of subclasses`() {
        with(findSubClasses<Runnable>().toList()) {
            assertTrue(size > 10, "expect more then 10 but got $size")
        }
    }

    @Test
    fun `find huge number of method overrides`() {
        val jIRClazz = cp.findClass<Runnable>()
        with(findMethodOverrides(jIRClazz.declaredMethods.first()).toList()) {
            println("Found: $size")
            assertTrue(size > 10)
        }
    }

    @Test
    fun `find regular method overrides`() {
        val jIRClazz = cp.findClass<Document>()
        with(findMethodOverrides(jIRClazz.declaredMethods.first()).toList()) {
            assertTrue(size >= 4)
        }
    }

    private inline fun <reified T> findSubClasses(allHierarchy: Boolean = false): Sequence<JIRClassOrInterface> =
        runBlocking {
            when {
                isInMemory -> cp.findSubclassesInMemory(T::class.java.name, allHierarchy, true)
                else -> ext.findSubClasses(T::class.java.name, allHierarchy)
            }
        }

    private fun findMethodOverrides(method: JIRMethod) = ext.findOverrides(method)

}

class InMemoryHierarchyTest : BaseInMemoryHierarchyTest() {
    companion object : WithDB(InMemoryHierarchy)
}

class RegularHierarchyTest : BaseInMemoryHierarchyTest() {
    companion object : WithDB()

    override val isInMemory: Boolean
        get() = false
}

class RestoredInMemoryHierarchyTest : BaseInMemoryHierarchyTest() {
    companion object : WithRestoredDB(InMemoryHierarchy)
}