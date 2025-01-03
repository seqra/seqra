package org.opentaint.ir.impl.bytecode

import org.opentaint.ir.api.ClassSource
import org.opentaint.ir.api.JIRAnnotation
import org.opentaint.ir.api.JIRClassOrInterface
import org.opentaint.ir.api.JIRClasspathFeature
import org.opentaint.ir.api.JIRInstExtFeature
import org.opentaint.ir.api.JIRMethod
import org.opentaint.ir.api.JIRParameter
import org.opentaint.ir.api.cfg.JIRGraph
import org.opentaint.ir.api.cfg.JIRInst
import org.opentaint.ir.api.cfg.JIRInstList
import org.opentaint.ir.api.cfg.JIRRawInst
import org.opentaint.ir.api.ext.findClass
import org.opentaint.ir.impl.cfg.JIRGraphBuilder
import org.opentaint.ir.impl.cfg.RawInstListBuilder
import org.opentaint.ir.impl.fs.fullAsmNode
import org.opentaint.ir.impl.types.MethodInfo
import org.opentaint.ir.impl.types.TypeNameImpl
import org.opentaint.ir.impl.types.signature.MethodResolutionImpl
import org.opentaint.ir.impl.types.signature.MethodSignature
import org.objectweb.asm.tree.MethodNode

class JIRMethodImpl(
    private val methodInfo: MethodInfo,
    private val source: ClassSource,
    private val features: List<JIRClasspathFeature>?,
    override val enclosingClass: JIRClassOrInterface
) : JIRMethod {

    override val name: String get() = methodInfo.name
    override val access: Int get() = methodInfo.access
    override val signature: String? get() = methodInfo.signature
    override val returnType = TypeNameImpl(methodInfo.returnClass)

    private val methodFeatures = features?.filterIsInstance<JIRInstExtFeature>()

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

    override fun asmNode(): MethodNode {
        return source.fullAsmNode.methods.first { it.name == name && it.desc == methodInfo.desc }
    }

    override val rawInstList: JIRInstList<JIRRawInst> by lazy {
        val list: JIRInstList<JIRRawInst> = RawInstListBuilder(this, asmNode().jsrInlined).build()
        methodFeatures?.fold(list) { value, feature ->
            feature.transformRawInstList(this, value)
        } ?: list
    }

    private val lazyGraph by lazy {
        JIRGraphBuilder(this, rawInstList).buildFlowGraph()
    }

    override fun flowGraph(): JIRGraph {
        return lazyGraph
    }

    override val instList: JIRInstList<JIRInst> by lazy {
        val list: JIRInstList<JIRInst> = JIRGraphBuilder(this, rawInstList).buildInstList()
        methodFeatures?.fold(list) { value, feature ->
            feature.transformInstList(this, value)
        } ?: list

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
