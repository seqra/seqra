
package org.opentaint.ir.impl.bytecode

import org.objectweb.asm.tree.MethodNode
import org.opentaint.ir.api.ClassSource
import org.opentaint.ir.api.JIRAnnotation
import org.opentaint.ir.api.JIRClassOrInterface
import org.opentaint.ir.api.JIRMethod
import org.opentaint.ir.api.JIRParameter
import org.opentaint.ir.api.ext.findClass
import org.opentaint.ir.impl.cfg.JIRRawInstListImpl
import org.opentaint.ir.impl.cfg.RawInstListBuilder
import org.opentaint.ir.impl.fs.fullAsmNode
import org.opentaint.ir.impl.types.MethodInfo
import org.opentaint.ir.impl.types.TypeNameImpl
import org.opentaint.ir.impl.types.signature.MethodResolutionImpl
import org.opentaint.ir.impl.types.signature.MethodSignature

class JIRMethodImpl(
    private val methodInfo: MethodInfo,
    private val source: ClassSource,
    override val enclosingClass: JIRClassOrInterface
) : JIRMethod {

    override val name: String get() = methodInfo.name
    override val access: Int get() = methodInfo.access
    override val signature: String? get() = methodInfo.signature
    override val returnType = TypeNameImpl(methodInfo.returnClass)

    override val exceptions: List<JIRClassOrInterface> by lazy(LazyThreadSafetyMode.NONE) {
        val methodSignature = MethodSignature.of(this)
        if (methodSignature is MethodResolutionImpl) {
            methodSignature.exceptionTypes.map {
                enclosingClass.classpath.findClass(it.name)
            }
        } else {
            emptyList()
        }
    }

    override val declaration = JIRDeclarationImpl.of(location = enclosingClass.declaration.location, this)

    override val parameters: List<JIRParameter>
        get() = methodInfo.parametersInfo.map { JIRParameterImpl(this, it) }

    override val annotations: List<JIRAnnotation>
        get() = methodInfo.annotations.map { JIRAnnotationImpl(it, enclosingClass.classpath) }

    override val description get() = methodInfo.desc

    override fun body(): MethodNode {
        return source.fullAsmNode.methods.first { it.name == name && it.desc == methodInfo.desc }
    }

    override fun instructionList(): JIRRawInstListImpl {
        return RawInstListBuilder(this, body().jsrInlined).build()
    }

    override fun equals(other: Any?): Boolean {
        if (other == null || other !is JIRMethodImpl) {
            return false
        }
        return other.name == name && enclosingClass == other.enclosingClass && methodInfo.desc == other.methodInfo.desc
    }

    override fun hashCode(): Int {
        return 31 * enclosingClass.hashCode() + name.hashCode()
    }

}
