package org.opentaint.ir.api.jvm

import org.opentaint.ir.api.common.CommonArrayType
import org.opentaint.ir.api.common.CommonClassType
import org.opentaint.ir.api.common.CommonRefType
import org.opentaint.ir.api.common.CommonType
import org.opentaint.ir.api.common.CommonTypedField
import org.opentaint.ir.api.common.CommonTypedMethod
import org.opentaint.ir.api.common.CommonTypedMethodParameter
import org.opentaint.ir.api.jvm.cfg.JIRInst
import org.opentaint.ir.api.jvm.ext.objectClass
import org.objectweb.asm.tree.LocalVariableNode

interface JIRTypedField : JIRAccessible, CommonTypedField {
    override val field: JIRField
    override val type: JIRType
    val enclosingType: JIRRefType
}

interface JIRTypedMethod : JIRAccessible, CommonTypedMethod<JIRMethod, JIRInst> {
    val name: String
    override val returnType: JIRType

    val typeParameters: List<JIRTypeVariableDeclaration>
    val typeArguments: List<JIRRefType>

    override val parameters: List<JIRTypedMethodParameter>
    val exceptions: List<JIRRefType>
    override val method: JIRMethod

    val enclosingType: JIRRefType

    fun typeOf(inst: LocalVariableNode): JIRType

}

interface JIRTypedMethodParameter : CommonTypedMethodParameter {
    override val type: JIRType
    override val name: String?
    override val enclosingMethod: JIRTypedMethod
}

interface JIRType : CommonType {
    val classpath: JIRClasspath
    override val typeName: String

    override val nullable: Boolean?
    val annotations: List<JIRAnnotation>

    fun copyWithAnnotations(annotations: List<JIRAnnotation>): JIRType
}

interface JIRPrimitiveType : JIRType {
    override val nullable: Boolean
        get() = false
}

interface JIRRefType : JIRType, CommonRefType {

    override val jIRClass: JIRClassOrInterface

    fun copyWithNullability(nullability: Boolean?): JIRRefType
}

interface JIRArrayType : JIRRefType, CommonArrayType {
    override val elementType: JIRType

    override val jIRClass: JIRClassOrInterface
        get() = classpath.objectClass

    override val dimensions: Int
}

interface JIRClassType : JIRRefType, JIRAccessible, CommonClassType {

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
