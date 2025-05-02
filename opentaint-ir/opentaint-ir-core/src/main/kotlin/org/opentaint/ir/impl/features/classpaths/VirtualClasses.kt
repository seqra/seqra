package org.opentaint.ir.impl.features.classpaths

import org.opentaint.ir.api.jvm.JIRByteCodeLocation
import org.opentaint.ir.api.jvm.JIRClassOrInterface
import org.opentaint.ir.api.jvm.JIRProject
import org.opentaint.ir.api.jvm.JIRClasspathExtFeature
import org.opentaint.ir.api.jvm.RegisteredLocation
import org.opentaint.ir.impl.features.classpaths.AbstractJIRResolvedResult.JIRResolvedClassResultImpl
import org.opentaint.ir.impl.features.classpaths.virtual.JIRVirtualClass
import org.opentaint.ir.impl.features.classpaths.virtual.VirtualClassesBuilder

open class VirtualClasses(
    val classes: List<JIRVirtualClass>,
    private val virtualLocation: VirtualLocation = VirtualLocation()
) : JIRClasspathExtFeature {

    companion object {

        @JvmStatic
        fun builder(factory: VirtualClassesBuilder.() -> Unit): VirtualClasses {
            return VirtualClassesBuilder().also { it.factory() }.build()
        }

        @JvmStatic
        fun builder(): VirtualClassesBuilder {
            return VirtualClassesBuilder()
        }

    }

    private val map = classes.associateBy { it.name }

    override fun tryFindClass(classpath: JIRProject, name: String): JIRClasspathExtFeature.JIRResolvedClassResult? {
        val clazz = map[name]
        if (clazz != null) {
            clazz.bind(classpath, virtualLocation)
            return JIRResolvedClassResultImpl(name, clazz)
        }
        return null
    }

    override fun findClasses(classpath: JIRProject, name: String): List<JIRClassOrInterface>? {
        return listOfNotNull(map[name])
    }

}

class VirtualLocation : RegisteredLocation {
    override val jIRLocation: JIRByteCodeLocation?
        get() = null

    override val id: Long
        get() = -1

    override val path: String = "/dev/null"

    override val isRuntime: Boolean
        get() = false

}