package org.opentaint.ir.impl.reader

import org.opentaint.ir.api.ByteCodeLocation
import kotlinx.collections.immutable.toPersistentList
import org.objectweb.asm.*
import java.io.InputStream

// todo inner/outer classes?
class ClassByteCodeReader(private val input: InputStream) {

    private val methods: ArrayList<MethodMetaInfo> = arrayListOf()
    private val fields: ArrayList<FieldMetaInfo> = arrayListOf()
    private val annotations: ArrayList<AnnotationMetaInfo> = arrayListOf()

    fun readClassMeta(): ClassMetaInfo {
        val bytes = input.use { it.readBytes() }

        val reader = ClassReader(bytes).also {
            it.accept(newClassVisitor(), ClassReader.SKIP_CODE or ClassReader.SKIP_DEBUG or ClassReader.SKIP_FRAMES)
        }
        return ClassMetaInfo(
            name = Type.getObjectType(reader.className).className,
            access = reader.access,
            superClass = reader.superName?.let { Type.getObjectType(it).className },
            interfaces = reader.interfaces.map { Type.getObjectType(it).className }.toPersistentList(),
            methods = methods.toPersistentList(),
            fields = fields.toPersistentList(),
            annotations = annotations.toPersistentList()
        )
    }

    fun readMethod(methodName: String, methodSignature: String): Any {
        val bytes = input.use { it.readBytes() }

        return ClassReader(bytes).also {
            it.accept(object : ClassVisitor(Opcodes.ASM8) {
                override fun visitMethod(
                    access: Int,
                    name: String?,
                    descriptor: String?,
                    signature: String?,
                    exceptions: Array<out String>?
                ): MethodVisitor {
                    if (methodName == name && methodSignature == signature) {
                        return object : MethodVisitor(Opcodes.ASM8) {
                            override fun visitCode() {
                                super.visitCode()
                            }
                        }
                    }
                    return super.visitMethod(access, name, descriptor, signature, exceptions)
                }
            }, ClassReader.SKIP_DEBUG or ClassReader.SKIP_FRAMES)
        }
    }

    private fun newClassVisitor() = object : ClassVisitor(Opcodes.ASM8) {

        override fun visitAnnotation(descriptor: String?, visible: Boolean): AnnotationVisitor? {
            annotations.add(
                descriptor.asAnnotation(visible)
            )
            return super.visitAnnotation(descriptor, visible)
        }

        override fun visitField(
            access: Int,
            name: String,
            descriptor: String?,
            signature: String?,
            value: Any?
        ): FieldVisitor? {
            fields.add(
                FieldMetaInfo(
                    name = name,
                    access = access,
                    type = Type.getType(descriptor).className
                )
            )
            return super.visitField(access, name, descriptor, signature, value)
        }

        override fun visitMethod(
            access: Int,
            name: String,
            descriptor: String?,
            signature: String?,
            exceptions: Array<out String>?
        ): MethodVisitor {
            val list = arrayListOf<AnnotationMetaInfo>()
            methods.add(
                MethodMetaInfo(
                    name = name,
                    access = access,
                    returnType = Type.getReturnType(descriptor).className,
                    parameters = Type.getArgumentTypes(descriptor).map { it.className }.toPersistentList()
                ) {
                    list.toPersistentList()
                }
            )
            return object : MethodVisitor(Opcodes.ASM8) {

                override fun visitAnnotation(descriptor: String?, visible: Boolean): AnnotationVisitor? {
                    list.add(descriptor.asAnnotation(visible))
                    return super.visitAnnotation(descriptor, visible)
                }
            }
        }
    }

    private fun String?.asAnnotation(
        visible: Boolean
    ) = AnnotationMetaInfo(
        type = Type.getType(this).className,
        visible = visible
    )
}

suspend fun ByteCodeLocation.readClasses(): Sequence<ClassMetaInfo> {
    return classesByteCode().map { ClassByteCodeReader(it).readClassMeta() }
}

suspend fun ByteCodeLocation.reader(fullName: String): ClassByteCodeReader {
    val input = resolve(fullName) ?: throw IllegalStateException("can't find bytecode for class $fullName")
    return ClassByteCodeReader(input)
}
