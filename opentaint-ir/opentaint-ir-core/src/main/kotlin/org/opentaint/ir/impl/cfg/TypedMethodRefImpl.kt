package org.opentaint.ir.impl.cfg

import org.opentaint.ir.api.*
import org.opentaint.ir.api.cfg.*
import org.opentaint.ir.api.ext.findMethodOrNull
import org.opentaint.ir.api.ext.hasAnnotation
import org.opentaint.ir.api.ext.jvmName
import org.opentaint.ir.api.ext.packageName
import org.opentaint.ir.impl.cfg.util.typeName
import org.opentaint.ir.impl.softLazy
import org.opentaint.ir.impl.weakLazy
import org.objectweb.asm.Type

abstract class MethodSignatureRef(
        val type: JIRClassType,
        override val name: String,
        argTypes: List<TypeName>,
        returnType: TypeName,
) : TypedMethodRef {

    companion object {
        private val alwaysTrue: (JIRTypedMethod) -> Boolean = { true }
    }

    protected val description: String = buildString {
        append("(")
        argTypes.forEach {
            append(it.typeName.jvmName())
        }
        append(")")
        append(returnType.typeName.jvmName())
    }

    private fun List<JIRTypedMethod>.findMethod(filter: (JIRTypedMethod) -> Boolean = alwaysTrue): JIRTypedMethod? {
        return firstOrNull { it.name == name && filter(it) && it.method.description == description }
    }

    protected fun JIRClassType.findTypedMethod(filter: (JIRTypedMethod) -> Boolean = alwaysTrue): JIRTypedMethod {
        return findMethodOrNull(filter) ?: throw IllegalStateException(this.methodNotFoundMessage)
    }

    protected fun JIRClassType.findTypedMethodOrNull(filter: (JIRTypedMethod) -> Boolean = alwaysTrue): JIRTypedMethod? {
        var methodOrNull = findMethodOrNull {
            it.name == name && filter(it) && it.method.description == description
        }
        if (methodOrNull == null && jIRClass.packageName == "java.lang.invoke") {
            methodOrNull = findMethodOrNull {
                val method = it.method
                method.name == name && method.hasAnnotation("java.lang.invoke.MethodHandle\$PolymorphicSignature")
            } // weak consumption. may fail
        }
        return methodOrNull
    }

    fun JIRClassType.findClassMethod(filter: (JIRTypedMethod) -> Boolean = alwaysTrue): JIRTypedMethod? {
        return declaredMethods.findMethod(filter) ?: superType?.findClassMethod(filter)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is MethodSignatureRef) return false

        if (type != other.type) return false
        if (name != other.name) return false
        return description == other.description
    }

    override fun hashCode(): Int {
        var result = type.hashCode()
        result = 31 * result + name.hashCode()
        result = 31 * result + description.hashCode()
        return result
    }

    protected val methodNotFoundMessage: String
        get() {
            return type.methodNotFoundMessage
        }

    protected val JIRType.methodNotFoundMessage: String
        get() {
            val argumentTypes = Type.getArgumentTypes(description).map { it.descriptor.typeName() }
            return buildString {
                append("Can't find method '")
                append(typeName)
                append("#")
                append(name)
                append("(")
                argumentTypes.joinToString(", ") { it.typeName }
                append(")'")
            }
        }

}

class TypedStaticMethodRefImpl(
        type: JIRClassType,
        name: String,
        argTypes: List<TypeName>,
        returnType: TypeName
) : MethodSignatureRef(type, name, argTypes, returnType) {

    constructor(classpath: JIRClasspath, raw: JIRRawStaticCallExpr) : this(
            classpath.findTypeOrNull(raw.declaringClass.typeName) as JIRClassType,
            raw.methodName,
            raw.argumentTypes,
            raw.returnType
    )

    override val method: JIRTypedMethod by weakLazy {
        type.findClassMethod { it.isStatic } ?: throw IllegalStateException(methodNotFoundMessage)
    }
}

class TypedSpecialMethodRefImpl(
        type: JIRClassType,
        name: String,
        argTypes: List<TypeName>,
        returnType: TypeName
) : MethodSignatureRef(type, name, argTypes, returnType) {

    constructor(classpath: JIRClasspath, raw: JIRRawSpecialCallExpr) : this(
            classpath.findTypeOrNull(raw.declaringClass.typeName) as JIRClassType,
            raw.methodName,
            raw.argumentTypes,
            raw.returnType
    )

    override val method: JIRTypedMethod by weakLazy {
        type.findClassMethod() ?: throw IllegalStateException(methodNotFoundMessage)
    }

}

class VirtualMethodRefImpl(
        type: JIRClassType,
        private val actualType: JIRClassType,
        name: String,
        argTypes: List<TypeName>,
        returnType: TypeName
) : MethodSignatureRef(type, name, argTypes, returnType), VirtualTypedMethodRef {

    companion object {
        private fun JIRRawCallExpr.resolvedType(classpath: JIRClasspath): Pair<JIRClassType, JIRClassType> {
            val declared = classpath.findTypeOrNull(declaringClass.typeName) as JIRClassType
            if (this is JIRRawInstanceExpr) {
                val instance = instance
                if (instance is JIRRawLocal) {
                    val actualType = classpath.findTypeOrNull(instance.typeName.typeName)
                    if (actualType is JIRClassType) {
                        return declared to actualType
                    }
                }
            }
            return declared to declared
        }

        fun of(classpath: JIRClasspath, raw: JIRRawCallExpr): VirtualMethodRefImpl {
            val (declared, actual) = raw.resolvedType(classpath)
            return VirtualMethodRefImpl(
                    declared,
                    actual,
                    raw.methodName,
                    raw.argumentTypes,
                    raw.returnType
            )
        }

        fun of(type: JIRClassType, method: JIRTypedMethod): VirtualMethodRefImpl {
            return VirtualMethodRefImpl(
                    type, type,
                    method.name,
                    method.method.parameters.map { it.type },
                    method.method.returnType
            )
        }
    }

    override val method: JIRTypedMethod by softLazy {
        actualType.findTypedMethodOrNull { !it.isPrivate } ?: declaredMethod
    }

    override val declaredMethod: JIRTypedMethod by softLazy {
        type.findTypedMethod { !it.isPrivate }
    }
}

class TypedMethodRefImpl(
        type: JIRClassType,
        name: String,
        argTypes: List<TypeName>,
        returnType: TypeName
) : MethodSignatureRef(type, name, argTypes, returnType) {

    constructor(classpath: JIRClasspath, raw: JIRRawCallExpr) : this(
            classpath.findTypeOrNull(raw.declaringClass.typeName) as JIRClassType,
            raw.methodName,
            raw.argumentTypes,
            raw.returnType
    )

    override val method: JIRTypedMethod by softLazy {
        type.findTypedMethod()
    }

}

fun JIRClasspath.methodRef(expr: JIRRawCallExpr): TypedMethodRef {
    return when (expr) {
        is JIRRawStaticCallExpr -> TypedStaticMethodRefImpl(this, expr)
        is JIRRawSpecialCallExpr -> TypedSpecialMethodRefImpl(this, expr)
        else -> TypedMethodRefImpl(this, expr)
    }
}

fun JIRTypedMethod.methodRef(): TypedMethodRef {
    return TypedMethodRefImpl(
            enclosingType as JIRClassType,
            method.name,
            method.parameters.map { it.type },
            method.returnType
    )
}

class JIRInstLocationImpl(
        override val method: JIRMethod,
        override val index: Int,
        override val lineNumber: Int
) : JIRInstLocation {

    override fun toString(): String {
        return "${method.enclosingClass.name}#${method.name}:$lineNumber"
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as JIRInstLocationImpl

        if (index != other.index) return false
        return method == other.method
    }

    override fun hashCode(): Int {
        var result = index
        result = 31 * result + method.hashCode()
        return result
    }

}