package org.opentaint.ir.impl.bytecode

import org.opentaint.ir.api.JIRAnnotation
import org.opentaint.ir.api.JIRClassOrInterface
import org.opentaint.ir.api.JIRMethod
import org.opentaint.ir.api.JIRMethodExtFeature
import org.opentaint.ir.api.JIRParameter
import org.opentaint.ir.api.TypeName
import org.opentaint.ir.api.cfg.JIRInst
import org.opentaint.ir.api.cfg.JIRInstList
import org.opentaint.ir.api.cfg.JIRRawInst
import org.opentaint.ir.impl.softLazy
import org.opentaint.ir.impl.types.MethodInfo
import org.opentaint.ir.impl.types.TypeNameImpl
import org.opentaint.ir.impl.weakLazy
import org.objectweb.asm.tree.MethodNode

class JIRMethodImpl(
    private val methodInfo: MethodInfo,
    private val classpathCache: JIRMethodExtFeature,
    override val enclosingClass: JIRClassOrInterface
) : JIRMethod {

    override val name: String get() = methodInfo.name
    override val access: Int get() = methodInfo.access
    override val signature: String? get() = methodInfo.signature
    override val returnType = TypeNameImpl(methodInfo.returnClass)

    override val exceptions: List<TypeName>
        get() {
            return methodInfo.exceptions.map { TypeNameImpl(it) }
        }

    override val declaration = JIRDeclarationImpl.of(location = enclosingClass.declaration.location, this)

    override val parameters: List<JIRParameter>
        get() = methodInfo.parametersInfo.map { JIRParameterImpl(this, it) }

    override val annotations: List<JIRAnnotation>
        get() = methodInfo.annotations.map { JIRAnnotationImpl(it, enclosingClass.classpath) }

    override val description get() = methodInfo.desc

    override fun asmNode(): MethodNode {
        return enclosingClass.asmNode().methods.first { it.name == name && it.desc == methodInfo.desc }.jsrInlined
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