package org.opentaint.ir.api.jvm

import org.opentaint.ir.api.common.CommonMethod
import org.opentaint.ir.api.common.CommonMethodParameter
import org.opentaint.ir.api.common.CommonTypeName
import org.opentaint.ir.api.jvm.cfg.JIRGraph
import org.opentaint.ir.api.jvm.cfg.JIRInst
import org.opentaint.ir.api.jvm.cfg.JIRInstList
import org.opentaint.ir.api.jvm.cfg.JIRRawInst
import org.opentaint.ir.api.jvm.ext.CONSTRUCTOR
import org.objectweb.asm.Opcodes
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.MethodNode

@JvmDefaultWithoutCompatibility
interface JIRClassOrInterface : JIRAnnotatedSymbol, JIRAccessible {

    val classpath: JIRClasspath

    val declaredFields: List<JIRField>
    val declaredMethods: List<JIRMethod>

    val simpleName: String
    val signature: String?
    val isAnonymous: Boolean

    fun <T> withAsmNode(body: (ClassNode) -> T): T

    fun bytecode(): ByteArray

    val superClass: JIRClassOrInterface?
    val outerMethod: JIRMethod?
    val outerClass: JIRClassOrInterface?
    val interfaces: List<JIRClassOrInterface>
    val innerClasses: List<JIRClassOrInterface>

    fun <T> extensionValue(key: String): T?

    /**
     * lookup instance for this class. Use it to resolve field/method references from bytecode instructions
     *
     * It's not necessary that looked up method will return instance preserved in [JIRClassOrInterface.declaredFields] or
     * [JIRClassOrInterface.declaredMethods] collections
     */
    val lookup: JIRLookup<JIRField, JIRMethod>

    val isAnnotation: Boolean
        get() {
            return access and Opcodes.ACC_ANNOTATION != 0
        }

    /**
     * is class is interface
     */
    val isInterface: Boolean
        get() {
            return access and Opcodes.ACC_INTERFACE != 0
        }

}

interface JIRAnnotation : JIRSymbol {

    val visible: Boolean
    val jIRClass: JIRClassOrInterface?

    val values: Map<String, Any?>

    fun matches(className: String): Boolean

}

interface JIRMethod : JIRSymbol, JIRAnnotatedSymbol, JIRAccessible, CommonMethod {

    /** reference to class */
    val enclosingClass: JIRClassOrInterface

    val description: String

    override val returnType: TypeName

    val signature: String?
    override val parameters: List<JIRParameter>

    val exceptions: List<TypeName>

    fun <T> withAsmNode(body: (MethodNode) -> T): T

    override fun flowGraph(): JIRGraph

    val rawInstList: JIRInstList<JIRRawInst>
    val instList: JIRInstList<JIRInst>

    /**
     * is method has `native` modifier
     */
    val isNative: Boolean
        get() {
            return access and Opcodes.ACC_NATIVE != 0
        }

    /**
     * is item has `synchronized` modifier
     */
    val isSynchronized: Boolean
        get() {
            return access and Opcodes.ACC_SYNCHRONIZED != 0
        }

    /**
     * return true if method is constructor
     */
    val isConstructor: Boolean
        get() {
            return name == CONSTRUCTOR
        }

    val isClassInitializer: Boolean
        get() {
            return name == "<clinit>"
        }

}

interface JIRField : JIRAnnotatedSymbol, JIRAccessible {
    val enclosingClass: JIRClassOrInterface
    val type: TypeName
    val signature: String?
}

interface JIRParameter : JIRAnnotated, JIRAccessible, CommonMethodParameter {
    override val type: TypeName
    val name: String?
    val index: Int
    val method: JIRMethod
}

interface TypeName : CommonTypeName
