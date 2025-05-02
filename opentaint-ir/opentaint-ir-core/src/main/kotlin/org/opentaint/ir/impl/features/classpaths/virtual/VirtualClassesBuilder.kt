package org.opentaint.ir.impl.features.classpaths.virtual

import org.opentaint.ir.api.core.TypeName
import org.opentaint.ir.api.jvm.PredefinedJIRPrimitives
import org.opentaint.ir.api.jvm.ext.jvmName
import org.opentaint.ir.impl.features.classpaths.VirtualClasses
import org.opentaint.ir.impl.types.TypeNameImpl
import org.objectweb.asm.Opcodes

open class VirtualClassesBuilder {
    open class VirtualClassBuilder(private var _name: String) {
        var access: Int = Opcodes.ACC_PUBLIC
            private set
        var fields: ArrayList<VirtualFieldBuilder> = ArrayList()
            private set
        var methods: ArrayList<VirtualMethodBuilder> = ArrayList()
            private set

        val name: String get() = _name

        fun access(code: Int) = apply {
            this.access = code
        }

        fun name(name: String) = apply {
            this._name = name
        }

        fun field(name: String, access: Int = Opcodes.ACC_PUBLIC, callback: VirtualFieldBuilder.() -> Unit = {}) =
            apply {
                fields.add(VirtualFieldBuilder(name).also {
                    it.access(access)
                    it.callback()
                })
            }

        fun method(name: String, access: Int = Opcodes.ACC_PUBLIC, callback: VirtualMethodBuilder.() -> Unit = {}) =
            apply {
                methods.add(VirtualMethodBuilder(name).also {
                    it.access(access)
                    it.callback()
                })
            }

        fun build(): JIRVirtualClass {
            return JIRVirtualClassImpl(
                _name,
                access,
                fields.map { it.build() },
                methods.map { it.build() },
            )
        }
    }

    open class VirtualFieldBuilder(private var _name: String = "_virtual_") {
        companion object {
            private val defType = TypeNameImpl("java.lang.Object")
        }

        val name: String get() = _name

        var access: Int = Opcodes.ACC_PUBLIC
            private set
        var type: TypeName = defType
            private set

        fun access(code: Int) = apply {
            this.access = code
        }

        fun type(name: String) = apply {
            type = TypeNameImpl(name)
        }

        fun name(name: String) = apply {
            this._name = name
        }

        open fun build(): JIRVirtualField {
            return JIRVirtualFieldImpl(_name, access, type)
        }

    }

    open class VirtualMethodBuilder(private var _name: String = "_virtual_") {
        val name: String get() = _name

        var access = Opcodes.ACC_PUBLIC
            private set
        var returnType: TypeName = TypeNameImpl(PredefinedJIRPrimitives.Void)
            private set
        var parameters: List<TypeName> = emptyList()
            private set

        fun access(code: Int) = apply {
            this.access = code
        }

        fun params(vararg p: String) = apply {
            parameters = p.map { TypeNameImpl(it) }.toList()
        }

        fun name(name: String) = apply {
            this._name = name
        }

        fun returnType(name: String) = apply {
            returnType = TypeNameImpl(name)
        }

        open val description: String
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
                _name,
                access,
                returnType,
                parameters.mapIndexed { index, typeName -> JIRVirtualParameter(index, typeName) },
                description
            )
        }
    }

    private val classes = ArrayList<VirtualClassBuilder>()

    fun virtualClass(name: String, access: Int = Opcodes.ACC_PUBLIC, callback: VirtualClassBuilder.() -> Unit = {}) =
        apply {
            classes.add(VirtualClassBuilder(name).also {
                it.access(access)
                it.callback()
            })
        }

    fun buildClasses() = classes.map { it.build() }
    fun build() = VirtualClasses(buildClasses())
}