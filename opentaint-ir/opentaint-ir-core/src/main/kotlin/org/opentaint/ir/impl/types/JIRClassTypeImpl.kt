
package org.opentaint.ir.impl.types

import org.opentaint.ir.api.JIRClassOrInterface
import org.opentaint.ir.api.JIRClassType
import org.opentaint.ir.api.JIRRefType
import org.opentaint.ir.api.JIRTypedField
import org.opentaint.ir.api.JIRTypedMethod
import org.opentaint.ir.api.ext.isConstructor
import org.opentaint.ir.api.ext.isPackagePrivate
import org.opentaint.ir.api.ext.isProtected
import org.opentaint.ir.api.ext.isPublic
import org.opentaint.ir.api.ext.isStatic
import org.opentaint.ir.api.ext.packageName
import org.opentaint.ir.api.ext.toType
import org.opentaint.ir.impl.types.signature.JvmClassRefType
import org.opentaint.ir.impl.types.signature.JvmParameterizedType
import org.opentaint.ir.impl.types.signature.JvmType
import org.opentaint.ir.impl.types.signature.TypeResolutionImpl
import org.opentaint.ir.impl.types.signature.TypeSignature
import org.opentaint.ir.impl.types.substition.JIRSubstitutor
import org.opentaint.ir.impl.types.substition.substitute

open class JIRClassTypeImpl(
    override val jirClass: JIRClassOrInterface,
    override val outerType: JIRClassTypeImpl? = null,
    private val substitutor: JIRSubstitutor = JIRSubstitutor.empty,
    override val nullable: Boolean?
) : JIRClassType {

    constructor(
        jirClass: JIRClassOrInterface,
        outerType: JIRClassTypeImpl? = null,
        parameters: List<JvmType>,
        nullable: Boolean?
    ) : this(jirClass, outerType, jirClass.substitute(parameters, outerType?.substitutor), nullable)

    private val resolutionImpl by lazy(LazyThreadSafetyMode.NONE) { TypeSignature.withDeclarations(jirClass) as? TypeResolutionImpl }
    private val declaredTypeParameters by lazy(LazyThreadSafetyMode.NONE) { jirClass.typeParameters }

    override val classpath get() = jirClass.classpath

    override val typeName: String by lazy {
        val generics = if (substitutor.substitutions.isEmpty()) {
            declaredTypeParameters.joinToString { it.symbol }
        } else {
            declaredTypeParameters.joinToString {
                substitutor.substitution(it)?.displayName ?: it.symbol
            }
        }
        val outer = outerType
        val name = if (outer != null) {
            outer.typeName + "." + jirClass.simpleName
        } else {
            jirClass.name
        }
        name + ("<${generics}>".takeIf { generics.isNotEmpty() } ?: "")
    }

    override val typeParameters get() = declaredTypeParameters.map { it.asJcDeclaration(jirClass) }

    override val typeArguments: List<JIRRefType>
        get() {
            return declaredTypeParameters.map { declaration ->
                val jvmType = substitutor.substitution(declaration)
                if (jvmType != null) {
                    classpath.typeOf(jvmType) as JIRRefType
                } else {
                    JIRTypeVariableImpl(classpath, declaration.asJcDeclaration(jirClass), true)
                }
            }
        }


    override val superType: JIRClassType? by lazy(LazyThreadSafetyMode.NONE) {
        val superClass = jirClass.superClass ?: return@lazy null
        resolutionImpl?.let {
            val newSubstitutor = superSubstitutor(superClass, it.superClass)
            JIRClassTypeImpl(superClass, outerType, newSubstitutor, nullable)
        } ?: superClass.toType()
    }

    override val interfaces: List<JIRClassType> by lazy(LazyThreadSafetyMode.NONE) {
        jirClass.interfaces.map { iface ->
            val ifaceType = resolutionImpl?.interfaceType?.firstOrNull { it.isReferencesClass(iface.name) }
            if (ifaceType != null) {
                val newSubstitutor = superSubstitutor(iface, ifaceType)
                JIRClassTypeImpl(iface, null, newSubstitutor, nullable)
            } else {
                iface.toType()
            }
        }
    }

    override val innerTypes: List<JIRClassType> by lazy(LazyThreadSafetyMode.NONE) {
        jirClass.innerClasses.map {
            val outerMethod = it.outerMethod
            val outerClass = it.outerClass

            val innerParameters = (
                    outerMethod?.allVisibleTypeParameters() ?: outerClass?.allVisibleTypeParameters()
                    )?.values?.toList().orEmpty()
            val innerSubstitutor = when {
                it.isStatic -> JIRSubstitutor.empty.newScope(innerParameters)
                else -> substitutor.newScope(innerParameters)
            }
            JIRClassTypeImpl(it, this, innerSubstitutor, true)
        }
    }

    override val declaredMethods by lazy(LazyThreadSafetyMode.NONE) {
        typedMethods(true, fromSuperTypes = false, jirClass.packageName)
    }

    override val methods by lazy(LazyThreadSafetyMode.NONE) {
        //let's calculate visible methods from super types
        typedMethods(true, fromSuperTypes = true, jirClass.packageName)
    }

    override val declaredFields by lazy(LazyThreadSafetyMode.NONE) {
        typedFields(true, fromSuperTypes = false, jirClass.packageName)
    }

    override val fields by lazy(LazyThreadSafetyMode.NONE) {
        typedFields(true, fromSuperTypes = true, jirClass.packageName)
    }

    override fun copyWithNullability(nullability: Boolean?) = JIRClassTypeImpl(jirClass, outerType, substitutor, nullability)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as JIRClassTypeImpl

        if (nullable != other.nullable) return false
        if (typeName != other.typeName) return false

        return true
    }

    override fun hashCode(): Int {
        val result = nullable.hashCode()
        return 31 * result + typeName.hashCode()
    }

    private fun typedMethods(
        allMethods: Boolean,
        fromSuperTypes: Boolean,
        packageName: String
    ): List<JIRTypedMethod> {
        val classPackageName = jirClass.packageName
        val methodSet = if (allMethods) {
            jirClass.declaredMethods
        } else {
            jirClass.declaredMethods.filter { !it.isConstructor && (it.isPublic || it.isProtected || (it.isPackagePrivate && packageName == classPackageName)) }
        }
        val declaredMethods: List<JIRTypedMethod> = methodSet.map {
            JIRTypedMethodImpl(this@JIRClassTypeImpl, it, substitutor)
        }

        if (!fromSuperTypes) {
            return declaredMethods
        }
        val result = declaredMethods.toSortedSet(UnsafeHierarchyTypedMethodComparator)
        result.addAll(
            (superType as? JIRClassTypeImpl)?.typedMethods(false, fromSuperTypes = true, packageName).orEmpty()
        )
        result.addAll(
            interfaces.flatMap {
                (it as? JIRClassTypeImpl)?.typedMethods(false, fromSuperTypes = true, packageName).orEmpty()
            }
        )
        return result.toList()
    }

    private fun typedFields(all: Boolean, fromSuperTypes: Boolean, packageName: String): List<JIRTypedField> {
        val classPackageName = jirClass.packageName

        val fieldSet = if (all) {
            jirClass.declaredFields
        } else {
            jirClass.declaredFields.filter { it.isPublic || it.isProtected || (it.isPackagePrivate && packageName == classPackageName) }
        }
        val directSet = fieldSet.map {
            JIRTypedFieldImpl(this@JIRClassTypeImpl, it, substitutor)
        }
        if (!fromSuperTypes) {
            return directSet
        }
        val result = directSet.toSortedSet<JIRTypedField>(UnsafeHierarchyTypedFieldComparator)
        result.addAll(
            (superType as? JIRClassTypeImpl)?.typedFields(
                false,
                fromSuperTypes = true,
                classPackageName
            ).orEmpty()
        )
        return result.toList()
    }


    private fun superSubstitutor(superClass: JIRClassOrInterface, superType: JvmType): JIRSubstitutor {
        val superParameters = superClass.directTypeParameters()
        val substitutions = (superType as? JvmParameterizedType)?.parameterTypes
        if (substitutions == null || superParameters.size != substitutions.size) {
            return JIRSubstitutor.empty
        }
        return substitutor.fork(superParameters.mapIndexed { index, declaration -> declaration to substitutions[index] }
            .toMap())

    }

}

fun JvmType.isReferencesClass(name: String): Boolean {
    return when (val type = this) {
        is JvmClassRefType -> type.name == name
        is JvmParameterizedType -> type.name == name
        is JvmParameterizedType.JvmNestedType -> type.name == name
        else -> false
    }
}

// call with SAFE. comparator works only on methods from one hierarchy
private object UnsafeHierarchyTypedMethodComparator : Comparator<JIRTypedMethod> {

    override fun compare(o1: JIRTypedMethod, o2: JIRTypedMethod): Int {
        return when (o1.name) {
            o2.name -> o1.method.description.compareTo(o2.method.description)
            else -> o1.name.compareTo(o2.name)
        }
    }
}

// call with SAFE. comparator works only on methods from one hierarchy
private object UnsafeHierarchyTypedFieldComparator : Comparator<JIRTypedField> {

    override fun compare(o1: JIRTypedField, o2: JIRTypedField): Int {
        return o1.name.compareTo(o2.name)
    }
}