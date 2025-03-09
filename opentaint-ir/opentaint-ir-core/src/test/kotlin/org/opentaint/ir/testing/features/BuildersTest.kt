package org.opentaint.ir.testing.features

import kotlinx.coroutines.runBlocking
import org.opentaint.ir.api.JIRMethod
import org.opentaint.ir.api.ext.findClass
import org.opentaint.ir.impl.features.buildersExtension
import org.opentaint.ir.testing.BaseTest
import org.opentaint.ir.testing.WithGlobalDB
import org.opentaint.ir.testing.builders.Hierarchy.HierarchyInterface
import org.opentaint.ir.testing.builders.Interfaces.Interface
import org.opentaint.ir.testing.builders.Simple
import org.jooq.DSLContext
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.DisabledOnJre
import org.junit.jupiter.api.condition.JRE
import javax.xml.parsers.DocumentBuilderFactory

class BuildersTest : BaseTest() {

    companion object : WithGlobalDB()

    private val ext = runBlocking {
        cp.buildersExtension()
    }

    @Test
    fun `simple find builders`() {
        val builders = ext.findBuildMethods(cp.findClass<Simple>()).toList()
        assertEquals(1, builders.size)
        assertEquals("build", builders.first().name)
    }

    @Test
    fun `java package is not indexed`() {
        val builders = ext.findBuildMethods(cp.findClass<ArrayList<*>>())
        assertFalse(builders.iterator().hasNext())
    }

    @Test
    fun `method parameters is took into account`() {
        val builders = ext.findBuildMethods(cp.findClass<Interface>()).toList()
        assertEquals(1, builders.size)
        assertEquals("build1", builders.first().name)
    }

    @Test
    @DisabledOnJre(JRE.JAVA_8)
    fun `works for DocumentBuilderFactory`() {
        val builders = ext.findBuildMethods(cp.findClass<DocumentBuilderFactory>()).toList()
        val expected = builders.map { it.loggable }
        assertTrue(expected.contains("javax.xml.parsers.DocumentBuilderFactory#newDefaultInstance"))
        assertTrue(expected.contains("javax.xml.parsers.DocumentBuilderFactory#newInstance"))
    }

    @Test
    fun `works for DocumentBuilderFactory for java 8`() {
        val builders = ext.findBuildMethods(cp.findClass<DocumentBuilderFactory>()).toList()
        val expected = builders.map { it.loggable }
        assertTrue(expected.contains("javax.xml.parsers.DocumentBuilderFactory#newInstance"))
    }

    @Test
    fun `works for jooq`() {
        val builders = ext.findBuildMethods(cp.findClass<DSLContext>()).toList()
        assertEquals("org.jooq.impl.DSL#using", builders.first().loggable)
    }

    @Test
    fun `works for methods returns subclasses`() {
        val builders = ext.findBuildMethods(cp.findClass<HierarchyInterface>(), includeSubclasses = true).toList()
        assertEquals(1, builders.size)
        assertEquals("org.opentaint.ir.testing.builders.Hierarchy#build", builders.first().loggable)
    }

    private val JIRMethod.loggable get() = enclosingClass.name + "#" + name
}