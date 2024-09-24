package org.opentaint.ir.impl

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.opentaint.ir.api.*
import org.opentaint.ir.compilationDatabase
import org.opentaint.ir.impl.index.findClassOrNull
import org.opentaint.ir.impl.signature.*
import org.opentaint.ir.impl.types.ArrayClassIdImpl
import org.opentaint.ir.impl.types.PredefinedPrimitive
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
    fun `array types`() = runBlocking {
        val cp = db.classpathSet(allClasspath)
        val clazz = cp.findClassOrNull<Bar>()
        assertNotNull(clazz!!)
        assertEquals(Bar::class.java.name, clazz.name)

        val fields = clazz.fields()
        assertEquals(3, fields.size)

        with(fields.first()) {
            assertEquals("byteArray", name)
            assertEquals("byte[]", type().name)
            assertEquals(PredefinedPrimitive.byte, (type() as ArrayClassIdImpl).classId)
        }

        with(fields.get(1)) {
            assertEquals("objectArray", name)
            assertEquals("Object[]", type().name)
            assertEquals(cp.findClassOrNull<Any>(), (type() as ArrayClassIdImpl).classId)
        }

        with(fields.get(2)) {
            assertEquals("objectObjectArray", name)
            assertEquals("Object[][]", type().name)
            assertEquals(cp.findClassOrNull<Any>(), ((type() as ArrayClassIdImpl).classId as ArrayClassIdImpl).classId)
        }

        val methods = clazz.methods()
        assertEquals(2, methods.size)

        with(methods.first { it.name == "smth" }) {
            val parameters = parameters()
            assertEquals(1, parameters.size)
            assertEquals("byte[]", parameters.first().name)
            assertEquals("byte[]", returnType().name)
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
    fun `get signature of class`() = runBlocking {
        val cp = db.classpathSet(allClasspath)
        val a = cp.findClassOrNull<Generics<*>>()

        assertNotNull(a!!)
        val classSignature = a.signature()

        with(classSignature) {
            this as TypeResolutionImpl
            assertEquals("java.lang.Object", (superClass as RawType).name)
        }
    }

    @Test
    fun `get signature of methods`() = runBlocking {
        val cp = db.classpathSet(allClasspath)
        val a = cp.findClassOrNull<Generics<*>>()

        assertNotNull(a!!)
        val methodSignatures = a.methods().map { it.name to it.signature() }
        assertEquals(3, methodSignatures.size)
        with(methodSignatures[0]) {
            val (name, signature) = this
            assertEquals("<init>", name)
            assertEquals(Raw, signature)
        }
        with(methodSignatures[1]) {
            val (name, signature) = this
            assertEquals("merge", name)
            signature as MethodResolutionImpl
            assertEquals("void", (signature.returnType as PrimitiveType).ref.name)
            assertEquals(1, signature.parameterTypes.size)
            with(signature.parameterTypes.first()) {
                this as ParameterizedType
                assertEquals(Generics::class.java.name, this.name)
                assertEquals(1, parameterTypes.size)
                with(parameterTypes.first()) {
                    this as TypeVariable
                    assertEquals("T", this.symbol)
                }
            }
            assertEquals(1, signature.parameterTypes.size)
            val parameterizedType = signature.parameterTypes.first() as ParameterizedType
            assertEquals(1, parameterizedType.parameterTypes.size)
            assertEquals(Generics::class.java.name, parameterizedType.name)
            val typeVariable = parameterizedType.parameterTypes.first() as TypeVariable
            assertEquals("T", typeVariable.symbol)
        }
        with(methodSignatures[2]) {
            val (name, signature) = this
            assertEquals("merge1", name)
            signature as MethodResolutionImpl
            assertEquals("W", (signature.returnType as TypeVariable).symbol)

            assertEquals(1, signature.typeVariables.size)
            with(signature.typeVariables.first()) {
                this as Formal
                assertEquals("W", symbol)
                assertEquals(1, boundTypeTokens?.size)
                with(boundTypeTokens!!.first()) {
                    this as ParameterizedType
                    assertEquals("java.util.Collection", this.name)
                    assertEquals(1, parameterTypes.size)
                    with(parameterTypes.first()) {
                        this as TypeVariable
                        assertEquals("T", symbol)
                    }
                }
            }
            assertEquals(1, signature.parameterTypes.size)
            val parameterizedType = signature.parameterTypes.first() as ParameterizedType
            assertEquals(1, parameterizedType.parameterTypes.size)
            assertEquals(Generics::class.java.name, parameterizedType.name)
            val typeVariable = parameterizedType.parameterTypes.first() as TypeVariable
            assertEquals("T", typeVariable.symbol)
        }
    }

    @Test
    fun `get signature of fields`() = runBlocking {
        val cp = db.classpathSet(allClasspath)
        val a = cp.findClassOrNull<Generics<*>>()

        assertNotNull(a!!)
        val fieldSignatures = a.fields().map { it.name to it.signature() }

        assertEquals(2, fieldSignatures.size)

        with(fieldSignatures.first()) {
            val (name, signature) = this
            signature as FieldResolutionImpl
            val fieldType = signature.fieldType as TypeVariable
            assertEquals("niceField", name)
            assertEquals("T", fieldType.symbol)
        }
        with(fieldSignatures.get(1)) {
            val (name, signature) = this
            signature as FieldResolutionImpl
            val fieldType = signature.fieldType as ParameterizedType
            assertEquals("niceList", name)
            assertEquals("java.util.List", fieldType.name)
            with(fieldType.parameterTypes) {
                assertEquals(1, size)
                with(first()) {
                    this as BoundWildcard.UpperBoundWildcard
                    val bondType = boundType as TypeVariable
                    assertEquals("T", bondType.symbol)
                }
            }
            assertEquals("java.util.List", fieldType.name)
        }
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

