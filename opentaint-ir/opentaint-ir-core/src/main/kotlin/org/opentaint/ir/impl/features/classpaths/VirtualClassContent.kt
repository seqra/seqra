package org.opentaint.ir.impl.features.classpaths

import org.opentaint.ir.api.JIRClassOrInterface
import org.opentaint.ir.api.JIRClasspathFeature
import org.opentaint.ir.api.JIRField
import org.opentaint.ir.api.JIRMethod
import org.opentaint.ir.impl.features.classpaths.virtual.VirtualClassesBuilder

class VirtualClassContent(private val builders: List<VirtualClassContentBuilder>) : JIRClasspathFeature {

    companion object {

        @JvmStatic
        fun builder(factory: VirtualClassContentsBuilder.() -> Unit): VirtualClassContent {
            return VirtualClassContentsBuilder().also { it.factory() }.build()
        }

        @JvmStatic
        fun builder(): VirtualClassContentsBuilder {
            return VirtualClassContentsBuilder()
        }

    }

    override fun fieldsOf(clazz: JIRClassOrInterface): List<JIRField>? {
        if (builders.isNotEmpty()) {
            builders.forEach {
                if (it.matcher(clazz)) {
                    return it.fieldConfigs.map { config ->
                        val builder = VirtualClassesBuilder.VirtualFieldBuilder()
                        config(builder, clazz)
                        builder.build().also { field ->
                            field.bind(clazz)
                        }
                    }
                }
            }
        }
        return null
    }

    override fun methodsOf(clazz: JIRClassOrInterface): List<JIRMethod>? {
        if (builders.isNotEmpty()) {
            builders.forEach {
                if (it.matcher(clazz)) {
                    return it.methodConfigs.map { config ->
                        val builder = VirtualClassesBuilder.VirtualMethodBuilder()
                        config(builder, clazz)
                        builder.build().also { method ->
                            method.bind(clazz)
                        }
                    }
                }
            }
        }
        return null

    }

}

class VirtualClassContentsBuilder() {
    internal val builders = ArrayList<VirtualClassContentBuilder>()

    fun content(builder: VirtualClassContentBuilder.() -> Unit) = apply {
        VirtualClassContentBuilder().also {
            it.builder()
            builders.add(it)
        }
    }

    fun build() = VirtualClassContent(builders)

}

class VirtualClassContentBuilder {
    internal var matcher: (JIRClassOrInterface) -> Boolean = { false }
    internal val fieldConfigs = ArrayList<(VirtualClassesBuilder.VirtualFieldBuilder, JIRClassOrInterface) -> Unit>()
    internal val methodConfigs = ArrayList<(VirtualClassesBuilder.VirtualMethodBuilder, JIRClassOrInterface) -> Unit>()

    fun matcher(m: (JIRClassOrInterface) -> Boolean) = apply {
        matcher = m
    }

    fun field(configure: (VirtualClassesBuilder.VirtualFieldBuilder, JIRClassOrInterface) -> Unit) = apply {
        fieldConfigs.add(configure)
    }

    fun method(configure: (VirtualClassesBuilder.VirtualMethodBuilder, JIRClassOrInterface) -> Unit) = apply {
        methodConfigs.add(configure)
    }

}