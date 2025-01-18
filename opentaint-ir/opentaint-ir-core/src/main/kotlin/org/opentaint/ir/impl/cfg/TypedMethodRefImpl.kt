package org.opentaint.ir.impl.cfg

import org.opentaint.ir.api.*
import org.opentaint.ir.api.cfg.JIRInstLocation
import org.opentaint.ir.api.cfg.JIRRawCallExpr
import org.opentaint.ir.api.cfg.TypedMethodRef
import org.opentaint.ir.api.ext.*
import org.opentaint.ir.impl.softLazy
import org.opentaint.ir.impl.weakLazy

data class TypedMethodRefImpl(
    val type: JIRClassType,
    override val name: String,
    val argTypes: List<TypeName>,
    val returnType: TypeName
) : TypedMethodRef {

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

    override val method: JIRTypedMethod by weakLazy {
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
    return TypedMethodRefImpl(this, expr)
}

fun JIRType.methodRef(expr: JIRRawCallExpr): TypedMethodRef {
    return TypedMethodRefImpl(this, expr)
}

fun JIRTypedMethod.methodRef(): TypedMethodRef {
    return TypedMethodRefImpl(
        enclosingType as JIRClassType,
        method.name,
        method.parameters.map { it.type },
        method.returnType
    )
}

class JIRMethodRefImpl(method: JIRMethod): JIRMethodRef {

    private val classpath = method.enclosingClass.classpath
    private val className: String = method.enclosingClass.name
    private val name: String = method.name
    private val description: String = method.description

    override val method get() = classpath.findClass(className).findMethodOrNull(name, description)!!

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as JIRMethodRefImpl

        if (className != other.className) return false
        if (name != other.name) return false
        return description == other.description
    }

    override fun hashCode(): Int {
        var result = className.hashCode()
        result = 31 * result + name.hashCode()
        result = 31 * result + description.hashCode()
        return result
    }

}

class JIRInstLocationImpl(
    val methodRef: JIRMethodRef,
    override val index: Int,
    override val lineNumber: Int
) : JIRInstLocation {

    override val method: JIRMethod by softLazy {
        methodRef.method
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as JIRInstLocationImpl

        if (index != other.index) return false
        return methodRef == other.methodRef
    }

    override fun hashCode(): Int {
        var result = index
        result = 31 * result + methodRef.hashCode()
        return result
    }

}