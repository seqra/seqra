@file:JvmName("JIRClasses")

package org.opentaint.ir.api.jvm.ext

import org.opentaint.ir.api.jvm.JIRClassOrInterface
import org.opentaint.ir.api.jvm.JIRClassType
import org.opentaint.ir.api.jvm.JIRField
import org.opentaint.ir.api.jvm.JIRMethod
import org.objectweb.asm.Opcodes
import org.objectweb.asm.tree.MethodNode

val JIRClassOrInterface.isLocalOrAnonymous: Boolean
    get() {
        return outerMethod != null
    }

val JIRClassOrInterface.isLocal: Boolean
    get() {
        return outerClass != null && !isAnonymous
    }

val JIRClassOrInterface.isMemberClass: Boolean
    get() {
        return simpleBinaryName != null && !isLocalOrAnonymous
    }

val JIRClassOrInterface.isEnum: Boolean
    get() {
        return access and Opcodes.ACC_ENUM != 0 && superClass?.name == Enum::class.java.name
    }

fun JIRClassOrInterface.toType(): JIRClassType {
    return classpath.classTypeOf(this) as JIRClassType
}

val JIRClassOrInterface.packageName get() = name.substringBeforeLast(".", missingDelimiterValue = "")

const val JAVA_OBJECT = "java.lang.Object"

/**
 * find field by name
 */
fun JIRClassOrInterface.findFieldOrNull(name: String): JIRField? {
    return lookup.field(name)
}

fun JIRClassOrInterface.findDeclaredFieldOrNull(name: String): JIRField? = declaredFields.singleOrNull { it.name == name }

fun JIRClassOrInterface.findDeclaredMethodOrNull(name: String, desc: String? = null): JIRMethod? {
    return when (desc) {
        null -> declaredMethods.firstOrNull { it.name == name }
        else -> declaredMethods.singleOrNull { it.name == name && it.description == desc }
    }
}

/**
 * find method by name and description
 */
fun JIRClassOrInterface.findMethodOrNull(name: String, desc: String): JIRMethod? {
    return lookup.method(name, desc)
}

/**
 * find method by ASM node
 */
fun JIRClassOrInterface.findMethodOrNull(methodNode: MethodNode): JIRMethod? =
    findMethodOrNull(methodNode.name, methodNode.desc)

/**
 * @return null if ClassId is not enum and enum value names otherwise
 */
val JIRClassOrInterface.enumValues: List<JIRField>?
    get() {
        if (isEnum) {
            return declaredFields.filter { it.isEnum }
        }
        return null
    }

val JIRClassOrInterface.methods: List<JIRMethod>
    get() {
        return methods(allMethods = true, fromSuperTypes = true, packageName = packageName)
    }

val JIRClassOrInterface.fields: List<JIRField>
    get() {
        return fields(allFields = true, fromSuperTypes = true, packageName = packageName)
    }

private fun JIRClassOrInterface.methods(
    allMethods: Boolean,
    fromSuperTypes: Boolean,
    packageName: String
): List<JIRMethod> {
    val classPackageName = this.packageName
    val methodSet = if (allMethods) {
        declaredMethods
    } else {
        declaredMethods.filter { !it.isConstructor && (it.isPublic || it.isProtected || (it.isPackagePrivate && packageName == classPackageName)) }
    }

    if (!fromSuperTypes) {
        return methodSet
    }
    val result = methodSet.toSortedSet(UnsafeHierarchyMethodComparator)
    result.addAll(
        superClass?.methods(false, fromSuperTypes = true, packageName).orEmpty()
    )
    result.addAll(
        interfaces.flatMap {
            it.methods(false, fromSuperTypes = true, packageName).orEmpty()
        }
    )
    return result.toList()
}

private fun JIRClassOrInterface.fields(
    allFields: Boolean,
    fromSuperTypes: Boolean,
    packageName: String
): List<JIRField> {
    val classPackageName = this.packageName
    val fieldSet = if (allFields) {
        declaredFields
    } else {
        declaredFields.filter { (it.isPublic || it.isProtected || (it.isPackagePrivate && packageName == classPackageName)) }
    }

    if (!fromSuperTypes) {
        return fieldSet
    }
    val result = fieldSet.toSortedSet(UnsafeHierarchyFieldComparator)
    result.addAll(
        superClass?.fields(false, fromSuperTypes = true, packageName).orEmpty()
    )
    return result.toList()
}

val JIRClassOrInterface.constructors: List<JIRMethod>
    get() {
        return declaredMethods.filter { it.isConstructor }
    }

val JIRClassOrInterface.allSuperHierarchy: Set<JIRClassOrInterface>
    get() {
        return allSuperHierarchySequence.toMutableSet()
    }

val JIRClassOrInterface.allSuperHierarchySequence: Sequence<JIRClassOrInterface>
    get() {
        return sequence {
            superClass?.let {
                yield(it)
                yieldAll(it.allSuperHierarchySequence)
            }
            yieldAll(interfaces)
            interfaces.forEach {
                yieldAll(it.allSuperHierarchySequence)
            }
        }
    }

val JIRClassOrInterface.superClasses: List<JIRClassOrInterface>
    get() {
        val result = arrayListOf<JIRClassOrInterface>()
        var t = superClass
        while (t != null) {
            result.add(t)
            t = t.superClass
        }
        return result
    }

infix fun JIRClassOrInterface.isSubClassOf(another: JIRClassOrInterface): Boolean {
    if (another == classpath.findClassOrNull<Any>()) {
        return true
    }
    if (another == this) {
        return true
    }
    if (isInterface && !another.isInterface) {
        return false
    }
    return allSuperHierarchy.any { it == another }
}

val JIRClassOrInterface.isKotlin: Boolean
    get() {
        return annotations.any { it.matches("kotlin.Metadata") }
    }

private val JIRClassOrInterface.simpleBinaryName: String?
    get() {
        // top level class
        val enclosingClass = outerClass ?: return null
        // Otherwise, strip the enclosing class' name
        return try {
            name.substring(enclosingClass.name.length)
        } catch (ex: IndexOutOfBoundsException) {
            throw InternalError("Malformed class name", ex)
        }
    }

// call with SAFE. comparator works only on methods from one hierarchy
internal object UnsafeHierarchyFieldComparator : Comparator<JIRField> {

    override fun compare(o1: JIRField, o2: JIRField): Int {
        return o1.name.compareTo(o2.name)
    }
}
