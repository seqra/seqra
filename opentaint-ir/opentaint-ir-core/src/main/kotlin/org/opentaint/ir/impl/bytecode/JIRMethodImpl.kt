package org.opentaint.ir.impl.bytecode

import org.opentaint.ir.api.jvm.JIRAnnotation
import org.opentaint.ir.api.jvm.JIRClassOrInterface
import org.opentaint.ir.api.jvm.JIRMethod
import org.opentaint.ir.api.jvm.JIRMethodExtFeature
import org.opentaint.ir.api.jvm.JIRMethodExtFeature.JIRFlowGraphResult
import org.opentaint.ir.api.jvm.JIRMethodExtFeature.JIRInstListResult
import org.opentaint.ir.api.jvm.JIRMethodExtFeature.JIRRawInstListResult
import org.opentaint.ir.api.jvm.JIRParameter
import org.opentaint.ir.api.jvm.TypeName
import org.opentaint.ir.api.jvm.cfg.JIRGraph
import org.opentaint.ir.api.jvm.cfg.JIRInst
import org.opentaint.ir.api.jvm.cfg.JIRInstList
import org.opentaint.ir.api.jvm.cfg.JIRRawInst
import org.opentaint.ir.impl.features.JIRFeaturesChain
import org.opentaint.ir.impl.types.AnnotationInfo
import org.opentaint.ir.impl.types.MethodInfo
import org.opentaint.ir.impl.types.TypeNameImpl
import org.objectweb.asm.TypeReference
import org.objectweb.asm.tree.MethodNode

class JIRMethodImpl(
    private val methodInfo: MethodInfo,
    private val featuresChain: JIRFeaturesChain,
    override val enclosingClass: JIRClassOrInterface
) : JIRMethod {

    override val name: String get() = methodInfo.name
    override val access: Int get() = methodInfo.access
    override val signature: String? get() = methodInfo.signature
    override val returnType: TypeName = TypeNameImpl(methodInfo.returnClass)

    override val exceptions: List<TypeName>
        get() {
            return methodInfo.exceptions.map { TypeNameImpl(it) }
        }

    override val declaration = JIRDeclarationImpl.of(location = enclosingClass.declaration.location, this)

    override val parameters: List<JIRParameter>
        get() = methodInfo.parametersInfo.map { JIRParameterImpl(this, it) }

    override val annotations: List<JIRAnnotation>
        get() = methodInfo.annotations
            .filter { it.typeRef == null } // Type annotations are stored with method in bytecode, but they are not a part of method in language
            .map { JIRAnnotationImpl(it, enclosingClass.classpath) }

    internal val returnTypeAnnotationInfos: List<AnnotationInfo>
        get() = methodInfo.annotations.filter {
            it.typeRef != null && TypeReference(it.typeRef).sort == TypeReference.METHOD_RETURN
        }

    internal fun parameterTypeAnnotationInfos(parameterIndex: Int): List<AnnotationInfo> =
        methodInfo.annotations.filter {
            it.typeRef != null && TypeReference(it.typeRef).sort == TypeReference.METHOD_FORMAL_PARAMETER
                && TypeReference(it.typeRef).formalParameterIndex == parameterIndex
        }

    override val description get() = methodInfo.desc

    override fun <T> withAsmNode(body: (MethodNode) -> T): T {
        val methodNode = enclosingClass.withAsmNode { classNode ->
            classNode.methods.first { it.name == name && it.desc == methodInfo.desc }
        }

        return synchronized(methodNode) {
            body(methodNode.jsrInlined)
        }
    }

    override val rawInstList: JIRInstList<JIRRawInst>
        get() {
            return featuresChain.call<JIRMethodExtFeature, JIRRawInstListResult> { it.rawInstList(this) }!!.rawInstList
        }

    override fun flowGraph(): JIRGraph {
        return featuresChain.call<JIRMethodExtFeature, JIRFlowGraphResult> { it.flowGraph(this) }!!.flowGraph
    }

    override val instList: JIRInstList<JIRInst> get() {
        return featuresChain.call<JIRMethodExtFeature, JIRInstListResult> { it.instList(this) }!!.instList
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

    override fun toString(): String {
        return "${enclosingClass}#$name(${parameters.joinToString { it.type.typeName }})"
    }

}
