package org.opentaint.ir.impl.bytecode

import org.objectweb.asm.tree.MethodNode
import org.opentaint.ir.api.JIRAnnotation
import org.opentaint.ir.api.JIRClassOrInterface
import org.opentaint.ir.api.JIRDeclaration
import org.opentaint.ir.api.JIRMethod
import org.opentaint.ir.api.JIRParameter
import org.opentaint.ir.api.TypeName
import org.opentaint.ir.api.ext.findClass
import org.opentaint.ir.impl.fs.ClassSource
import org.opentaint.ir.impl.signature.MethodResolutionImpl
import org.opentaint.ir.impl.signature.MethodSignature
import org.opentaint.ir.impl.types.MethodInfo
import org.opentaint.ir.impl.types.TypeNameImpl

class JIRMethodImpl(
    private val methodInfo: MethodInfo,
    private val source: ClassSource,
    override val jirClass: JIRClassOrInterface
) : JIRMethod {

    override val name: String get() = methodInfo.name
    override val access: Int get() = methodInfo.access
    override val signature: String? get() = methodInfo.signature
    override val returnType: TypeName get() = TypeNameImpl(methodInfo.returnClass)

    override suspend fun exceptions(): List<JIRClassOrInterface> {
        val methodSignature = MethodSignature.of(methodInfo.signature)
        if (methodSignature is MethodResolutionImpl) {
            return methodSignature.exceptionTypes.map {
                jirClass.classpath.findClass(it.name)
            }
        }
        return emptyList()
    }

    override val declaration: JIRDeclaration
        get() = JIRDeclarationImpl.of(location = jirClass.declaration.location, this)

    override val parameters: List<JIRParameter>
        get() = methodInfo.parametersInfo.map { JIRParameterImpl(this, it) }

    override val annotations: List<JIRAnnotation>
        get() = methodInfo.annotations.map { JIRAnnotationImpl(it, jirClass.classpath) }

    override val description get() = methodInfo.desc

    override suspend fun body(): MethodNode {
        return source.fullAsmNode.methods.first { it.name == name && it.desc == methodInfo.desc }
    }

    override fun equals(other: Any?): Boolean {
        if (other == null || other !is JIRMethodImpl) {
            return false
        }
        return other.name == name && jirClass == other.jirClass && methodInfo.desc == other.methodInfo.desc
    }

    override fun hashCode(): Int {
        return 31 * jirClass.hashCode() + name.hashCode()
    }


}