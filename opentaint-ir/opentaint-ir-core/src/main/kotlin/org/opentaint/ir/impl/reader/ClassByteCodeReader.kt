package org.opentaint.ir.impl.reader

import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.toPersistentList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.objectweb.asm.*
import java.util.jar.JarEntry
import java.util.jar.JarFile

// todo inner/outer classes?
class ClassByteCodeReader(private val jar: JarFile, private val jarEntry: JarEntry) {

    private val methods: ArrayList<MethodMetaInfo> = arrayListOf()
    private val fields: ArrayList<FieldMetaInfo> = arrayListOf()
    private val annotations: ArrayList<AnnotationMetaInfo> = arrayListOf()
    private var bytecode: ByteArray? = null

    suspend fun readClassMeta(): ClassMetaInfo {
        val bytes = withContext(Dispatchers.IO) {
            jar.getInputStream(jarEntry)
                .use { it.readBytes() }
                .also { bytecode = it }
        }
        val reader = ClassReader(bytes).also {
            it.accept(newClassVisitor(), ClassReader.SKIP_CODE and ClassReader.SKIP_DEBUG and ClassReader.SKIP_FRAMES)
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

    private fun newClassVisitor() = object : ClassVisitor(Opcodes.ASM8) {

        override fun visitAnnotation(descriptor: String?, visible: Boolean): AnnotationVisitor? {
            annotations.add(
                AnnotationMetaInfo(
                    type = Type.getType(descriptor).className,
                    visible = visible
                )
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
        ): MethodVisitor? {
            methods.add(
                MethodMetaInfo(
                    name = name,
                    access = access,
                    returnType = Type.getReturnType(descriptor).className,
                    parameters = Type.getArgumentTypes(descriptor).map { it.className }.toPersistentList()
                )
            )
            return super.visitMethod(access, name, descriptor, signature, exceptions)
        }
    }
}

class ClassMetaInfo(
    var name: String,
    var access: Int = 0,

    var methods: PersistentList<MethodMetaInfo>,
    var fields: PersistentList<FieldMetaInfo>,

    var superClass: String? = null,
    var interfaces: PersistentList<String>,
    var annotations: PersistentList<AnnotationMetaInfo>
)

class MethodMetaInfo(
    var name: String,
    var access: Int,
    var returnType: String,
    var parameters: PersistentList<String>
)

class FieldMetaInfo(
    var name: String,
    var access: Int,
    var type: String
)

class AnnotationMetaInfo(
    var visible: Boolean,
    var type: String
)