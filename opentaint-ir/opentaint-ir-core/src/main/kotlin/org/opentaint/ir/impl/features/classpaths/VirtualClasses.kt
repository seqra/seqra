package org.opentaint.ir.impl.features.classpaths

import org.opentaint.ir.api.JIRByteCodeLocation
import org.opentaint.ir.api.JIRClassOrInterface
import org.opentaint.ir.api.JIRClasspath
import org.opentaint.ir.api.JIRClasspathExtFeature
import org.opentaint.ir.api.RegisteredLocation
import org.opentaint.ir.impl.features.classpaths.virtual.JIRVirtualClass
import org.opentaint.ir.impl.features.classpaths.virtual.VirtualClassesBuilder
import java.util.*

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

    override fun tryFindClass(classpath: JIRClasspath, name: String): Optional<JIRClassOrInterface>? {
        val clazz = map[name]
        if (clazz != null) {
            clazz.bind(classpath, virtualLocation)
            return Optional.of(clazz)
        }
        return null
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