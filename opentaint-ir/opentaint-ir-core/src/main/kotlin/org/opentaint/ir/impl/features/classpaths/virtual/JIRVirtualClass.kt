package org.opentaint.ir.impl.features.classpaths.virtual

import org.opentaint.ir.api.JIRAnnotation
import org.opentaint.ir.api.JIRClassOrInterface
import org.opentaint.ir.api.JIRClasspath
import org.opentaint.ir.api.JIRDeclaration
import org.opentaint.ir.api.JIRMethod
import org.opentaint.ir.api.ext.isInterface
import org.opentaint.ir.api.ext.objectClass
import org.opentaint.ir.impl.bytecode.JIRDeclarationImpl
import org.opentaint.ir.impl.features.classpaths.VirtualLocation
import org.objectweb.asm.Opcodes
import org.objectweb.asm.tree.ClassNode

interface JIRVirtualClass : JIRClassOrInterface {
    override val declaredFields: List<JIRVirtualField>
    override val declaredMethods: List<JIRVirtualMethod>

    fun bind(classpath: JIRClasspath, virtualLocation: VirtualLocation) {
    }
}

open class JIRVirtualClassImpl(
    override val name: String,
    override val access: Int = Opcodes.ACC_PUBLIC,
    override val declaredFields: List<JIRVirtualField>,
    override val declaredMethods: List<JIRVirtualMethod>
) : JIRVirtualClass {

    init {
        declaredFields.forEach { it.bind(this) }
        declaredMethods.forEach { it.bind(this) }
    }

    private lateinit var virtualLocation: VirtualLocation

    override val declaration: JIRDeclaration
        get() = JIRDeclarationImpl.of(virtualLocation, this)

    override val annotations: List<JIRAnnotation>
        get() = emptyList()

    override val signature: String?
        get() = null

    override val outerClass: JIRClassOrInterface?
        get() = null

    override val innerClasses: List<JIRClassOrInterface>
        get() = emptyList()

    override val interfaces: List<JIRClassOrInterface>
        get() = emptyList()

    override val simpleName: String get() = name.substringAfterLast(".")

    override fun bytecode(): ClassNode {
        throw IllegalStateException("Can't get ASM node for Virtual class")
    }

    override val isAnonymous: Boolean
        get() = false

    override fun binaryBytecode(): ByteArray {
        throw IllegalStateException("Can't get bytecode for Virtual class")
    }

    override val superClass: JIRClassOrInterface?
        get() = when (isInterface) {
            true -> null
            else -> classpath.objectClass
        }

    override val outerMethod: JIRMethod?
        get() = null

    override lateinit var classpath: JIRClasspath

    override fun bind(classpath: JIRClasspath, virtualLocation: VirtualLocation) {
        this.classpath = classpath
        this.virtualLocation = virtualLocation
    }

}