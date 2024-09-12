package org.opentaint.java.compilation.database.impl

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.opentaint.java.compilation.database.api.isFinal
import org.opentaint.java.compilation.database.api.isInterface
import org.opentaint.java.compilation.database.api.isPrivate
import org.opentaint.java.compilation.database.api.isPublic
import org.opentaint.java.compilation.database.compilationDatabase
import org.w3c.dom.DocumentType

class DatabaseTest: LibrariesMixin {

    @Test
    fun `find class from build dir folder`() = runBlocking {
        val db = compilationDatabase {
            predefinedDirOrJars = allClasspath
            useJavaHomeJRE()
        }


        val cp = db.classpathSet(allClasspath)
        val clazz = cp.findClassOrNull(Foo::class.java.name)
        assertNotNull(clazz!!)
        assertEquals(Foo::class.java.name, clazz.name)
        assertTrue(clazz.isFinal())
        assertTrue(clazz.isPublic())
        assertFalse(clazz.isInterface())

        val annotations = clazz.annotations()
        assertTrue(annotations.size > 1)
        assertNotNull(annotations.firstOrNull { it.name == Nested::class.java.name })

        val fields = clazz.fields()
        assertEquals(2, fields.size)

        with(fields.first()) {
            assertEquals("foo", name)
            assertEquals("int", type()?.name)
        }
        with(fields.get(1)) {
            assertEquals("bar", name)
            assertEquals(String::class.java.name, type()?.name)
        }

        val methods = clazz.methods()
        assertEquals(5, methods.size)
        with(methods.first { it.name == "smthPublic" }) {
            assertEquals(1, parameters().size)
            assertEquals("int", parameters().first().name)
            assertTrue(isPublic())
        }

        with(methods.first { it.name == "smthPrivate" }) {
            assertTrue(parameters().isEmpty())
            assertTrue(isPrivate())
        }
    }

    @Test
    fun `find lazy-loaded class`() = runBlocking {
        val db = compilationDatabase {
            useJavaHomeJRE()
        }


        val cp = db.classpathSet(emptyList())
        val domClass = cp.findClassOrNull(org.w3c.dom.Document::class.java.name)
        assertNotNull(domClass!!)

        assertTrue(domClass.isPublic())
        assertTrue(domClass.isInterface())

        val methods = domClass.methods()
        assertTrue(methods.isNotEmpty())
        with(methods.first { it.name == "getDoctype" }) {
            assertTrue(parameters().isEmpty())
            assertEquals(DocumentType::class.java.name, returnType()?.name)
            assertTrue(isPublic())
        }
    }

    @Test
    fun `find sub-types from lazy loaded classes`() = runBlocking {
        val db = compilationDatabase {
            useProcessJRE()
        }

        val cp = db.classpathSet(emptyList())
        val domClass = cp.findClassOrNull(org.w3c.dom.Document::class.java.name)
        assertNotNull(domClass!!)

        with(cp.findSubTypesOf(java.util.AbstractMap::class.java.name)) {
            assertTrue(size > 10)
        }

        with(cp.findSubTypesOf(org.w3c.dom.Document::class.java.name)) {
            assertEquals(3, size)
        }
    }
}

