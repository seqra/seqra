package org.opentaint.jvm.sast.project.spring

import org.opentaint.ir.api.jvm.JIRClassOrInterface
import org.opentaint.ir.api.jvm.JIRClasspath
import org.opentaint.ir.api.jvm.JIRDeclaration
import org.opentaint.ir.api.jvm.RegisteredLocation
import org.opentaint.ir.impl.bytecode.JIRDeclarationImpl
import org.opentaint.ir.impl.features.classpaths.VirtualLocation
import org.opentaint.ir.impl.features.classpaths.virtual.JIRVirtualClassImpl
import org.opentaint.ir.impl.features.classpaths.virtual.JIRVirtualField
import org.opentaint.ir.impl.features.classpaths.virtual.JIRVirtualMethod

class SpringGeneratedClass(
    name: String,
    val methods: MutableList<JIRVirtualMethod>,
    val fields: MutableList<JIRVirtualField>
) : JIRVirtualClassImpl(name, initialFields = fields, initialMethods = methods) {
    private lateinit var declarationLocation: RegisteredLocation

    override val isAnonymous: Boolean get() = false

    override val interfaces: List<JIRClassOrInterface> get() = emptyList()

    override val declaration: JIRDeclaration
        get() = JIRDeclarationImpl.of(declarationLocation, this)

    override fun hashCode(): Int = name.hashCode()

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        return other is SpringGeneratedClass && name == other.name
    }

    override fun toString(): String = "(spring: $name)"

    override fun bind(classpath: JIRClasspath, virtualLocation: VirtualLocation) {
        bindWithLocation(classpath, virtualLocation)
    }

    fun bindWithLocation(classpath: JIRClasspath, location: RegisteredLocation) {
        this.classpath = classpath
        this.declarationLocation = location
    }
}