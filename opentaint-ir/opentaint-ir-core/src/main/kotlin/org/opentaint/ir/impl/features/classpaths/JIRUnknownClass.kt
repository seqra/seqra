package org.opentaint.ir.impl.features.classpaths

import org.opentaint.ir.api.jvm.JIRClassOrInterface
import org.opentaint.ir.api.jvm.JIRClassType
import org.opentaint.ir.api.jvm.JIRClasspath
import org.opentaint.ir.api.jvm.JIRClasspathExtFeature
import org.opentaint.ir.api.jvm.JIRField
import org.opentaint.ir.api.jvm.JIRLookup
import org.opentaint.ir.api.jvm.JIRLookupExtFeature
import org.opentaint.ir.api.jvm.JIRMethod
import org.opentaint.ir.api.jvm.JIRTypedField
import org.opentaint.ir.api.jvm.JIRTypedMethod
import org.opentaint.ir.api.jvm.TypeName
import org.opentaint.ir.impl.features.classpaths.AbstractJIRResolvedResult.JIRResolvedClassResultImpl
import org.opentaint.ir.impl.features.classpaths.virtual.JIRVirtualClassImpl
import org.opentaint.ir.impl.features.classpaths.virtual.JIRVirtualFieldImpl
import org.opentaint.ir.impl.features.classpaths.virtual.JIRVirtualMethodImpl
import org.opentaint.ir.impl.features.classpaths.virtual.JIRVirtualParameter
import org.opentaint.ir.impl.types.JIRTypedFieldImpl
import org.opentaint.ir.impl.types.JIRTypedMethodImpl
import org.opentaint.ir.impl.types.TypeNameImpl
import org.opentaint.ir.impl.types.substition.JIRSubstitutorImpl
import org.objectweb.asm.Type
import org.opentaint.ir.api.jvm.ext.JAVA_OBJECT

class JIRUnknownClass(override var classpath: JIRClasspath, name: String) : JIRVirtualClassImpl(
    name,
    initialFields = emptyList(),
    initialMethods = emptyList()
) {
    override val lookup: JIRLookup<JIRField, JIRMethod> = JIRUnknownClassLookup(this)

    override fun equals(other: Any?): Boolean {
        if (this === other) {
            return true
        }
        return other is JIRUnknownClass && other.name == name
    }

    override fun hashCode(): Int = name.hashCode()

    override val superClass: JIRClassOrInterface?
        get() = if (name == JAVA_OBJECT) null else super.superClass
}

class JIRUnknownMethod(
    enclosingClass: JIRClassOrInterface,
    name: String,
    access: Int,
    description: String,
    returnType: TypeName,
    params: List<TypeName>
) : JIRVirtualMethodImpl(
    name,
    access,
    returnType = returnType,
    parameters = params.mapIndexed { index, typeName -> JIRVirtualParameter(index, typeName) },
    description = description
) {

    companion object {

        fun method(type: JIRClassOrInterface, name: String, access: Int, description: String): JIRMethod {
            val methodType = Type.getMethodType(description)
            val returnType = TypeNameImpl.fromTypeName(methodType.returnType.className)
            val paramsType = methodType.argumentTypes.map { TypeNameImpl.fromTypeName(it.className) }
            return JIRUnknownMethod(type, name, access, description, returnType, paramsType)
        }

        fun typedMethod(type: JIRClassType, name: String, access: Int, description: String): JIRTypedMethod {
            return JIRTypedMethodImpl(
                type,
                method(type.jIRClass, name, access, description),
                JIRSubstitutorImpl.empty
            )
        }
    }

    init {
        bind(enclosingClass)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) {
            return true
        }
        return other is JIRUnknownMethod && description == other.description
    }

    override fun hashCode(): Int = description.hashCode()
}

class JIRUnknownField(enclosingClass: JIRClassOrInterface, name: String, access: Int, type: TypeName) :
    JIRVirtualFieldImpl(name, access, type = type) {

    companion object {

        fun typedField(type: JIRClassType, name: String, access: Int, fieldType: TypeName): JIRTypedField {
            return JIRTypedFieldImpl(
                type,
                JIRUnknownField(type.jIRClass, name, access, fieldType),
                JIRSubstitutorImpl.empty
            )
        }

    }

    init {
        bind(enclosingClass)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) {
            return true
        }
        return other is JIRUnknownField && enclosingClass == other.enclosingClass && name == other.name
    }

    override fun hashCode(): Int = enclosingClass.hashCode() * 31 + name.hashCode()

}

/**
 * Feature for mocking references to unknown classes. I.e let's assume that we have:
 *
 * ```
 * class Bar {
 *
 *      int x = 0;
 *
 *      public void run() {
 *          System.out.println("Hello world");
 *      }
 * }
 *
 * class Foo extends Bar {
 *
 *      Bar f = new Bar();
 *
 *      public void call() {
 *          System.out.println(f.x);
 *          run();
 *      }
 * }
 * ```
 *
 * Let's assume that we have classpath that contains class `Foo` and doesn't contain `Bar`. Default behavior for
 * classpath is to fail on trying to access class that doesn't exist. i.e parsing method instructions will fail, reading
 * class hierarchy will fail, resolving method will fail.
 *
 * UnknownClasses feature fix this behaviour. All references pointing to nowhere will be resolved as special implementation
 * of [JIRClassOrInterface] instance. Such instance will have **empty** [JIRClassOrInterface.declaredFields] and
 * [JIRClassOrInterface.declaredMethods] but all resolutions done through [JIRClassOrInterface.lookup] interface will return
 * mocked instances
 *
 */
object UnknownClasses : JIRClasspathExtFeature {

    private val location = VirtualLocation()

    override fun tryFindClass(classpath: JIRClasspath, name: String): JIRClasspathExtFeature.JIRResolvedClassResult {
        return JIRResolvedClassResultImpl(name, JIRUnknownClass(classpath, name).also {
            it.bind(classpath, location)
        })
    }

    override fun tryFindType(
        classpath: JIRClasspath,
        name: String,
        nullable: Boolean?
    ): JIRClasspathExtFeature.JIRResolvedTypeResult {
        return AbstractJIRResolvedResult.JIRResolvedTypeResultImpl(
            name,
            JIRUnknownType(classpath, name, location, nullable ?: true)
        )
    }
}

/**
 * Used for mocking of methods and fields refs that doesn't exist in code base of classpath
 * ```
 * class Bar {
 *
 *      int x = 0;
 *
 *      public void run() {
 *          System.out.println("Hello world");
 *      }
 * }
 *
 * class Foo extends Bar {
 *
 *      Bar f = new Bar();
 *
 *      public void call() {
 *          System.out.println(f.y);
 *          f.runSomething();
 *      }
 * }
 * ```
 *
 * 3-address representation of bytecode for Foo class can't resolve `Bar#y` field and `Bar#runSomething`
 * method by default. With this feature such methods and fields will be resolved as JIRUnknownField and JIRUnknownMethod
 */
object UnknownClassMethodsAndFields : JIRLookupExtFeature {

    override fun lookup(clazz: JIRClassOrInterface): JIRLookup<JIRField, JIRMethod> {
        if (clazz !is JIRUnknownClass) {
            return TrivialLookup
        }
        return JIRUnknownClassLookup(clazz)
    }

    override fun lookup(type: JIRClassType): JIRLookup<JIRTypedField, JIRTypedMethod> {
        return JIRUnknownTypeLookup(type)
    }
}

val JIRClasspath.isResolveAllToUnknown: Boolean get() = isInstalled(UnknownClasses)

private object TrivialLookup : JIRLookup<JIRField, JIRMethod> {

    override fun field(name: String, typeName: TypeName?, fieldKind: JIRLookup.FieldKind): JIRField? = null

    override fun method(name: String, description: String): JIRMethod? = null

    override fun staticMethod(name: String, description: String): JIRMethod? = null

    override fun specialMethod(name: String, description: String): JIRMethod? = null
}

