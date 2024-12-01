package org.opentaint.opentaint-ir.impl

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.opentaint.opentaint-ir.api.JIRClassOrInterface
import org.opentaint.opentaint-ir.api.JIRType
import org.opentaint.opentaint-ir.api.ext.autoboxIfNeeded
import org.opentaint.opentaint-ir.api.ext.findClass
import org.opentaint.opentaint-ir.api.ext.findTypeOrNull
import org.opentaint.opentaint-ir.api.ext.isSubClassOf
import org.opentaint.opentaint-ir.api.ext.unboxIfNeeded
import org.opentaint.opentaint-ir.api.short
import org.opentaint.opentaint-ir.impl.hierarchies.Creature.Animal
import org.opentaint.opentaint-ir.impl.hierarchies.Creature.Bird
import org.opentaint.opentaint-ir.impl.hierarchies.Creature.Dinosaur
import org.opentaint.opentaint-ir.impl.hierarchies.Creature.DinosaurImpl
import org.opentaint.opentaint-ir.impl.hierarchies.Creature.Fish
import org.opentaint.opentaint-ir.impl.hierarchies.Creature.Pterodactyl
import org.opentaint.opentaint-ir.impl.hierarchies.Creature.TRex

@Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN")
class ApiExtTest : BaseTest() {

    companion object : WithDB()

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
        assertTrue(classOf<Dinosaur>() isSubClassOf classOf<org.opentaint.opentaint-ir.impl.hierarchies.Creature>())

        assertFalse(classOf<Dinosaur>() isSubClassOf classOf<Fish>())
        assertTrue(classOf<TRex>() isSubClassOf classOf<org.opentaint.opentaint-ir.impl.hierarchies.Creature>())
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