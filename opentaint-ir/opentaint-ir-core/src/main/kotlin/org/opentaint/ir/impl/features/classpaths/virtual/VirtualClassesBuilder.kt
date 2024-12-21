package org.opentaint.ir.impl.features.classpaths.virtual

import org.opentaint.ir.api.PredefinedPrimitives
import org.opentaint.ir.api.TypeName
import org.opentaint.ir.api.ext.jvmName
import org.opentaint.ir.impl.features.classpaths.VirtualClasses
import org.opentaint.ir.impl.types.TypeNameImpl
import org.objectweb.asm.Opcodes

open class VirtualClassesBuilder {
    open class VirtualClassBuilder(var name: String) {
        var access: Int = Opcodes.ACC_PUBLIC
        var fields: ArrayList<VirtualFieldBuilder> = ArrayList()
        var methods: ArrayList<VirtualMethodBuilder> = ArrayList()

        fun name(name: String) = apply {
            this.name = name
        }

        fun newField(name: String, access: Int = Opcodes.ACC_PUBLIC, callback: VirtualFieldBuilder.() -> Unit = {}) =
            apply {
                fields.add(VirtualFieldBuilder(name).also {
                    it.access = access
                    it.callback()
                })
            }

        fun newMethod(name: String, access: Int = Opcodes.ACC_PUBLIC, callback: VirtualMethodBuilder.() -> Unit = {}) =
            apply {
                methods.add(VirtualMethodBuilder(name).also {
                    it.access = access
                    it.callback()
                })
            }

        fun build(): JIRVirtualClass {
            return JIRVirtualClassImpl(
                name,
                access,
                fields.map { it.build() },
                methods.map { it.build() },
            )
        }
    }

    open class VirtualFieldBuilder(var name: String = "_virtual_") {
        companion object {
            private val defType = TypeNameImpl("java.lang.Object")
        }

        var access: Int = Opcodes.ACC_PUBLIC
        var type: TypeName = defType

        fun type(name: String) = apply {
            type = TypeNameImpl(name)
        }

        fun name(name: String) = apply {
            this.name = name
        }

        fun build(): JIRVirtualField {
            return JIRVirtualFieldImpl(name, access, type)
        }

    }

    open class VirtualMethodBuilder(var name: String = "_virtual_") {

        var access = Opcodes.ACC_PUBLIC
        var returnType: TypeName = TypeNameImpl(PredefinedPrimitives.Void)
        var parameters: List<TypeName> = emptyList()

        fun params(vararg p: String) = apply {
            parameters = p.map { TypeNameImpl(it) }.toList()
        }

        fun name(name: String) = apply {
            this.name = name
        }

        fun returnType(name: String) = apply {
            returnType = TypeNameImpl(name)
        }

        val description: String
            get() {
                return buildString {
                    append("(")
                    parameters.forEach {
                        append(it.typeName.jvmName())
                    }
                    append(")")
                    append(returnType.typeName.jvmName())
                }
            }

        open fun build(): JIRVirtualMethod {
            return JIRVirtualMethodImpl(
                name,
                access,
                returnType,
                parameters.mapIndexed { index, typeName -> JIRVirtualParameter(index, typeName) },
                description
            )
        }
    }

    private val classes = ArrayList<VirtualClassBuilder>()

    fun newClass(name: String, access: Int = Opcodes.ACC_PUBLIC, callback: VirtualClassBuilder.() -> Unit = {}) {
        classes.add(VirtualClassBuilder(name).also {
            it.access = access
            it.callback()
        })
    }

    fun buildClasses() = classes.map { it.build() }
    fun build() = VirtualClasses(buildClasses())
}