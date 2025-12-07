package org.opentaint.jvm.util

import org.opentaint.ir.api.jvm.JcArrayType
import org.opentaint.ir.api.jvm.JcClassOrInterface
import org.opentaint.ir.api.jvm.JcClassType
import org.opentaint.ir.api.jvm.JcClasspath
import org.opentaint.ir.api.jvm.JcClasspathFeature
import org.opentaint.ir.api.jvm.JcField
import org.opentaint.ir.api.jvm.JcMethod
import org.opentaint.ir.api.jvm.JcPrimitiveType
import org.opentaint.ir.api.jvm.JcRefType
import org.opentaint.ir.api.jvm.JcType
import org.opentaint.ir.api.jvm.JcTypeVariable
import org.opentaint.ir.api.jvm.JcTypedField
import org.opentaint.ir.api.jvm.JcTypedMethod
import org.opentaint.ir.api.jvm.MethodNotFoundException
import org.opentaint.ir.api.jvm.TypeName
import org.opentaint.ir.api.jvm.cfg.JcInst
import org.opentaint.ir.api.jvm.ext.findFieldOrNull
import org.opentaint.ir.api.jvm.ext.jcdbSignature
import org.opentaint.ir.api.jvm.ext.toType
import org.opentaint.ir.approximation.Approximations
import org.opentaint.ir.impl.JcClasspathImpl
import org.opentaint.ir.impl.bytecode.JcClassOrInterfaceImpl
import org.opentaint.ir.impl.bytecode.JcFieldImpl
import org.opentaint.ir.impl.bytecode.joinFeatureFields
import org.opentaint.ir.impl.bytecode.joinFeatureMethods
import org.opentaint.ir.impl.bytecode.toJcMethod
import org.opentaint.ir.impl.features.JcFeaturesChain
import org.opentaint.ir.impl.features.classpaths.ClasspathCache
import org.opentaint.ir.impl.types.JcClassTypeImpl
import org.opentaint.ir.impl.types.TypeNameImpl
import org.objectweb.asm.tree.MethodNode
import java.lang.reflect.Constructor
import java.lang.reflect.Executable
import java.lang.reflect.Field
import java.lang.reflect.Method
import kotlin.reflect.jvm.javaField
import kotlin.reflect.jvm.javaMethod

val JcClasspath.stringType: JcType
    get() = findClassOrNull("java.lang.String")!!.toType()

fun JcClasspath.findFieldByFullNameOrNull(fieldFullName: String): JcField? {
    val className = fieldFullName.substringBeforeLast('.')
    val fieldName = fieldFullName.substringAfterLast('.')
    val jcClass = findClassOrNull(className) ?: return null
    return jcClass.declaredFields.find { it.name == fieldName }
}

operator fun JcClasspath.get(klass: Class<*>) = this.findClassOrNull(klass.typeName)

val JcClassOrInterface.typename
    get() = TypeNameImpl.fromTypeName(this.name)

fun JcType.toStringType(): String =
    when (this) {
        is JcClassType -> jcClass.name
        is JcTypeVariable -> jcClass.name
        is JcArrayType -> "${elementType.toStringType()}[]"
        else -> typeName
    }

fun JcType.getTypename() = TypeNameImpl.fromTypeName(this.typeName)

val JcInst.enclosingClass
    get() = this.location.method.enclosingClass

val JcInst.enclosingMethod
    get() = this.location.method

fun Class<*>.toJcType(jcClasspath: JcClasspath): JcType? {
    return jcClasspath.findTypeOrNull(this.typeName)
}

fun JcType.toJcClass(): JcClassOrInterface? =
    when (this) {
        is JcRefType -> jcClass
        is JcPrimitiveType -> null
        else -> error("Unexpected type")
    }

fun JcField.findJavaField(javaFields: List<Field>): Field? {
    val field = javaFields.find { it.name == name }
    check(field == null || field.type.typeName == this.type.typeName) {
        "invalid field: types of field $field and $this differ ${field?.type?.typeName} and ${this.type.typeName}"
    }
    return field
}

fun JcField.toJavaField(classLoader: ClassLoader): Field? {
    try {
        val type = enclosingClass.toJavaClass(classLoader)
        val fields = if (isStatic) type.staticFields else type.allInstanceFields
        return this.findJavaField(fields)
    } catch (e: Throwable) {
        return null
    }
}

val JcClassOrInterface.allDeclaredFields
    get(): List<JcField> {
        val result = HashMap<String, JcField>()
        var current: JcClassOrInterface? = this
        do {
            current!!.declaredFields.forEach {
                result.putIfAbsent("${it.name}${it.type}", it)
            }
            current = current.superClass
        } while (current != null)
        return result.values.toList()
    }

fun TypeName.toJcType(jcClasspath: JcClasspath): JcType? = jcClasspath.findTypeOrNull(typeName)
fun TypeName.toJcClassOrInterface(jcClasspath: JcClasspath): JcClassOrInterface? = jcClasspath.findClassOrNull(typeName)

fun JcMethod.toJavaExecutable(classLoader: ClassLoader): Executable? {
    val type = enclosingClass.toType().toJavaClass(classLoader)
    return (type.methods + type.declaredMethods).find { it.jcdbSignature == this.jcdbSignature }
        ?: (type.constructors + type.declaredConstructors).find { it.jcdbSignature == this.jcdbSignature }
}

fun JcMethod.toJavaMethod(classLoader: ClassLoader): Method {
    val klass = Class.forName(enclosingClass.name, false, classLoader)
    return (klass.methods + klass.declaredMethods).find { it.isSameSignatures(this) }
        ?: throw MethodNotFoundException("Can't find method $name in classpath")
}

fun JcMethod.toJavaConstructor(classLoader: ClassLoader): Constructor<*> {
    require(isConstructor) { "Can't convert not constructor to constructor" }
    val klass = Class.forName(enclosingClass.name, true, classLoader)
    return (klass.constructors + klass.declaredConstructors).find { it.jcdbSignature == this.jcdbSignature }
        ?: throw MethodNotFoundException("Can't find constructor of class ${enclosingClass.name}")
}

val Method.jcdbSignature: String
    get() {
        val parameterTypesAsString = parameterTypes.toJcdbFormat()
        return name + "(" + parameterTypesAsString + ")" + returnType.typeName + ";"
    }

val Constructor<*>.jcdbSignature: String
    get() {
        val methodName = "<init>"
        //Because of jcdb
        val returnType = "void;"
        val parameterTypesAsString = parameterTypes.toJcdbFormat()
        return "$methodName($parameterTypesAsString)$returnType"
    }

private fun Array<Class<*>>.toJcdbFormat(): String =
    if (isEmpty()) "" else joinToString(";", postfix = ";") { it.typeName }

fun Method.isSameSignatures(jcMethod: JcMethod) =
    jcdbSignature == jcMethod.jcdbSignature

fun Constructor<*>.isSameSignatures(jcMethod: JcMethod) =
    jcdbSignature == jcMethod.jcdbSignature

fun JcMethod.isSameSignature(mn: MethodNode): Boolean =
    withAsmNode { it.isSameSignature(mn) }

val JcMethod.toTypedMethod: JcTypedMethod
    get() = this.enclosingClass.toType().declaredMethods.first { typed -> typed.method == this }

val JcClassOrInterface.enumValuesField: JcTypedField
    get() = toType().findFieldOrNull("\$VALUES") ?: error("No \$VALUES field found for the enum type $this")

val JcClassType.name: String
    get() = if (this is JcClassTypeImpl) name else jcClass.name

val JcClassType.outerClassInstanceField: JcTypedField?
    get() = fields.singleOrNull { it.name == "this\$0" }

@Suppress("RecursivePropertyAccessor")
val JcClassType.allFields: List<JcTypedField>
    get() = declaredFields + (superType?.allFields ?: emptyList())

@Suppress("RecursivePropertyAccessor")
val JcClassOrInterface.allFields: List<JcField>
    get() = declaredFields + (superClass?.allFields ?: emptyList())

val JcClassType.allInstanceFields: List<JcTypedField>
    get() = allFields.filter { !it.isStatic }

val kotlin.reflect.KProperty<*>.javaName: String
    get() = this.javaField?.name ?: error("No java name for field $this")

val kotlin.reflect.KFunction<*>.javaName: String
    get() = this.javaMethod?.name ?: error("No java name for method $this")

class JcCpWithoutApproximations(val cp: JcClasspath) : JcClasspath by cp {
    init {
        check(cp !is JcCpWithoutApproximations)
    }

    override val features: List<JcClasspathFeature> by lazy {
        cp.featuresWithoutApproximations()
    }

    private fun JcClasspath.featuresWithoutApproximations(): List<JcClasspathFeature> {
        if (this !is JcClasspathImpl)
            error("unexpected JcClasspath: $this")

        val featuresChainField = this.javaClass.getDeclaredField("featuresChain")
        featuresChainField.isAccessible = true
        val featuresChain = featuresChainField.get(this) as JcFeaturesChain
        return featuresChain.features.filterNot { it is Approximations || it is ClasspathCache }
    }

    private class JcClassWithoutApproximations(
        private val cls: JcClassOrInterface, private val cp: JcCpWithoutApproximations
    ) : JcClassOrInterface by cls {
        override val classpath: JcClasspath get() = cp
        private val featuresChain by lazy {
            JcFeaturesChain(cp.features)
        }

        override val declaredFields: List<JcField> by lazy {
            if (cls !is JcClassOrInterfaceImpl)
                return@lazy cls.declaredFields

            val default = cls.info.fields.map { JcFieldImpl(this, it) }
            default.joinFeatureFields(this, featuresChain)
        }

        override val declaredMethods: List<JcMethod> by lazy {
            if (cls !is JcClassOrInterfaceImpl)
                return@lazy cls.declaredMethods

            val default = cls.info.methods.map { toJcMethod(it, featuresChain) }
            default.joinFeatureMethods(this, featuresChain)
        }
    }

    private val classWithoutApproximationsCache = hashMapOf<JcClassOrInterface, JcClassWithoutApproximations>()

    private val JcClassOrInterface.withoutApproximations: JcClassOrInterface get() {
        if (this is JcClassWithoutApproximations) return this

        check(classpath === cp)

        return classWithoutApproximationsCache.getOrPut(this) {
            JcClassWithoutApproximations(this, this@JcCpWithoutApproximations)
        }
    }

    private val JcField.withoutApproximations: JcField? get() {
        return this.enclosingClass.withoutApproximations.declaredFields.find {
            it.name == this.name && it.isStatic == this.isStatic
        }
    }

    val JcField.isOriginalField: Boolean get() = withoutApproximations != null
}

fun JcClasspath.cpWithoutApproximations(): JcCpWithoutApproximations {
    if (this is JcCpWithoutApproximations) return this
    return JcCpWithoutApproximations(this)
}
