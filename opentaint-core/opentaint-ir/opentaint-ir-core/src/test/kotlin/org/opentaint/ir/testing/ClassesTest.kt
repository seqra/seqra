package org.opentaint.ir.testing

import kotlinx.coroutines.runBlocking
import org.opentaint.ir.api.jvm.JIRClassType
import org.opentaint.ir.api.jvm.JIRClasspath
import org.opentaint.ir.api.jvm.ext.HierarchyExtension
import org.opentaint.ir.api.jvm.ext.enumValues
import org.opentaint.ir.api.jvm.ext.findClass
import org.opentaint.ir.api.jvm.ext.findTypeOrNull
import org.opentaint.ir.impl.features.duplicatedClasses
import org.opentaint.ir.impl.features.hierarchyExt
import org.opentaint.ir.testing.structure.EnumExamples.EnumWithField
import org.opentaint.ir.testing.structure.EnumExamples.EnumWithStaticInstance
import org.opentaint.ir.testing.structure.EnumExamples.SimpleEnum
import org.opentaint.ir.testing.tests.DatabaseEnvTest
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

open class ClassesTest : DatabaseEnvTest() {

    companion object : WithGlobalDbImmutable() {
        @AfterAll
        @JvmStatic
        fun cleanUpDb() {
            cleanup()
        }
    }

    override val cp: JIRClasspath = runBlocking { db.classpath(allClasspath) }

    override val hierarchyExt: HierarchyExtension
        get() = runBlocking { cp.hierarchyExt() }

    @Test
    fun `diagnostics should work`() {
        val duplicates = runBlocking { cp.duplicatedClasses() }
        println(duplicates.entries.joinToString("\n") { it.key + " found " + it.value + " times" })
        assertTrue(duplicates.isNotEmpty())
        assertTrue(duplicates.values.all { it > 1 })
        duplicates.entries.forEach { (name, count) ->
            val classes = cp.findClasses(name)
            assertEquals(count, classes.size, "Expected count for $name is $count but was ${classes.size}")
        }
    }

    @Test
    fun `enum constructor methods`() {
        val enumType = cp.findTypeOrNull<SimpleEnum>() as JIRClassType
        val parameters = enumType.declaredMethods.first { it.method.isConstructor }.parameters
        assertEquals("java.lang.String", parameters.first().type.typeName)
        assertEquals("int", parameters[1].type.typeName)
    }

    @Test
    fun `enum constructor methods with fields`() {
        val enumType = cp.findTypeOrNull<EnumWithField>() as JIRClassType
        val parameters = enumType.declaredMethods.first { it.method.isConstructor }.parameters
        assertEquals("java.lang.String", parameters.first().type.typeName)
        assertEquals("int", parameters[1].type.typeName)
        assertEquals("int", parameters[2].type.typeName)
    }

    @Test
    fun `enum values filter out static instances`() {
        val enumType = cp.findClass<EnumWithStaticInstance>()
        assertEquals(2, enumType.enumValues!!.size)
        assertEquals(listOf("C1", "C2"), enumType.enumValues!!.map { it.name })
    }
}

class ClassesWithoutJRETest : ClassesTest() {

    companion object : WithGlobalDbWithoutJRE() {
        @AfterAll
        @JvmStatic
        fun cleanUpDb() {
            ClassesTest.cleanup()
        }
    }
}