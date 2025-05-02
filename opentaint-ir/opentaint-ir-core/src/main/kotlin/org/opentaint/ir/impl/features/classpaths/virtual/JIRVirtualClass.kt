package org.opentaint.ir.impl.features.classpaths.virtual

import org.opentaint.ir.api.*
import org.opentaint.ir.api.jvm.JIRAnnotation
import org.opentaint.ir.api.jvm.JIRClassOrInterface
import org.opentaint.ir.api.jvm.ext.objectClass
import org.opentaint.ir.impl.bytecode.JIRClassLookupImpl
import org.opentaint.ir.impl.bytecode.JIRDeclarationImpl
import org.opentaint.ir.impl.bytecode.joinFeatureFields
import org.opentaint.ir.impl.bytecode.joinFeatureMethods
import org.opentaint.ir.impl.features.JIRFeaturesChain
import org.opentaint.ir.impl.features.classpaths.VirtualLocation
import org.opentaint.ir.api.jvm.JIRDeclaration
import org.opentaint.ir.api.jvm.JIRField
import org.opentaint.ir.api.jvm.JIRLookup
import org.opentaint.ir.api.jvm.JIRMethod
import org.opentaint.ir.api.jvm.JIRProject
import org.objectweb.asm.Opcodes
import org.objectweb.asm.tree.ClassNode

@JvmDefaultWithoutCompatibility
interface JIRVirtualClass : JIRClassOrInterface {
    override val declaredFields: List<JIRVirtualField>
    override val declaredMethods: List<JIRVirtualMethod>

    override fun <T> extensionValue(key: String): T? = null

    fun bind(classpath: JIRProject, virtualLocation: VirtualLocation) {
    }
}

open class JIRVirtualClassImpl(
    override val name: String,
    override val access: Int = Opcodes.ACC_PUBLIC,
    private val initialFields: List<JIRVirtualField>,
    private val initialMethods: List<JIRVirtualMethod>
) : JIRVirtualClass {

    private val featuresChain get() = JIRFeaturesChain(classpath.features.orEmpty())
    private lateinit var virtualLocation: VirtualLocation

    override val lookup: JIRLookup<JIRField, JIRMethod> = JIRClassLookupImpl(this)

    override val declaredFields: List<JIRVirtualField> by lazy(LazyThreadSafetyMode.PUBLICATION) {
        val default = initialFields.onEach { it.bind(this) }
        default.joinFeatureFields(this, featuresChain).map { it as JIRVirtualField }
    }

    override val declaredMethods: List<JIRVirtualMethod> by lazy(LazyThreadSafetyMode.PUBLICATION) {
        val default = initialMethods.onEach { it.bind(this) }
        default.joinFeatureMethods(this, featuresChain).map { it as JIRVirtualMethod }
    }

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

    override fun asmNode(): ClassNode {
        throw IllegalStateException("Can't get ASM node for Virtual class")
    }

    override val isAnonymous: Boolean
        get() = false

    override fun bytecode(): ByteArray {
        throw IllegalStateException("Can't get bytecode for Virtual class")
    }

    override val superClass: JIRClassOrInterface?
        get() = when (isInterface) {
            true -> null
            else -> classpath.objectClass
        }

    override val outerMethod: JIRMethod?
        get() = null

    override lateinit var classpath: JIRProject

    override fun bind(classpath: JIRProject, virtualLocation: VirtualLocation) {
        this.classpath = classpath
        this.virtualLocation = virtualLocation
    }

}