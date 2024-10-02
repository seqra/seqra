package org.opentaint.ir.impl

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.opentaint.ir.api.JIRDB
import org.opentaint.ir.api.Raw
import org.opentaint.ir.api.ext.findClass
import org.opentaint.ir.impl.signature.*
import org.opentaint.ir.impl.usages.Generics
import org.opentaint.ir.jirdb

class SignatureTest {
    companion object : LibrariesMixin {
        var db: JIRDB? = runBlocking {
            jirdb {
                predefinedDirOrJars = allClasspath
                useProcessJavaRuntime()
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

    private val cp = runBlocking { db!!.classpathSet(allClasspath) }

    @AfterEach
    fun close() {
        cp.close()
    }

    @Test
    fun `get signature of class`() = runBlocking {
        val a = cp.findClass<Generics<*>>()

        val classSignature = a.resolution()

        with(classSignature) {
            this as TypeResolutionImpl
            Assertions.assertEquals("java.lang.Object", (superClass as RawType).name)
        }
    }

    @Test
    fun `get signature of methods`() = runBlocking {
        val a = cp.findClass<Generics<*>>()

        val methodSignatures = a.methods().map { it.name to it.resolution() }
        Assertions.assertEquals(3, methodSignatures.size)
        with(methodSignatures[0]) {
            val (name, signature) = this
            Assertions.assertEquals("<init>", name)
            Assertions.assertEquals(Raw, signature)
        }
        with(methodSignatures[1]) {
            val (name, signature) = this
            Assertions.assertEquals("merge", name)
            signature as MethodResolutionImpl
            Assertions.assertEquals("void", (signature.returnType as PrimitiveType).ref.name)
            Assertions.assertEquals(1, signature.parameterTypes.size)
            with(signature.parameterTypes.first()) {
                this as ParameterizedType
                Assertions.assertEquals(Generics::class.java.name, this.name)
                Assertions.assertEquals(1, parameterTypes.size)
                with(parameterTypes.first()) {
                    this as TypeVariable
                    Assertions.assertEquals("T", this.symbol)
                }
            }
            Assertions.assertEquals(1, signature.parameterTypes.size)
            val parameterizedType = signature.parameterTypes.first() as ParameterizedType
            Assertions.assertEquals(1, parameterizedType.parameterTypes.size)
            Assertions.assertEquals(Generics::class.java.name, parameterizedType.name)
            val typeVariable = parameterizedType.parameterTypes.first() as TypeVariable
            Assertions.assertEquals("T", typeVariable.symbol)
        }
        with(methodSignatures[2]) {
            val (name, signature) = this
            Assertions.assertEquals("merge1", name)
            signature as MethodResolutionImpl
            Assertions.assertEquals("W", (signature.returnType as TypeVariable).symbol)

            Assertions.assertEquals(1, signature.typeVariables.size)
            with(signature.typeVariables.first()) {
                this as Formal
                Assertions.assertEquals("W", symbol)
                Assertions.assertEquals(1, boundTypeTokens?.size)
                with(boundTypeTokens!!.first()) {
                    this as ParameterizedType
                    Assertions.assertEquals("java.util.Collection", this.name)
                    Assertions.assertEquals(1, parameterTypes.size)
                    with(parameterTypes.first()) {
                        this as TypeVariable
                        Assertions.assertEquals("T", symbol)
                    }
                }
            }
            Assertions.assertEquals(1, signature.parameterTypes.size)
            val parameterizedType = signature.parameterTypes.first() as ParameterizedType
            Assertions.assertEquals(1, parameterizedType.parameterTypes.size)
            Assertions.assertEquals(Generics::class.java.name, parameterizedType.name)
            val typeVariable = parameterizedType.parameterTypes.first() as TypeVariable
            Assertions.assertEquals("T", typeVariable.symbol)
        }
    }

    @Test
    fun `get signature of fields`() = runBlocking {
        val a = cp.findClass<Generics<*>>()

        val fieldSignatures = a.fields().map { it.name to it.resolution() }

        Assertions.assertEquals(2, fieldSignatures.size)

        with(fieldSignatures.first()) {
            val (name, signature) = this
            signature as FieldResolutionImpl
            val fieldType = signature.fieldType as TypeVariable
            Assertions.assertEquals("niceField", name)
            Assertions.assertEquals("T", fieldType.symbol)
        }
        with(fieldSignatures.get(1)) {
            val (name, signature) = this
            signature as FieldResolutionImpl
            val fieldType = signature.fieldType as ParameterizedType
            Assertions.assertEquals("niceList", name)
            Assertions.assertEquals("java.util.List", fieldType.name)
            with(fieldType.parameterTypes) {
                Assertions.assertEquals(1, size)
                with(first()) {
                    this as BoundWildcard.UpperBoundWildcard
                    val bondType = boundType as TypeVariable
                    Assertions.assertEquals("T", bondType.symbol)
                }
            }
            Assertions.assertEquals("java.util.List", fieldType.name)
        }
    }

}

