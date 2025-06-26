package org.opentaint.ir.api.jvm

import org.opentaint.ir.api.common.CommonType
import org.opentaint.ir.api.jvm.ext.objectClass
import org.objectweb.asm.tree.LocalVariableNode

interface JIRTypedField : JIRAccessible {
    val field: JIRField
    val name: String
    val type: JIRType
    val enclosingType: JIRRefType
}

// CommonTypedMethod<JIRMethod, JIRInst>
interface JIRTypedMethod : JIRAccessible {
    val name: String
    val returnType: JIRType

    val typeParameters: List<JIRTypeVariableDeclaration>
    val typeArguments: List<JIRRefType>

    val parameters: List<JIRTypedMethodParameter>
    val exceptions: List<JIRRefType>
    val method: JIRMethod

    val enclosingType: JIRRefType

    fun typeOf(inst: LocalVariableNode): JIRType

}

// CommonTypedMethodParameter
interface JIRTypedMethodParameter {
    val type: JIRType
    val name: String?
    val enclosingMethod: JIRTypedMethod
}

interface JIRType : CommonType {
    val classpath: JIRClasspath
    val annotations: List<JIRAnnotation>

    fun copyWithAnnotations(annotations: List<JIRAnnotation>): JIRType
}

interface JIRPrimitiveType : JIRType {
    override val nullable: Boolean
        get() = false
}

interface JIRRefType : JIRType {
    val jIRClass: JIRClassOrInterface

    fun copyWithNullability(nullability: Boolean?): JIRRefType
}

interface JIRArrayType : JIRRefType {
    val elementType: JIRType
    val dimensions: Int

    override val jIRClass: JIRClassOrInterface
        get() = classpath.objectClass
}

interface JIRClassType : JIRRefType, JIRAccessible {

    val outerType: JIRClassType?

    val declaredMethods: List<JIRTypedMethod>
    val methods: List<JIRTypedMethod>

    val declaredFields: List<JIRTypedField>
    val fields: List<JIRTypedField>

    val typeParameters: List<JIRTypeVariableDeclaration>
    val typeArguments: List<JIRRefType>

    val superType: JIRClassType?
    val interfaces: List<JIRClassType>

    val innerTypes: List<JIRClassType>

    /**
     * lookup instance for this class. Use it to resolve field/method references from bytecode instructions
     *
     * It's not necessary that looked up method will return instance preserved in [JIRClassType.declaredFields] or
     * [JIRClassType.declaredMethods] collections
     */
    val lookup: JIRLookup<JIRTypedField, JIRTypedMethod>

}

interface JIRTypeVariable : JIRRefType {
    val symbol: String

    val bounds: List<JIRRefType>
}

interface JIRBoundedWildcard : JIRRefType {
    val upperBounds: List<JIRRefType>
    val lowerBounds: List<JIRRefType>

    override fun copyWithAnnotations(annotations: List<JIRAnnotation>): JIRType = this
}

interface JIRUnboundWildcard : JIRRefType {
    override val jIRClass: JIRClassOrInterface
        get() = classpath.objectClass

    override fun copyWithAnnotations(annotations: List<JIRAnnotation>): JIRType = this
}

interface JIRTypeVariableDeclaration {
    val symbol: String
    val bounds: List<JIRRefType>
    val owner: JIRAccessible
}
