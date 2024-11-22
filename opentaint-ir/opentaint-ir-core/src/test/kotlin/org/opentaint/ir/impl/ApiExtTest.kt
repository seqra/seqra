
package org.opentaint.ir.impl

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.opentaint.ir.api.JIRClassOrInterface
import org.opentaint.ir.api.JIRType
import org.opentaint.ir.api.autoboxIfNeeded
import org.opentaint.ir.api.ext.findClass
import org.opentaint.ir.api.ext.findTypeOrNull
import org.opentaint.ir.api.isSubtypeOf
import org.opentaint.ir.api.short
import org.opentaint.ir.api.unboxIfNeeded
import org.opentaint.ir.impl.hierarchies.Creature
import org.opentaint.ir.impl.hierarchies.Creature.Animal
import org.opentaint.ir.impl.hierarchies.Creature.Bird
import org.opentaint.ir.impl.hierarchies.Creature.Dinosaur
import org.opentaint.ir.impl.hierarchies.Creature.DinosaurImpl
import org.opentaint.ir.impl.hierarchies.Creature.Fish
import org.opentaint.ir.impl.hierarchies.Creature.Pterodactyl
import org.opentaint.ir.impl.hierarchies.Creature.TRex

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
        assertTrue(classOf<Dinosaur>() isSubtypeOf classOf<Creature>())

        assertFalse(classOf<Dinosaur>() isSubtypeOf classOf<Fish>())
        assertTrue(classOf<TRex>() isSubtypeOf classOf<Creature>())
        assertTrue(classOf<TRex>() isSubtypeOf classOf<Animal>())
        assertTrue(classOf<TRex>() isSubtypeOf classOf<DinosaurImpl>())

        assertFalse(classOf<TRex>() isSubtypeOf classOf<Fish>())
        assertFalse(classOf<Pterodactyl>() isSubtypeOf classOf<Fish>())
        assertTrue(classOf<Pterodactyl>() isSubtypeOf classOf<Bird>())
    }

    private inline fun <reified T> typeOf(): JIRType {
        return cp.findTypeOrNull<T>() ?: throw IllegalStateException("Type ${T::class.java.name} not found")
    }

    private inline fun <reified T> classOf(): JIRClassOrInterface {
        return cp.findClass<T>()
    }
}