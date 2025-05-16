package org.opentaint.ir.impl.features.classpaths.virtual

import org.opentaint.ir.api.jvm.JIRAnnotation
import org.opentaint.ir.api.jvm.JIRClassOrInterface
import org.opentaint.ir.api.jvm.JIRDeclaration
import org.opentaint.ir.api.jvm.JIRMethod
import org.opentaint.ir.api.jvm.JIRParameter
import org.opentaint.ir.api.jvm.TypeName
import org.opentaint.ir.api.jvm.cfg.JIRGraph
import org.opentaint.ir.api.jvm.cfg.JIRInst
import org.opentaint.ir.api.jvm.cfg.JIRInstList
import org.opentaint.ir.api.jvm.cfg.JIRRawInst
import org.opentaint.ir.impl.bytecode.JIRDeclarationImpl
import org.opentaint.ir.impl.cfg.JIRGraphImpl
import org.opentaint.ir.impl.cfg.JIRInstListImpl
import org.objectweb.asm.Opcodes
import org.objectweb.asm.tree.MethodNode

interface JIRVirtualMethod : JIRMethod {

    fun bind(clazz: JIRClassOrInterface)

    override fun asmNode() = MethodNode()

    override val rawInstList: JIRInstList<JIRRawInst>
        get() = JIRInstListImpl(emptyList())
    override val instList: JIRInstList<JIRInst>
        get() = JIRInstListImpl(emptyList())

    override fun flowGraph(): JIRGraph = JIRGraphImpl(this, instList.instructions)
}

open class JIRVirtualParameter(
    override val index: Int,
    override val type: TypeName,
) : JIRParameter {

    override val declaration: JIRDeclaration
        get() = JIRDeclarationImpl.of(method.enclosingClass.declaration.location, this)

    override val name: String?
        get() = null

    override val annotations: List<JIRAnnotation>
        get() = emptyList()

    override val access: Int
        get() = Opcodes.ACC_PUBLIC

    override lateinit var method: JIRMethod

    fun bind(method: JIRVirtualMethod) {
        this.method = method
    }

}

open class JIRVirtualMethodImpl(
    override val name: String,
    override val access: Int = Opcodes.ACC_PUBLIC,
    override val returnType: TypeName,
    override val parameters: List<JIRVirtualParameter>,
    override val description: String,
) : JIRVirtualMethod {

    init {
        parameters.forEach { it.bind(this) }
    }

    override val declaration: JIRDeclaration
        get() = JIRDeclarationImpl.of(enclosingClass.declaration.location, this)

    override lateinit var enclosingClass: JIRClassOrInterface

    override val signature: String?
        get() = null
    override val annotations: List<JIRAnnotation>
        get() = emptyList()

    override val exceptions: List<TypeName>
        get() = emptyList()

    override fun bind(clazz: JIRClassOrInterface) {
        enclosingClass = clazz
    }

    override fun toString(): String {
        return "virtual ${enclosingClass}#$name(${parameters.joinToString { it.type.typeName }})"
    }
}
