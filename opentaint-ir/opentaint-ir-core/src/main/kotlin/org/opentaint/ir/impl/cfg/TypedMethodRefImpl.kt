package org.opentaint.ir.impl.cfg

import org.opentaint.ir.api.JIRClassType
import org.opentaint.ir.api.JIRClasspath
import org.opentaint.ir.api.JIRMethod
import org.opentaint.ir.api.JIRType
import org.opentaint.ir.api.JIRTypedMethod
import org.opentaint.ir.api.MethodNotFoundException
import org.opentaint.ir.api.TypeName
import org.opentaint.ir.api.cfg.JIRInstLocation
import org.opentaint.ir.api.cfg.JIRRawCallExpr
import org.opentaint.ir.api.cfg.JIRRawInstanceExpr
import org.opentaint.ir.api.cfg.JIRRawLocal
import org.opentaint.ir.api.cfg.JIRRawSpecialCallExpr
import org.opentaint.ir.api.cfg.JIRRawStaticCallExpr
import org.opentaint.ir.api.cfg.TypedMethodRef
import org.opentaint.ir.api.cfg.VirtualTypedMethodRef
import org.opentaint.ir.api.ext.findType
import org.opentaint.ir.api.ext.jvmName
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

    protected val description: String = buildString {
        append("(")
        argTypes.forEach {
            append(it.typeName.jvmName())
        }
        append(")")
        append(returnType.typeName.jvmName())
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

    fun JIRType.throwNotFoundException(): Nothing {
        throw MethodNotFoundException(this.methodNotFoundMessage)
    }

}

class TypedStaticMethodRefImpl(
    type: JIRClassType,
    name: String,
    argTypes: List<TypeName>,
    returnType: TypeName,
) : MethodSignatureRef(type, name, argTypes, returnType) {

    constructor(classpath: JIRClasspath, raw: JIRRawStaticCallExpr) : this(
        classpath.findType(raw.declaringClass.typeName) as JIRClassType,
        raw.methodName,
        raw.argumentTypes,
        raw.returnType
    )

    override val method: JIRTypedMethod by weakLazy {
        type.lookup.staticMethod(name, description) ?: type.throwNotFoundException()
    }
}

class TypedSpecialMethodRefImpl(
    type: JIRClassType,
    name: String,
    argTypes: List<TypeName>,
    returnType: TypeName,
) : MethodSignatureRef(type, name, argTypes, returnType) {

    constructor(classpath: JIRClasspath, raw: JIRRawSpecialCallExpr) : this(
        classpath.findType(raw.declaringClass.typeName) as JIRClassType,
        raw.methodName,
        raw.argumentTypes,
        raw.returnType
    )

    override val method: JIRTypedMethod by weakLazy {
        type.lookup.specialMethod(name, description) ?: type.throwNotFoundException()
    }

}

class VirtualMethodRefImpl(
    type: JIRClassType,
    private val actualType: JIRClassType,
    name: String,
    argTypes: List<TypeName>,
    returnType: TypeName,
) : MethodSignatureRef(type, name, argTypes, returnType), VirtualTypedMethodRef {

    companion object {
        private fun JIRRawCallExpr.resolvedType(classpath: JIRClasspath): Pair<JIRClassType, JIRClassType> {
            val declared = classpath.findType(declaringClass.typeName) as JIRClassType
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
        actualType.lookup.method(name, description) ?: declaredMethod
    }

    override val declaredMethod: JIRTypedMethod by softLazy {
        type.lookup.method(name, description) ?: type.throwNotFoundException()
    }
}

class TypedMethodRefImpl(
    type: JIRClassType,
    name: String,
    argTypes: List<TypeName>,
    returnType: TypeName,
) : MethodSignatureRef(type, name, argTypes, returnType) {

    constructor(classpath: JIRClasspath, raw: JIRRawCallExpr) : this(
        classpath.findType(raw.declaringClass.typeName) as JIRClassType,
        raw.methodName,
        raw.argumentTypes,
        raw.returnType
    )

    override val method: JIRTypedMethod by softLazy {
        type.lookup.method(name, description) ?: type.throwNotFoundException()
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
    override val lineNumber: Int,
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
