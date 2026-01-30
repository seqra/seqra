package org.opentaint.ir.impl.features.classpaths

import org.opentaint.ir.api.jvm.*
import org.opentaint.ir.api.jvm.ext.objectType
import org.opentaint.ir.impl.cfg.util.OBJECT_CLASS
import org.opentaint.ir.impl.cfg.util.typeNameFromJvmName
import org.objectweb.asm.Opcodes

class JIRUnknownType(
    override var classpath: JIRClasspath,
    private val name: String,
    private val location: VirtualLocation,
    override var nullable: Boolean
) : JIRClassType {

    override val lookup: JIRLookup<JIRTypedField, JIRTypedMethod> = JIRUnknownTypeLookup(this)

    override val jIRClass: JIRClassOrInterface = JIRUnknownClass(classpath, name).also {
        it.bind(classpath, location)
    }

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

    override val annotations: List<JIRAnnotation> = emptyList()

    override fun copyWithAnnotations(annotations: List<JIRAnnotation>) = this

    override fun copyWithNullability(nullability: Boolean?) = this

    override val access: Int
        get() = Opcodes.ACC_PUBLIC

    override fun equals(other: Any?): Boolean {
        if (this === other) {
            return true
        }
        return other is JIRUnknownType && other.name == name
    }

    override fun hashCode(): Int = name.hashCode()

    override fun toString(): String = typeName
}

open class JIRUnknownClassLookup(val clazz: JIRClassOrInterface) : JIRLookup<JIRField, JIRMethod> {

    override fun specialMethod(name: String, description: String): JIRMethod =
        JIRUnknownMethod.method(clazz, name, access = Opcodes.ACC_PUBLIC, description)

    override fun staticMethod(name: String, description: String): JIRMethod =
        JIRUnknownMethod.method(clazz, name, access = Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC, description)

    override fun field(name: String, typeName: TypeName?, fieldKind: JIRLookup.FieldKind): JIRField {
        val staticModifier = if (fieldKind == JIRLookup.FieldKind.STATIC) Opcodes.ACC_STATIC else 0
        val fieldType = typeName ?: OBJECT_CLASS.typeNameFromJvmName()
        return JIRUnknownField(clazz, name, access = Opcodes.ACC_PUBLIC or staticModifier, fieldType)
    }

    override fun method(name: String, description: String): JIRMethod {
        return JIRUnknownMethod.method(clazz, name, access = Opcodes.ACC_PUBLIC, description)
    }

}

open class JIRUnknownTypeLookup(val type: JIRClassType) : JIRLookup<JIRTypedField, JIRTypedMethod> {

    override fun specialMethod(name: String, description: String): JIRTypedMethod =
        JIRUnknownMethod.typedMethod(type, name, access = Opcodes.ACC_PUBLIC, description)

    override fun staticMethod(name: String, description: String): JIRTypedMethod =
        JIRUnknownMethod.typedMethod(type, name, access = Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC, description)

    override fun field(name: String, typeName: TypeName?, fieldKind: JIRLookup.FieldKind): JIRTypedField {
        val staticModifier = if (fieldKind == JIRLookup.FieldKind.STATIC) Opcodes.ACC_STATIC else 0
        val fieldType = typeName ?: OBJECT_CLASS.typeNameFromJvmName()
        return JIRUnknownField.typedField(type, name, access = Opcodes.ACC_PUBLIC or staticModifier, fieldType)
    }

    override fun method(name: String, description: String): JIRTypedMethod {
        return JIRUnknownMethod.typedMethod(type, name, access = Opcodes.ACC_PUBLIC, description)
    }

}
