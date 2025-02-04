package org.opentaint.ir.impl.cfg

import org.opentaint.ir.api.JIRClassType
import org.opentaint.ir.api.JIRClasspath
import org.opentaint.ir.api.JIRMethod
import org.opentaint.ir.api.JIRType
import org.opentaint.ir.api.JIRTypedMethod
import org.opentaint.ir.api.TypeName
import org.opentaint.ir.api.cfg.JIRInstLocation
import org.opentaint.ir.api.cfg.JIRRawCallExpr
import org.opentaint.ir.api.cfg.JIRRawSpecialCallExpr
import org.opentaint.ir.api.cfg.JIRRawStaticCallExpr
import org.opentaint.ir.api.cfg.TypedMethodRef
import org.opentaint.ir.api.ext.findMethodOrNull
import org.opentaint.ir.api.ext.hasAnnotation
import org.opentaint.ir.api.ext.jvmName
import org.opentaint.ir.api.ext.objectType
import org.opentaint.ir.api.ext.packageName
import org.opentaint.ir.impl.softLazy
import org.opentaint.ir.impl.weakLazy

interface MethodSignatureRef : TypedMethodRef {
    val type: JIRClassType
    val argTypes: List<TypeName>

    fun findDeclaredMethod(filter: (JIRTypedMethod) -> Boolean): JIRTypedMethod? {
        return type.findDeclaredMethod(filter)
    }

    fun findDeclaredMethod(): JIRTypedMethod? {
        return type.findDeclaredMethod { true }
    }

    fun JIRClassType.findDeclaredMethod(filter: (JIRTypedMethod) -> Boolean): JIRTypedMethod? {
        val types = argTypes.joinToString { it.typeName }
        return this.declaredMethods.firstOrNull { it.name == name && filter(it) && it.method.parameters.joinToString { it.type.typeName } == types }
    }

    val methodNotFoundMessage: String
        get() {
            return buildString {
                append("Can't find method '")
                append(type.typeName)
                append("#")
                append(name)
                append("(")
                argTypes.forEach {
                    append(it.typeName)
                    append(",")
                }
                append(")")
            }
        }
}

data class TypedStaticMethodRefImpl(
    override val type: JIRClassType,
    override val name: String,
    override val argTypes: List<TypeName>,
    val returnType: TypeName
) : MethodSignatureRef {

    constructor(classpath: JIRClasspath, raw: JIRRawStaticCallExpr) : this(
        classpath.findTypeOrNull(raw.declaringClass.typeName) as JIRClassType,
        raw.methodName,
        raw.argumentTypes,
        raw.returnType
    )

    override val method: JIRTypedMethod by weakLazy {
        findDeclaredMethod { it.isStatic } ?: type.superType?.let {
            TypedStaticMethodRefImpl(it, name, argTypes, returnType).method
        } ?: throw IllegalStateException(methodNotFoundMessage)
    }
}

data class TypedSpecialMethodRefImpl(
    override val type: JIRClassType,
    override val name: String,
    override val argTypes: List<TypeName>,
    val returnType: TypeName
) : MethodSignatureRef {

    constructor(classpath: JIRClasspath, raw: JIRRawSpecialCallExpr) : this(
        classpath.findTypeOrNull(raw.declaringClass.typeName) as JIRClassType,
        raw.methodName,
        raw.argumentTypes,
        raw.returnType
    )

    override val method: JIRTypedMethod by weakLazy {
        findDeclaredMethod() ?: type.superType?.let {
            TypedSpecialMethodRefImpl(it, name, argTypes, returnType).method
        } ?: throw IllegalStateException(methodNotFoundMessage)
    }

}

data class TypedMethodRefImpl(
    override val type: JIRClassType,
    override val name: String,
    override val argTypes: List<TypeName>,
    val returnType: TypeName
) : MethodSignatureRef {

    constructor(classpath: JIRClasspath, raw: JIRRawCallExpr) : this(
        classpath.findTypeOrNull(raw.declaringClass.typeName) as JIRClassType,
        raw.methodName,
        raw.argumentTypes,
        raw.returnType
    )

    constructor(type: JIRType, raw: JIRRawCallExpr) : this(
        (type as? JIRClassType) ?: type.classpath.objectType,
        raw.methodName,
        raw.argumentTypes,
        raw.returnType
    )

    override val method: JIRTypedMethod by softLazy {
        type.getMethod(name, argTypes, returnType)
    }

    private fun JIRClassType.getMethod(name: String, argTypes: List<TypeName>, returnType: TypeName): JIRTypedMethod {
        val sb = buildString {
            append("(")
            argTypes.forEach {
                append(it.typeName.jvmName())
            }
            append(")")
            append(returnType.typeName.jvmName())
        }
        var methodOrNull = findMethodOrNull(name, sb)
        if (methodOrNull == null && jIRClass.packageName == "java.lang.invoke") {
            methodOrNull = findMethodOrNull {
                val method = it.method
                method.name == name && method.hasAnnotation("java.lang.invoke.MethodHandle\$PolymorphicSignature")
            } // weak consumption. may fail
        }
        return methodOrNull ?: error("Could not find a method with correct signature $typeName#$name$sb")
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