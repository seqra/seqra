package org.opentaint.ir.testing

import kotlinx.coroutines.runBlocking
import org.opentaint.ir.api.jvm.JIRClassOrInterface
import org.opentaint.ir.api.jvm.JIRType
import org.opentaint.ir.api.jvm.ext.*
import org.opentaint.ir.testing.hierarchies.Creature
import org.opentaint.ir.testing.hierarchies.Creature.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

@Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN")
class ApiExtTest : BaseTest() {

    companion object : WithGlobalDB()

    @Test
    fun `unboxing primitive type`() {
        val clazz = typeOf<java.lang.Short>()
        assertEquals(cp.short, clazz.unboxIfNeeded())
    }

    @Test
    fun `unboxing regular type`() {
        val clazz = typeOf<String>()
        assertEquals(clazz, clazz.unboxIfNeeded())
    }

    @Test
    fun `autoboxing primitive type`() {
        val type = cp.findTypeOrNull("short")

        assertEquals(typeOf<java.lang.Short>(), type?.autoboxIfNeeded())
    }

    @Test
    fun `autoboxing regular type`() {
        val clazz = typeOf<String>()
        assertEquals(clazz, clazz.autoboxIfNeeded())
    }

    @Test
    fun `isSubtype for regular classes`() = runBlocking {
        assertTrue(classOf<Dinosaur>() isSubClassOf classOf<Creature>())

        assertFalse(classOf<Dinosaur>() isSubClassOf classOf<Fish>())
        assertTrue(classOf<TRex>() isSubClassOf classOf<Creature>())
        assertTrue(classOf<TRex>() isSubClassOf classOf<Animal>())
        assertTrue(classOf<TRex>() isSubClassOf classOf<DinosaurImpl>())

        assertFalse(classOf<TRex>() isSubClassOf classOf<Fish>())
        assertFalse(classOf<Pterodactyl>() isSubClassOf classOf<Fish>())
        assertTrue(classOf<Pterodactyl>() isSubClassOf classOf<Bird>())
    }

    private inline fun <reified T> typeOf(): JIRType {
        return cp.findTypeOrNull<T>() ?: throw IllegalStateException("Type ${T::class.java.name} not found")
    }

    private inline fun <reified T> classOf(): JIRClassOrInterface {
        return cp.findClass<T>()
    }
}
