package org.opentaint.ir.impl.types

import org.opentaint.ir.api.jvm.*
import org.opentaint.ir.api.jvm.ext.findClass
import org.opentaint.ir.api.jvm.ext.packageName
import org.opentaint.ir.api.jvm.ext.toType
import org.opentaint.ir.impl.bytecode.TypeDelegatingLookup
import org.opentaint.ir.impl.types.signature.JvmClassRefType
import org.opentaint.ir.impl.types.signature.JvmParameterizedType
import org.opentaint.ir.impl.types.signature.TypeResolutionImpl
import org.opentaint.ir.impl.types.signature.TypeSignature
import org.opentaint.ir.impl.types.substition.JIRSubstitutorImpl
import org.opentaint.ir.impl.types.substition.SafeSubstitution
import kotlin.LazyThreadSafetyMode.PUBLICATION

class JIRClassTypeImpl(
    override val classpath: JIRClasspath,
    val name: String,
    override val outerType: JIRClassTypeImpl? = null,
    private val substitutor: JIRSubstitutor = JIRSubstitutorImpl.empty,
    override val nullable: Boolean?,
    override val annotations: List<JIRAnnotation>
) : JIRClassType {

    constructor(
        classpath: JIRClasspath,
        name: String,
        outerType: JIRClassTypeImpl? = null,
        parameters: List<JvmType>,
        nullable: Boolean?,
        annotations: List<JIRAnnotation>
    ) : this(
        classpath,
        name,
        outerType,
        classpath.substitute(name, parameters, outerType?.substitutor),
        nullable,
        annotations
    )

    private val resolutionImpl by lazy(PUBLICATION) { TypeSignature.withDeclarations(jIRClass) as? TypeResolutionImpl }
    private val declaredTypeParameters by lazy(PUBLICATION) { jIRClass.typeParameters }

    override val lookup: JIRLookup<JIRTypedField, JIRTypedMethod> = TypeDelegatingLookup(
        this,
        classpath.features?.filterIsInstance<JIRLookupExtFeature>().orEmpty(),
        JIRClassTypeLookupImpl(this)
    )

    override val jIRClass: JIRClassOrInterface get() = classpath.findClass(name)

    override val access: Int
        get() = jIRClass.access

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
            if (jIRClass.isAnonymous) {
                outer.typeName + "$" + jIRClass.simpleName.substringAfter("\$")
            } else {
                outer.typeName + "." + jIRClass.simpleName.substringAfter("\$")
            }
        } else {
            jIRClass.name
        }
        name + ("<${generics}>".takeIf { generics.isNotEmpty() } ?: "")
    }

    override val typeParameters get() = declaredTypeParameters.map { it.asJIRDeclaration(jIRClass) }

    override val typeArguments: List<JIRRefType>
        get() {
            return declaredTypeParameters.map { declaration ->
                val jvmType = substitutor.substitution(declaration)
                if (jvmType != null) {
                    classpath.typeOf(jvmType) as JIRRefType
                } else {
                    JIRTypeVariableImpl(classpath, declaration.asJIRDeclaration(jIRClass), true)
                }
            }
        }

    override val superType: JIRClassType?
        get() {
            val superClass = jIRClass.superClass ?: return null
            return resolutionImpl?.let {
                val newSubstitutor = superSubstitutor(superClass, it.superClass)
                JIRClassTypeImpl(classpath, superClass.name, outerType, newSubstitutor, nullable, annotations)
            } ?: superClass.toType()
        }

    override val interfaces: List<JIRClassType>
        get() {
            return jIRClass.interfaces.map { iface ->
                val ifaceType = resolutionImpl?.interfaceType?.firstOrNull { it.isReferencesClass(iface.name) }
                if (ifaceType != null) {
                    val newSubstitutor = superSubstitutor(iface, ifaceType)
                    JIRClassTypeImpl(classpath, iface.name, null, newSubstitutor, nullable, annotations)
                } else {
                    iface.toType()
                }
            }
        }

    override val innerTypes: List<JIRClassType>
        get() {
            return jIRClass.innerClasses.map {
                val outerMethod = it.outerMethod
                val outerClass = it.outerClass

                val innerParameters = (
                        outerMethod?.allVisibleTypeParameters() ?: outerClass?.allVisibleTypeParameters()
                        )?.values?.toList().orEmpty()
                val innerSubstitutor = when {
                    it.isStatic -> JIRSubstitutorImpl.empty.newScope(innerParameters)
                    else -> substitutor.newScope(innerParameters)
                }
                JIRClassTypeImpl(classpath, it.name, this, innerSubstitutor, true, annotations)
            }
        }

    override val declaredMethods: List<JIRTypedMethod> by lazy(PUBLICATION) {
        typedMethods(true, fromSuperTypes = false, jIRClass.packageName)
    }

    override val methods: List<JIRTypedMethod> by lazy(PUBLICATION) {
        //let's calculate visible methods from super types
        typedMethods(true, fromSuperTypes = true, jIRClass.packageName)
    }

    override val declaredFields: List<JIRTypedField> by lazy(PUBLICATION) {
        typedFields(true, fromSuperTypes = false, jIRClass.packageName)
    }

    override val fields: List<JIRTypedField> by lazy(PUBLICATION) {
        typedFields(true, fromSuperTypes = true, jIRClass.packageName)
    }

    override fun copyWithNullability(nullability: Boolean?) =
        JIRClassTypeImpl(classpath, name, outerType, substitutor, nullability, annotations)

    override fun copyWithAnnotations(annotations: List<JIRAnnotation>): JIRType =
        JIRClassTypeImpl(classpath, name, outerType, substitutor, nullable, annotations)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as JIRClassTypeImpl

        if (nullable != other.nullable) return false
        if (jIRClass != other.jIRClass) return false
        return substitutor == other.substitutor
    }

    override fun hashCode(): Int {
        val result = nullable.hashCode()
        return 31 * result + jIRClass.hashCode()
    }

    private fun typedMethods(
        allMethods: Boolean,
        fromSuperTypes: Boolean,
        packageName: String
    ): List<JIRTypedMethod> {
        val classPackageName = jIRClass.packageName
        val methodSet = if (allMethods) {
            jIRClass.declaredMethods
        } else {
            jIRClass.declaredMethods.filter { !it.isConstructor && (it.isPublic || it.isProtected || (it.isPackagePrivate && packageName == classPackageName)) }
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
        val classPackageName = jIRClass.packageName

        val fieldSet = if (all) {
            jIRClass.declaredFields
        } else {
            jIRClass.declaredFields.filter { it.isPublic || it.isProtected || (it.isPackagePrivate && packageName == classPackageName) }
        }
        val directSet = fieldSet.map {
            JIRTypedFieldImpl(this@JIRClassTypeImpl, it, substitutor)
        }
        if (!fromSuperTypes) {
            return directSet
        }
        val result = directSet.toSortedSet<JIRTypedField>(UnsafeHierarchyTypedFieldComparator)
        val superTypesToCheck = (listOf(superType) + interfaces).mapNotNull { it as? JIRClassTypeImpl }

        result.addAll(
            superTypesToCheck.flatMap {
                it.typedFields(
                    false,
                    fromSuperTypes = true,
                    classPackageName
                )
            }
        )
        return result.toList()
    }

    private fun superSubstitutor(superClass: JIRClassOrInterface, superType: JvmType): JIRSubstitutor {
        val superParameters = superClass.directTypeParameters()
        val substitutions = (superType as? JvmParameterizedType)?.parameterTypes
        if (substitutions == null || superParameters.size != substitutions.size) {
            return JIRSubstitutorImpl.empty
        }
        return substitutor.fork(superParameters.mapIndexed { index, declaration -> declaration to substitutions[index] }
            .toMap())

    }

}

private fun JIRClasspath.substitute(
    name: String,
    parameters: List<JvmType>,
    substitutor: JIRSubstitutor?
): JIRSubstitutor {
    val genericsSubstitutor = features?.firstNotNullOfOrNull { it as? JIRGenericsSubstitutionFeature } ?: SafeSubstitution
    return genericsSubstitutor.substitute(findClass(name), parameters, substitutor)
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
