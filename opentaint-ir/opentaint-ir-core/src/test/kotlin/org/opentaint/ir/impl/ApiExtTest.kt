package org.opentaint.ir.impl

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.opentaint.ir.api.*
import org.opentaint.ir.api.ext.findClass
import org.opentaint.ir.impl.hierarchies.Creature
import org.opentaint.ir.impl.hierarchies.Creature.*
import org.opentaint.ir.impl.types.boolean
import org.opentaint.ir.impl.types.short
import org.opentaint.ir.jirdb

@Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN")
class ApiExtTest : LibrariesMixin {

    companion object : LibrariesMixin {
        var db: JIRDB? = runBlocking {
            jirdb {
                useProcessJavaRuntime()
                predefinedDirOrJars = allClasspath
            }.also {
                it.awaitBackgroundJobs()
            }
        }

        @AfterAll
        @JvmStatic
        fun cleanup() {
            db?.close()
            db = null
        }
    }

    var cp = runBlocking { db!!.classpathSet(allClasspath) }

    @Test
    fun `unboxing primitive type`() = runBlocking {
        val clazz = classOf<java.lang.Short>()
        assertEquals(cp.short, clazz.unboxIfNeeded())
    }

    @Test
    fun `unboxing regular type`() = runBlocking {
        val clazz = classOf<String>()
        assertEquals(clazz, clazz.unboxIfNeeded())
    }

    @Test
    fun `autoboxing primitive type`() = runBlocking {
        val clazz = cp.findClass("short")

        assertEquals(classOf<java.lang.Short>(), clazz.autoboxIfNeeded())
    }

    @Test
    fun `autoboxing regular type`() = runBlocking {
        val clazz = classOf<String>()
        assertEquals(clazz, clazz.autoboxIfNeeded())
    }

    @Test
    fun `class array`() = runBlocking {
        val clazz = arrayClassOf<String>()
        assertTrue(clazz is ArrayClassId)
        clazz as ArrayClassId
        assertEquals(cp.findClass<String>(), clazz.elementClass)
    }

    @Test
    fun `isSubtype for unboxing`() = runBlocking {
        assertTrue(cp.boolean isSubtypeOf cp.boolean.autoboxIfNeeded())
        assertTrue(cp.boolean.autoboxIfNeeded() isSubtypeOf cp.boolean)
    }

    @Test
    fun `isSubtype for arrays`() = runBlocking {
        assertTrue(arrayClassOf<String>() isSubtypeOf arrayClassOf<Any>())
        assertFalse(arrayClassOf<Any>() isSubtypeOf arrayClassOf<String>())
    }

    @Test
    fun `isSubtype for arrays with unboxing`() = runBlocking {
        assertFalse(cp.findClass("short[]") isSubtypeOf arrayClassOf<java.lang.Short>())
        assertFalse(arrayClassOf<java.lang.Short>() isSubtypeOf cp.findClass("short[]"))

        assertFalse(arrayClassOf<Any>() isSubtypeOf arrayClassOf<java.lang.Short>())

    }

    @Test
    fun `isSubtype for arrays with regular types`() = runBlocking {
        assertTrue(arrayClassOf<Dinosaur>() isSubtypeOf arrayClassOf<Creature>())
        assertFalse(arrayClassOf<Any>() isSubtypeOf arrayClassOf<java.lang.Short>())
    }

    @Test
    fun `isSubtype for regular types`() = runBlocking {
        assertTrue(classOf<Dinosaur>() isSubtypeOf classOf<Creature>())

        assertFalse(classOf<Dinosaur>() isSubtypeOf classOf<Fish>())
        assertTrue(classOf<TRex>() isSubtypeOf classOf<Creature>())
        assertTrue(classOf<TRex>() isSubtypeOf classOf<Animal>())
        assertTrue(classOf<TRex>() isSubtypeOf classOf<DinosaurImpl>())

        assertFalse(classOf<TRex>() isSubtypeOf classOf<Fish>())
        assertFalse(classOf<Pterodactyl>() isSubtypeOf classOf<Fish>())
        assertTrue(classOf<Pterodactyl>() isSubtypeOf classOf<Bird>())
    }

    private inline fun <reified T> classOf(): ClassId = runBlocking {
        cp.findClass<T>()
    }

    private inline fun <reified T> arrayClassOf(): ClassId {
        val name = T::class.java.name + "[]"
        return runBlocking {
            cp.findClass(name)
        }
    }

    @AfterEach
    fun close() {
        cp.close()
    }
}