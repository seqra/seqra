package org.opentaint.ir.impl.features.classpaths

import org.opentaint.ir.api.*
import org.opentaint.ir.api.ext.objectType
import org.opentaint.ir.impl.cfg.util.OBJECT_CLASS
import org.opentaint.ir.impl.types.TypeNameImpl
import org.objectweb.asm.Opcodes

class JIRUnknownType(override var classpath: JIRClasspath, private val name: String) : JIRClassType {

    override val lookup: JIRLookup<JIRTypedField, JIRTypedMethod> = JIRUnknownTypeLookup(this)

    override val jIRClass: JIRClassOrInterface get() = JIRUnknownClass(classpath, name)
    override val outerType: JIRClassType? = null
    override val declaredMethods: List<JIRTypedMethod> = emptyList()
    override val methods: List<JIRTypedMethod> = emptyList()
    override val declaredFields: List<JIRTypedField> = emptyList()
    override val fields: List<JIRTypedField> = emptyList()
    override val typeParameters: List<JIRTypeVariableDeclaration> = emptyList()
    override val typeArguments: List<JIRRefType> = emptyList()
    override val superType: JIRClassType get() = classpath.objectType
    override val interfaces: List<JIRClassType> = emptyList()
    override val innerTypes: List<JIRClassType> = emptyList()

    override val typeName: String
        get() = name

    override val nullable: Boolean
        get() = true
    override val annotations: List<JIRAnnotation> = emptyList()

    override fun copyWithAnnotations(annotations: List<JIRAnnotation>) = this

    override fun copyWithNullability(nullability: Boolean?) = this

    override val access: Int
        get() = Opcodes.ACC_PUBLIC
}

class JIRUnknownClassLookup(val clazz: JIRUnknownClass) : JIRLookup<JIRField, JIRMethod> {

    override fun specialMethod(name: String, description: String): JIRMethod = method(name, description)
    override fun staticMethod(name: String, description: String): JIRMethod = method(name, description)

    override fun field(name: String, typeName: TypeName?): JIRField {
        return JIRUnknownField(clazz, name, typeName ?: TypeNameImpl(OBJECT_CLASS))
    }

    override fun method(name: String, description: String): JIRMethod {
        return JIRUnknownMethod.method(clazz, name, description)
    }

}

class JIRUnknownTypeLookup(val type: JIRUnknownType) : JIRLookup<JIRTypedField, JIRTypedMethod> {

    override fun specialMethod(name: String, description: String): JIRTypedMethod = method(name, description)
    override fun staticMethod(name: String, description: String): JIRTypedMethod = method(name, description)

    override fun field(name: String, typeName: TypeName?): JIRTypedField {
        return JIRUnknownField.typedField(type, name, typeName ?: TypeNameImpl(OBJECT_CLASS))
    }

    override fun method(name: String, description: String): JIRTypedMethod {
        return JIRUnknownMethod.typedMethod(type, name, description)
    }

}