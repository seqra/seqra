package org.opentaint.ir.impl

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.opentaint.ir.api.*
import org.opentaint.ir.compilationDatabase
import org.opentaint.ir.impl.index.findClassOrNull
import org.opentaint.ir.impl.usages.Generics
import org.opentaint.ir.impl.usages.HelloWorldAnonymousClasses
import org.opentaint.ir.impl.usages.WithInner
import org.w3c.dom.Document
import org.w3c.dom.DocumentType
import java.util.*
import java.util.concurrent.ConcurrentHashMap

class DatabaseTest : LibrariesMixin {

    private val db = runBlocking {
        compilationDatabase {
            predefinedDirOrJars = allClasspath
            useProcessJavaRuntime()
        }
    }

    @AfterEach
    fun close() {
        runBlocking {
            db.awaitBackgroundJobs()
            db.close()
        }
    }

    @Test
    fun `find class from build dir folder`() = runBlocking {
        val cp = db.classpathSet(allClasspath)
        val clazz = cp.findClassOrNull<Foo>()
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
        with(fields[1]) {
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
    fun `inner and static`() = runBlocking {
        val cp = db.classpathSet(allClasspath)
        val withInner = cp.findClassOrNull<WithInner>()
        val inner = cp.findClassOrNull<WithInner.Inner>()
        val staticInner = cp.findClassOrNull<WithInner.StaticInner>()

        val anon = cp.findClassOrNull("org.opentaint.ir.impl.usages.WithInner$1")
        assertNotNull(withInner!!)
        assertNotNull(inner!!)
        assertNotNull(staticInner!!)
        assertNotNull(anon!!)

        assertEquals(withInner, anon.outerClass())
        assertEquals(withInner, inner.outerClass())
        assertEquals(withInner, staticInner.outerClass())
        assertEquals(withInner.findMethodOrNull("sayHello", "()V"), anon.outerMethod())
        assertNull(staticInner.outerMethod())
    }

    @Test
    fun `get signature of class and methods`() = runBlocking {
        val cp = db.classpathSet(allClasspath)
        val a = cp.findClassOrNull<Generics<*>>()

        assertNotNull(a!!)
        val classSignature = a.signature()
        val methodSignatures = a.methods().map { it.signature() }
        val fieldSignatures = a.fields().map { it.signature() }
        methodSignatures.forEach {
            println(it)
        }
        fieldSignatures.forEach {
            println(it)
        }
        println(classSignature)
    }

    @Test
    fun `local and anonymous classes`() = runBlocking {
        val cp = db.classpathSet(allClasspath)
        val withAnonymous = cp.findClassOrNull<HelloWorldAnonymousClasses>()
        assertNotNull(withAnonymous!!)

        val helloWorld = cp.findClassOrNull<HelloWorldAnonymousClasses.HelloWorld>()
        assertNotNull(helloWorld!!)
        assertTrue(helloWorld.isMemberClass())

        val innerClasses = withAnonymous.innerClasses()
        assertEquals(4, innerClasses.size)
        val notHelloWorld = innerClasses.filterNot { it.name.contains("\$HelloWorld") }
        val englishGreetings = notHelloWorld.first { it.name.contains("EnglishGreeting") }
        assertTrue(englishGreetings.isLocal())
        assertFalse(englishGreetings.isAnonymous())

        (notHelloWorld - englishGreetings).forEach {
            assertFalse(it.isLocal())
            assertTrue(it.isAnonymous())
            assertFalse(it.isMemberClass())
        }
    }

    @Test
    fun `find lazy-loaded class`() = runBlocking {
        val cp = db.classpathSet(emptyList())
        val domClass = cp.findClassOrNull<Document>()
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
        val cp = db.classpathSet(emptyList())
        val domClass = cp.findClassOrNull<Document>()
        assertNotNull(domClass!!)

        with(cp.findSubClasses(java.util.AbstractMap::class.java.name)) {
            assertTrue(size > 10) {
                "expected more then 10 but got only: ${joinToString { it.name }}"
            }

            assertNotNull(firstOrNull { it.name == EnumMap::class.java.name })
            assertNotNull(firstOrNull { it.name == HashMap::class.java.name })
            assertNotNull(firstOrNull { it.name == WeakHashMap::class.java.name })
            assertNotNull(firstOrNull { it.name == TreeMap::class.java.name })
            assertNotNull(firstOrNull { it.name == ConcurrentHashMap::class.java.name })
        }

        with(cp.findSubClasses(Document::class.java.name)) {
            assertTrue(isNotEmpty())
        }
    }

    @Test
    fun `find sub-types with all hierarchy`() = runBlocking {
        val cp = db.classpathSet(allClasspath)
        val clazz = cp.findClassOrNull<SuperDuper>()
        assertNotNull(clazz!!)

        with(cp.findSubClasses(clazz, allHierarchy = true)) {
            assertEquals(4, size) {
                "expected more then 10 but got only: ${joinToString { it.name }}"
            }

            assertNotNull(firstOrNull { it.name == A::class.java.name })
            assertNotNull(firstOrNull { it.name == B::class.java.name })
            assertNotNull(firstOrNull { it.name == C::class.java.name })
            assertNotNull(firstOrNull { it.name == D::class.java.name })
        }
    }
}

