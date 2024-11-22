
package org.opentaint.ir.impl

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.opentaint.ir.api.JIRClassOrInterface
import org.opentaint.ir.api.JIRField
import org.opentaint.ir.api.JIRMethod
import org.opentaint.ir.api.Pure
import org.opentaint.ir.api.ext.findClass
import org.opentaint.ir.impl.types.signature.FieldResolutionImpl
import org.opentaint.ir.impl.types.signature.FieldSignature
import org.opentaint.ir.impl.types.signature.JvmBoundWildcard
import org.opentaint.ir.impl.types.signature.JvmClassRefType
import org.opentaint.ir.impl.types.signature.JvmParameterizedType
import org.opentaint.ir.impl.types.signature.JvmPrimitiveType
import org.opentaint.ir.impl.types.signature.JvmTypeParameterDeclarationImpl
import org.opentaint.ir.impl.types.signature.JvmTypeVariable
import org.opentaint.ir.impl.types.signature.MethodResolutionImpl
import org.opentaint.ir.impl.types.signature.MethodSignature
import org.opentaint.ir.impl.types.signature.TypeResolutionImpl
import org.opentaint.ir.impl.types.signature.TypeSignature
import org.opentaint.ir.impl.types.typeParameters
import org.opentaint.ir.impl.usages.Generics

class SignatureTest: BaseTest() {

    companion object : WithDB()

    @Test
    fun `get signature of class`() = runBlocking {
        val a = cp.findClass<Generics<*>>()

        val classSignature = a.resolution

        with(classSignature) {
            this as TypeResolutionImpl
            assertEquals("java.lang.Object", (superClass as JvmClassRefType).name)
        }
    }

    @Test
    fun `get signature of methods`() = runBlocking {
        val a = cp.findClass<Generics<*>>()

        val methodSignatures = a.declaredMethods.map { it.name to it.resolution }
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
            assertEquals("void", (signature.returnType as JvmPrimitiveType).ref)
            assertEquals(1, signature.parameterTypes.size)
            with(signature.parameterTypes.first()) {
                this as JvmParameterizedType
                assertEquals(Generics::class.java.name, this.name)
                assertEquals(1, parameterTypes.size)
                with(parameterTypes.first()) {
                    this as JvmTypeVariable
                    assertEquals("T", this.symbol)
                }
            }
            assertEquals(1, signature.parameterTypes.size)
            val parameterizedType = signature.parameterTypes.first() as JvmParameterizedType
            assertEquals(1, parameterizedType.parameterTypes.size)
            assertEquals(Generics::class.java.name, parameterizedType.name)
            val typeVariable = parameterizedType.parameterTypes.first() as JvmTypeVariable
            assertEquals("T", typeVariable.symbol)
        }
        with(methodSignatures[2]) {
            val (name, signature) = this
            assertEquals("merge1", name)
            signature as MethodResolutionImpl
            assertEquals("W", (signature.returnType as JvmTypeVariable).symbol)

            assertEquals(1, signature.typeVariables.size)
            with(signature.typeVariables.first()) {
                this as JvmTypeParameterDeclarationImpl
                assertEquals("W", symbol)
                assertEquals(1, bounds?.size)
                with(bounds!!.first()) {
                    this as JvmParameterizedType
                    assertEquals("java.util.Collection", this.name)
                    assertEquals(1, parameterTypes.size)
                    with(parameterTypes.first()) {
                        this as JvmTypeVariable
                        assertEquals("T", symbol)
                    }
                }
            }
            assertEquals(1, signature.parameterTypes.size)
            val parameterizedType = signature.parameterTypes.first() as JvmParameterizedType
            assertEquals(1, parameterizedType.parameterTypes.size)
            assertEquals(Generics::class.java.name, parameterizedType.name)
            val STypeVariable = parameterizedType.parameterTypes.first() as JvmTypeVariable
            assertEquals("T", STypeVariable.symbol)
        }
    }

    @Test
    fun `get signature of fields`() = runBlocking {
        val a = cp.findClass<Generics<*>>()

        val fieldSignatures = a.declaredFields.map { it.name to it.resolution }

        assertEquals(2, fieldSignatures.size)

        with(fieldSignatures.first()) {
            val (name, signature) = this
            signature as FieldResolutionImpl
            val fieldType = signature.fieldType as JvmTypeVariable
            assertEquals("niceField", name)
            assertEquals("T", fieldType.symbol)
        }
        with(fieldSignatures.get(1)) {
            val (name, signature) = this
            signature as FieldResolutionImpl
            val fieldType = signature.fieldType as JvmParameterizedType
            assertEquals("niceList", name)
            assertEquals("java.util.List", fieldType.name)
            with(fieldType.parameterTypes) {
                assertEquals(1, size)
                with(first()) {
                    this as JvmBoundWildcard.JvmUpperBoundWildcard
                    val bondType = bound as JvmTypeVariable
                    assertEquals("T", bondType.symbol)
                }
            }
            assertEquals("java.util.List", fieldType.name)
        }
    }


    private val JIRClassOrInterface.resolution get() = TypeSignature.of(this)
    private val JIRMethod.resolution get() = MethodSignature.of(this)
    private val JIRField.resolution get() = FieldSignature.of(signature, enclosingClass.typeParameters.associateBy { it.symbol })
}

