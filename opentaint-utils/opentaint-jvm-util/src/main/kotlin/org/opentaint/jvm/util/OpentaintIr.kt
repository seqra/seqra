package org.opentaint.jvm.util

import org.opentaint.ir.api.jvm.JIRArrayType
import org.opentaint.ir.api.jvm.JIRClassOrInterface
import org.opentaint.ir.api.jvm.JIRClassType
import org.opentaint.ir.api.jvm.JIRClasspath
import org.opentaint.ir.api.jvm.JIRClasspathFeature
import org.opentaint.ir.api.jvm.JIRField
import org.opentaint.ir.api.jvm.JIRMethod
import org.opentaint.ir.api.jvm.JIRPrimitiveType
import org.opentaint.ir.api.jvm.JIRRefType
import org.opentaint.ir.api.jvm.JIRType
import org.opentaint.ir.api.jvm.JIRTypeVariable
import org.opentaint.ir.api.jvm.JIRTypedField
import org.opentaint.ir.api.jvm.JIRTypedMethod
import org.opentaint.ir.api.jvm.MethodNotFoundException
import org.opentaint.ir.api.jvm.TypeName
import org.opentaint.ir.api.jvm.cfg.JIRInst
import org.opentaint.ir.api.jvm.ext.findFieldOrNull
import org.opentaint.ir.api.jvm.ext.jIRdbSignature
import org.opentaint.ir.api.jvm.ext.toType
import org.opentaint.ir.approximation.Approximations
import org.opentaint.ir.impl.JIRClasspathImpl
import org.opentaint.ir.impl.bytecode.JIRClassOrInterfaceImpl
import org.opentaint.ir.impl.bytecode.JIRFieldImpl
import org.opentaint.ir.impl.bytecode.joinFeatureFields
import org.opentaint.ir.impl.bytecode.joinFeatureMethods
import org.opentaint.ir.impl.bytecode.toJIRMethod
import org.opentaint.ir.impl.features.JIRFeaturesChain
import org.opentaint.ir.impl.features.classpaths.ClasspathCache
import org.opentaint.ir.impl.types.JIRClassTypeImpl
import org.opentaint.ir.impl.types.TypeNameImpl
import org.objectweb.asm.tree.MethodNode
import java.lang.reflect.Constructor
import java.lang.reflect.Executable
import java.lang.reflect.Field
import java.lang.reflect.Method
import kotlin.reflect.jvm.javaField
import kotlin.reflect.jvm.javaMethod

val JIRClasspath.stringType: JIRType
    get() = findClassOrNull("java.lang.String")!!.toType()

fun JIRClasspath.findFieldByFullNameOrNull(fieldFullName: String): JIRField? {
    val className = fieldFullName.substringBeforeLast('.')
    val fieldName = fieldFullName.substringAfterLast('.')
    val jIRClass = findClassOrNull(className) ?: return null
    return jIRClass.declaredFields.find { it.name == fieldName }
}

operator fun JIRClasspath.get(klass: Class<*>) = this.findClassOrNull(klass.typeName)

val JIRClassOrInterface.typename
    get() = TypeNameImpl.fromTypeName(this.name)

fun JIRType.toStringType(): String =
    when (this) {
        is JIRClassType -> jIRClass.name
        is JIRTypeVariable -> jIRClass.name
        is JIRArrayType -> "${elementType.toStringType()}[]"
        else -> typeName
    }

fun JIRType.getTypename() = TypeNameImpl.fromTypeName(this.typeName)

val JIRInst.enclosingClass
    get() = this.location.method.enclosingClass

val JIRInst.enclosingMethod
    get() = this.location.method

fun Class<*>.toJIRType(jIRClasspath: JIRClasspath): JIRType? {
    return jIRClasspath.findTypeOrNull(this.typeName)
}

fun JIRType.toJIRClass(): JIRClassOrInterface? =
    when (this) {
        is JIRRefType -> jIRClass
        is JIRPrimitiveType -> null
        else -> error("Unexpected type")
    }

fun JIRField.findJavaField(javaFields: List<Field>): Field? {
    val field = javaFields.find { it.name == name }
    check(field == null || field.type.typeName == this.type.typeName) {
        "invalid field: types of field $field and $this differ ${field?.type?.typeName} and ${this.type.typeName}"
    }
    return field
}

fun JIRField.toJavaField(classLoader: ClassLoader): Field? {
    try {
        val type = enclosingClass.toJavaClass(classLoader)
        val fields = if (isStatic) type.staticFields else type.allInstanceFields
        return this.findJavaField(fields)
    } catch (e: Throwable) {
        return null
    }
}

val JIRClassOrInterface.allDeclaredFields
    get(): List<JIRField> {
        val result = HashMap<String, JIRField>()
        var current: JIRClassOrInterface? = this
        do {
            current!!.declaredFields.forEach {
                result.putIfAbsent("${it.name}${it.type}", it)
            }
            current = current.superClass
        } while (current != null)
        return result.values.toList()
    }

fun TypeName.toJIRType(jIRClasspath: JIRClasspath): JIRType? = jIRClasspath.findTypeOrNull(typeName)
fun TypeName.toJIRClassOrInterface(jIRClasspath: JIRClasspath): JIRClassOrInterface? = jIRClasspath.findClassOrNull(typeName)

fun JIRMethod.toJavaExecutable(classLoader: ClassLoader): Executable? {
    val type = enclosingClass.toType().toJavaClass(classLoader)
    return (type.methods + type.declaredMethods).find { it.jIRdbSignature == this.jIRdbSignature }
        ?: (type.constructors + type.declaredConstructors).find { it.jIRdbSignature == this.jIRdbSignature }
}

fun JIRMethod.toJavaMethod(classLoader: ClassLoader): Method {
    val klass = Class.forName(enclosingClass.name, false, classLoader)
    return (klass.methods + klass.declaredMethods).find { it.isSameSignatures(this) }
        ?: throw MethodNotFoundException("Can't find method $name in classpath")
}

fun JIRMethod.toJavaConstructor(classLoader: ClassLoader): Constructor<*> {
    require(isConstructor) { "Can't convert not constructor to constructor" }
    val klass = Class.forName(enclosingClass.name, true, classLoader)
    return (klass.constructors + klass.declaredConstructors).find { it.jIRdbSignature == this.jIRdbSignature }
        ?: throw MethodNotFoundException("Can't find constructor of class ${enclosingClass.name}")
}

val Method.jIRdbSignature: String
    get() {
        val parameterTypesAsString = parameterTypes.toJIRdbFormat()
        return name + "(" + parameterTypesAsString + ")" + returnType.typeName + ";"
    }

val Constructor<*>.jIRdbSignature: String
    get() {
        val methodName = "<init>"
        //Because of jIRdb
        val returnType = "void;"
        val parameterTypesAsString = parameterTypes.toJIRdbFormat()
        return "$methodName($parameterTypesAsString)$returnType"
    }

private fun Array<Class<*>>.toJIRdbFormat(): String =
    if (isEmpty()) "" else joinToString(";", postfix = ";") { it.typeName }

fun Method.isSameSignatures(jIRMethod: JIRMethod) =
    jIRdbSignature == jIRMethod.jIRdbSignature

fun Constructor<*>.isSameSignatures(jIRMethod: JIRMethod) =
    jIRdbSignature == jIRMethod.jIRdbSignature

fun JIRMethod.isSameSignature(mn: MethodNode): Boolean =
    withAsmNode { it.isSameSignature(mn) }

val JIRMethod.toTypedMethod: JIRTypedMethod
    get() = this.enclosingClass.toType().declaredMethods.first { typed -> typed.method == this }

val JIRClassOrInterface.enumValuesField: JIRTypedField
    get() = toType().findFieldOrNull("\$VALUES") ?: error("No \$VALUES field found for the enum type $this")

val JIRClassType.name: String
    get() = if (this is JIRClassTypeImpl) name else jIRClass.name

val JIRClassType.outerClassInstanceField: JIRTypedField?
    get() = fields.singleOrNull { it.name == "this\$0" }

@Suppress("RecursivePropertyAccessor")
val JIRClassType.allFields: List<JIRTypedField>
    get() = declaredFields + (superType?.allFields ?: emptyList())

@Suppress("RecursivePropertyAccessor")
val JIRClassOrInterface.allFields: List<JIRField>
    get() = declaredFields + (superClass?.allFields ?: emptyList())

val JIRClassType.allInstanceFields: List<JIRTypedField>
    get() = allFields.filter { !it.isStatic }

val kotlin.reflect.KProperty<*>.javaName: String
    get() = this.javaField?.name ?: error("No java name for field $this")

val kotlin.reflect.KFunction<*>.javaName: String
    get() = this.javaMethod?.name ?: error("No java name for method $this")

class JIRCpWithoutApproximations(val cp: JIRClasspath) : JIRClasspath by cp {
    init {
        check(cp !is JIRCpWithoutApproximations)
    }

    override val features: List<JIRClasspathFeature> by lazy {
        cp.featuresWithoutApproximations()
    }

    private fun JIRClasspath.featuresWithoutApproximations(): List<JIRClasspathFeature> {
        if (this !is JIRClasspathImpl)
            error("unexpected JIRClasspath: $this")

        val featuresChainField = this.javaClass.getDeclaredField("featuresChain")
        featuresChainField.isAccessible = true
        val featuresChain = featuresChainField.get(this) as JIRFeaturesChain
        return featuresChain.features.filterNot { it is Approximations || it is ClasspathCache }
    }

    private class JIRClassWithoutApproximations(
        private val cls: JIRClassOrInterface, private val cp: JIRCpWithoutApproximations
    ) : JIRClassOrInterface by cls {
        override val classpath: JIRClasspath get() = cp
        private val featuresChain by lazy {
            JIRFeaturesChain(cp.features)
        }

        override val declaredFields: List<JIRField> by lazy {
            if (cls !is JIRClassOrInterfaceImpl)
                return@lazy cls.declaredFields

            val default = cls.info.fields.map { JIRFieldImpl(this, it) }
            default.joinFeatureFields(this, featuresChain)
        }

        override val declaredMethods: List<JIRMethod> by lazy {
            if (cls !is JIRClassOrInterfaceImpl)
                return@lazy cls.declaredMethods

            val default = cls.info.methods.map { toJIRMethod(it, featuresChain) }
            default.joinFeatureMethods(this, featuresChain)
        }
    }

    private val classWithoutApproximationsCache = hashMapOf<JIRClassOrInterface, JIRClassWithoutApproximations>()

    private val JIRClassOrInterface.withoutApproximations: JIRClassOrInterface get() {
        if (this is JIRClassWithoutApproximations) return this

        check(classpath === cp)

        return classWithoutApproximationsCache.getOrPut(this) {
            JIRClassWithoutApproximations(this, this@JIRCpWithoutApproximations)
        }
    }

    private val JIRField.withoutApproximations: JIRField? get() {
        return this.enclosingClass.withoutApproximations.declaredFields.find {
            it.name == this.name && it.isStatic == this.isStatic
        }
    }

    val JIRField.isOriginalField: Boolean get() = withoutApproximations != null
}

fun JIRClasspath.cpWithoutApproximations(): JIRCpWithoutApproximations {
    if (this is JIRCpWithoutApproximations) return this
    return JIRCpWithoutApproximations(this)
}
