package org.opentaint.ir.impl.features.classpaths

import org.opentaint.ir.api.*
import org.opentaint.ir.api.ext.jIRdbName
import org.opentaint.ir.impl.features.classpaths.AbstractJIRResolvedResult.JIRResolvedClassResultImpl
import org.opentaint.ir.impl.features.classpaths.virtual.JIRVirtualClassImpl
import org.opentaint.ir.impl.features.classpaths.virtual.JIRVirtualFieldImpl
import org.opentaint.ir.impl.features.classpaths.virtual.JIRVirtualMethodImpl
import org.opentaint.ir.impl.features.classpaths.virtual.JIRVirtualParameter
import org.opentaint.ir.impl.types.JIRTypedFieldImpl
import org.opentaint.ir.impl.types.JIRTypedMethodImpl
import org.opentaint.ir.impl.types.TypeNameImpl
import org.opentaint.ir.impl.types.substition.JIRSubstitutor
import org.objectweb.asm.Type

class JIRUnknownClass(override var classpath: JIRClasspath, name: String) : JIRVirtualClassImpl(
    name,
    initialFields = emptyList(),
    initialMethods = emptyList()
) {
    override val lookup: JIRLookup<JIRField, JIRMethod> = JIRUnknownClassLookup(this)
}

class JIRUnknownMethod(
    enclosingClass: JIRUnknownClass,
    name: String,
    description: String,
    returnType: TypeName,
    params: List<TypeName>
) : JIRVirtualMethodImpl(
    name,
    returnType = returnType,
    parameters = params.mapIndexed { index, typeName -> JIRVirtualParameter(index, typeName) },
    description = description
) {

    companion object {
        fun method(type: JIRUnknownClass, name: String, description: String): JIRMethod {
            val methodType = Type.getMethodType(description)
            val returnType = TypeNameImpl(methodType.returnType.className.jIRdbName())
            val paramsType = methodType.argumentTypes.map { TypeNameImpl(it.className.jIRdbName()) }
            return JIRUnknownMethod(type, name, description, returnType, paramsType)
        }

        fun typedMethod(type: JIRUnknownType, name: String, description: String): JIRTypedMethod {
            return JIRTypedMethodImpl(
                type,
                method(type.jIRClass as JIRUnknownClass, name, description),
                JIRSubstitutor.empty
            )
        }
    }

    init {
        bind(enclosingClass)
    }
}

class JIRUnknownField(enclosingClass: JIRUnknownClass, name: String, type: TypeName) :
    JIRVirtualFieldImpl(name, type = type) {

    companion object {

        fun typedField(type: JIRClassType, name: String, fieldType: TypeName): JIRTypedField {
            return JIRTypedFieldImpl(
                type,
                JIRUnknownField(type.jIRClass as JIRUnknownClass, name, fieldType),
                JIRSubstitutor.empty
            )
        }

    }

    init {
        bind(enclosingClass)
    }
}

object UnknownClasses : JIRClasspathExtFeature {
    override fun tryFindClass(classpath: JIRClasspath, name: String): JIRClasspathExtFeature.JIRResolvedClassResult {
        return JIRResolvedClassResultImpl(name, JIRUnknownClass(classpath, name))
    }

    override fun tryFindType(classpath: JIRClasspath, name: String): JIRClasspathExtFeature.JIRResolvedTypeResult {
        return AbstractJIRResolvedResult.JIRResolvedTypeResultImpl(name, JIRUnknownType(classpath, name))
    }
}

val JIRClasspath.isResolveAllToUnknown: Boolean get() = isInstalled(UnknownClasses)