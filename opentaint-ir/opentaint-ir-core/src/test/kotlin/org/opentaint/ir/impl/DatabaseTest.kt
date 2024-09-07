package org.opentaint.ir.impl

import org.opentaint.ir.api.isFinal
import org.opentaint.ir.api.isInterface
import org.opentaint.ir.api.isPrivate
import org.opentaint.ir.api.isPublic
import org.opentaint.ir.compilationDatabase
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.w3c.dom.DocumentType
import java.io.File
import java.net.URLClassLoader

class DatabaseTest {

    private val allClasspath: List<File>
        get() {
            val cl = ClassLoader.getSystemClassLoader()
            return (cl as URLClassLoader).urLs.map { File(it.file) }
        }

    @Test
    fun `find class from build dir folder`() = runBlocking {
        val db = compilationDatabase {
            predefinedJars = allClasspath
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

}

@Nested
class Foo {

    var foo: Int = 0
    private var bar: String = ""

    fun smthPublic(foo: Int) = foo

    private fun smthPrivate(): Int = foo
}
