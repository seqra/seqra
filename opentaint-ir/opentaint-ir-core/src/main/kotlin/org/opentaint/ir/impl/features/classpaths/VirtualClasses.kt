package org.opentaint.ir.impl.features.classpaths

import org.opentaint.ir.api.JIRByteCodeLocation
import org.opentaint.ir.api.JIRClassOrInterface
import org.opentaint.ir.api.JIRClasspath
import org.opentaint.ir.api.JIRClasspathFeature
import org.opentaint.ir.api.RegisteredLocation
import org.opentaint.ir.impl.features.classpaths.virtual.JIRVirtualClass
import org.opentaint.ir.impl.features.classpaths.virtual.VirtualClassesBuilder

open class VirtualClasses(
    val classes: List<JIRVirtualClass>,
    private val virtualLocation: VirtualLocation = VirtualLocation()
) : JIRClasspathFeature {

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

    override fun tryFindClass(classpath: JIRClasspath, name: String): JIRClassOrInterface? {
        return map[name]?.also {
            it.bind(classpath, virtualLocation)
        }
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