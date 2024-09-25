package org.opentaint.ir.impl

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.opentaint.ir.api.ArrayClassId
import org.opentaint.ir.api.autoboxIfNeeded
import org.opentaint.ir.api.unboxIfNeeded
import org.opentaint.ir.compilationDatabase
import org.opentaint.ir.impl.index.findClassOrNull
import org.opentaint.ir.impl.types.short

class ApiExtTest {

    companion object {
        var db = runBlocking {
            compilationDatabase {
                useProcessJavaRuntime()
            }.also {
                it.awaitBackgroundJobs()
            }
        }

        @AfterAll
        fun cleanup() {
            db.close()
        }
    }

    var cp = runBlocking { db.classpathSet(emptyList()) }

    @Test
    fun `unboxing primitive type`() = runBlocking {
        val clazz = cp.findClassOrNull("java.lang.Short")
        assertNotNull(clazz!!)
        assertEquals(cp.short, clazz.unboxIfNeeded())
    }

    @Test
    fun `unboxing regular type`() = runBlocking {
        val clazz = cp.findClassOrNull("java.lang.String")
        assertNotNull(clazz!!)
        assertEquals(clazz, clazz.unboxIfNeeded())
    }

    @Test
    fun `autoboxing primitive type`() = runBlocking {
        val clazz = cp.findClassOrNull("short")
        assertNotNull(clazz!!)
        assertEquals(java.lang.Short::class.java.name, clazz.autoboxIfNeeded().name)
    }

    @Test
    fun `autoboxing regulat type`() = runBlocking {
        val clazz = cp.findClassOrNull("java.lang.String")
        assertNotNull(clazz!!)
        assertEquals(java.lang.String::class.java.name, clazz.autoboxIfNeeded().name)
    }

    @Test
    fun `class array`() = runBlocking {
        val clazz = cp.findClassOrNull("java.lang.String[]")
        assertNotNull(clazz!!)
        assertTrue(clazz is ArrayClassId)
        clazz as ArrayClassId
        assertEquals(cp.findClassOrNull<String>()!!, clazz.elementClass)
    }

    @AfterEach
    fun cleanup() {
        cp.close()
    }
}