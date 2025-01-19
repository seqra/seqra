package org.opentaint.ir.impl.bytecode

import org.opentaint.ir.api.*
import org.opentaint.ir.api.cfg.JIRInst
import org.opentaint.ir.api.cfg.JIRInstList
import org.opentaint.ir.api.cfg.JIRRawInst
import org.opentaint.ir.api.ext.findClass
import org.opentaint.ir.impl.fs.fullAsmNode
import org.opentaint.ir.impl.types.MethodInfo
import org.opentaint.ir.impl.types.TypeNameImpl
import org.opentaint.ir.impl.types.signature.JvmClassRefType
import org.opentaint.ir.impl.types.signature.MethodResolutionImpl
import org.opentaint.ir.impl.types.signature.MethodSignature
import org.objectweb.asm.tree.MethodNode

class JIRMethodImpl(
    private val methodInfo: MethodInfo,
    private val source: ClassSource,
    private val classpathCache: JIRMethodExtFeature,
    override val enclosingClass: JIRClassOrInterface
) : JIRMethod {

    override val name: String get() = methodInfo.name
    override val access: Int get() = methodInfo.access
    override val signature: String? get() = methodInfo.signature
    override val returnType = TypeNameImpl(methodInfo.returnClass)

    private val methodSignature = MethodSignature.of(this)

    override val exceptions: List<JIRClassOrInterface>
        get() {
            if (methodSignature is MethodResolutionImpl) {
                val classpath = enclosingClass.classpath
                return methodSignature.exceptionTypes.mapNotNull {
                    (it as? JvmClassRefType)?.let {
                        classpath.findClass(it.name)
                    }
                }
            }
            return emptyList()
        }

    override val declaration = JIRDeclarationImpl.of(location = enclosingClass.declaration.location, this)

    override val parameters: List<JIRParameter>
        get() = methodInfo.parametersInfo.map { JIRParameterImpl(this, it) }

    override val annotations: List<JIRAnnotation>
        get() = methodInfo.annotations.map { JIRAnnotationImpl(it, enclosingClass.classpath) }

    override val description get() = methodInfo.desc

    override fun asmNode(): MethodNode {
        return source.fullAsmNode.methods.first { it.name == name && it.desc == methodInfo.desc }
    }

    override val rawInstList: JIRInstList<JIRRawInst> get() = classpathCache.rawInstList(this)

    override fun flowGraph() = classpathCache.flowGraph(this)

    override val instList: JIRInstList<JIRInst> get() = classpathCache.instList(this)

    override fun equals(other: Any?): Boolean {
        if (other == null || other !is JIRMethodImpl) {
            return false
        }
        return other.name == name && enclosingClass == other.enclosingClass && methodInfo.desc == other.methodInfo.desc
    }

    override fun hashCode(): Int {
        return 31 * enclosingClass.hashCode() + name.hashCode()
    }

    override fun toString(): String {
        return "${enclosingClass}#$name(${parameters.joinToString { it.type.typeName }})"
    }

}