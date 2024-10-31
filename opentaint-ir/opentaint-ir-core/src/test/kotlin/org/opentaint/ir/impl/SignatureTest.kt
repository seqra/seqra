package org.opentaint.ir.impl

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.opentaint.ir.api.JIRDB
import org.opentaint.ir.api.JIRClassOrInterface
import org.opentaint.ir.api.JIRField
import org.opentaint.ir.api.JIRMethod
import org.opentaint.ir.api.Pure
import org.opentaint.ir.api.ext.findClass
import org.opentaint.ir.impl.signature.FieldResolutionImpl
import org.opentaint.ir.impl.signature.FieldSignature
import org.opentaint.ir.impl.signature.Formal
import org.opentaint.ir.impl.signature.MethodResolutionImpl
import org.opentaint.ir.impl.signature.MethodSignature
import org.opentaint.ir.impl.signature.SBoundWildcard
import org.opentaint.ir.impl.signature.SClassRefType
import org.opentaint.ir.impl.signature.SParameterizedType
import org.opentaint.ir.impl.signature.SPrimitiveType
import org.opentaint.ir.impl.signature.STypeVariable
import org.opentaint.ir.impl.signature.TypeResolutionImpl
import org.opentaint.ir.impl.signature.TypeSignature
import org.opentaint.ir.impl.usages.Generics
import org.opentaint.ir.jirdb

class SignatureTest {
    companion object : LibrariesMixin {
        var db: JIRDB? = runBlocking {
            jirdb {
                loadByteCode(allClasspath)
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

    private val cp = runBlocking { db!!.classpath(allClasspath) }

    @AfterEach
    fun close() {
        cp.close()
    }

    @Test
    fun `get signature of class`() = runBlocking {
        val a = cp.findClass<Generics<*>>()

        val classSignature = a.resolution

        with(classSignature) {
            this as TypeResolutionImpl
            assertEquals("java.lang.Object", (superClass as SClassRefType).name)
        }
    }

    @Test
    fun `get signature of methods`() = runBlocking {
        val a = cp.findClass<Generics<*>>()

        val methodSignatures = a.methods.map { it.name to it.resolution }
        assertEquals(3, methodSignatures.size)
        with(methodSignatures[0]) {
            val (name, signature) = this
            assertEquals("<init>", name)
            assertEquals(Pure, signature)
        }
        with(methodSignatures[1]) {
            val (name, signature) = this
            assertEquals("merge", name)
            signature as MethodResolutionImpl
            assertEquals("void", (signature.returnType as SPrimitiveType).ref)
            assertEquals(1, signature.parameterTypes.size)
            with(signature.parameterTypes.first()) {
                this as SParameterizedType
                assertEquals(Generics::class.java.name, this.name)
                assertEquals(1, parameterTypes.size)
                with(parameterTypes.first()) {
                    this as STypeVariable
                    assertEquals("T", this.symbol)
                }
            }
            assertEquals(1, signature.parameterTypes.size)
            val parameterizedType = signature.parameterTypes.first() as SParameterizedType
            assertEquals(1, parameterizedType.parameterTypes.size)
            assertEquals(Generics::class.java.name, parameterizedType.name)
            val typeVariable = parameterizedType.parameterTypes.first() as STypeVariable
            assertEquals("T", typeVariable.symbol)
        }
        with(methodSignatures[2]) {
            val (name, signature) = this
            assertEquals("merge1", name)
            signature as MethodResolutionImpl
            assertEquals("W", (signature.returnType as STypeVariable).symbol)

            assertEquals(1, signature.typeVariables.size)
            with(signature.typeVariables.first()) {
                this as Formal
                assertEquals("W", symbol)
                assertEquals(1, boundTypeTokens?.size)
                with(boundTypeTokens!!.first()) {
                    this as SParameterizedType
                    assertEquals("java.util.Collection", this.name)
                    assertEquals(1, parameterTypes.size)
                    with(parameterTypes.first()) {
                        this as STypeVariable
                        assertEquals("T", symbol)
                    }
                }
            }
            assertEquals(1, signature.parameterTypes.size)
            val parameterizedType = signature.parameterTypes.first() as SParameterizedType
            assertEquals(1, parameterizedType.parameterTypes.size)
            assertEquals(Generics::class.java.name, parameterizedType.name)
            val STypeVariable = parameterizedType.parameterTypes.first() as STypeVariable
            assertEquals("T", STypeVariable.symbol)
        }
    }

    @Test
    fun `get signature of fields`() = runBlocking {
        val a = cp.findClass<Generics<*>>()

        val fieldSignatures = a.fields.map { it.name to it.resolution }

        assertEquals(2, fieldSignatures.size)

        with(fieldSignatures.first()) {
            val (name, signature) = this
            signature as FieldResolutionImpl
            val fieldType = signature.fieldType as STypeVariable
            assertEquals("niceField", name)
            assertEquals("T", fieldType.symbol)
        }
        with(fieldSignatures.get(1)) {
            val (name, signature) = this
            signature as FieldResolutionImpl
            val fieldType = signature.fieldType as SParameterizedType
            assertEquals("niceList", name)
            assertEquals("java.util.List", fieldType.name)
            with(fieldType.parameterTypes) {
                assertEquals(1, size)
                with(first()) {
                    this as SBoundWildcard.SUpperBoundWildcard
                    val bondType = bound as STypeVariable
                    assertEquals("T", bondType.symbol)
                }
            }
            assertEquals("java.util.List", fieldType.name)
        }
    }


    private val JIRClassOrInterface.resolution get() = TypeSignature.of(signature)
    private val JIRMethod.resolution get() = MethodSignature.of(signature)
    private val JIRField.resolution get() = FieldSignature.of(signature)
}

