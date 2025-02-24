package org.opentaint.ir.impl.bytecode

import org.opentaint.ir.api.*
import org.opentaint.ir.api.ext.packageName

class JIRClassLookupImpl(val clazz: JIRClassOrInterface) : JIRLookup<JIRField, JIRMethod> {

    override fun field(name: String, typeName: TypeName?): JIRField? {
        return JIRClassLookup.JIRFieldLookup(clazz, name).lookup()
    }

    override fun method(name: String, description: String): JIRMethod? {
        return JIRClassLookup.JIRMethodLookup(clazz, name, description).lookup()
    }

    override fun staticMethod(name: String, description: String): JIRMethod? {
        return JIRClassLookup.JIRStaticMethodLookup(clazz, name, description).lookup()
    }

    override fun specialMethod(name: String, description: String): JIRMethod? {
        return JIRClassLookup.JIRSpecialMethodLookup(clazz, name, description).lookup()
    }

}
internal abstract class JIRClassLookup<Result : JIRAccessible>(clazz: JIRClassOrInterface) :
    JIRAbstractLookup<JIRClassOrInterface, Result>(clazz) {

    override val JIRClassOrInterface.resolvePackage: String
        get() = packageName

    internal open class JIRMethodLookup(
        clazz: JIRClassOrInterface,
        protected val name: String,
        protected val description: String,
    ) : JIRClassLookup<JIRMethod>(clazz), PolymorphicSignatureSupport {

        override val JIRClassOrInterface.elements: List<JIRMethod>
            get() = entry.declaredMethods

        override fun JIRClassOrInterface.next() = listOfNotNull(entry.superClass) + entry.interfaces

        override val predicate: (JIRMethod) -> Boolean
            get() = { it.name == name && it.description == description }

        override fun lookupElement(en: JIRClassOrInterface): JIRMethod? {
            return super.lookupElement(en) ?: en.declaredMethods.find(en.name, description)
        }

    }

    internal class JIRStaticMethodLookup(
        clazz: JIRClassOrInterface,
        name: String,
        description: String,
    ) : JIRMethodLookup(clazz, name, description) {

        override fun JIRClassOrInterface.next() = listOfNotNull(entry.superClass)

        override val predicate: (JIRMethod) -> Boolean
            get() = { it.name == name && it.isStatic && it.description == description }

    }

    internal class JIRSpecialMethodLookup(
        clazz: JIRClassOrInterface,
        name: String,
        description: String,
    ) : JIRMethodLookup(clazz, name, description) {

        override fun JIRClassOrInterface.next() = emptyList<JIRClassOrInterface>()

        override val predicate: (JIRMethod) -> Boolean
            get() = { it.name == name && it.isStatic && it.description == description }

    }

    internal class JIRFieldLookup(clazz: JIRClassOrInterface, private val name: String) : JIRClassLookup<JIRField>(clazz) {

        override val JIRClassOrInterface.elements: List<JIRField>
            get() = entry.declaredFields

        override fun JIRClassOrInterface.next() = listOfNotNull(superClass) + interfaces

        override val predicate: (JIRField) -> Boolean
            get() = { it.name == name }

    }

}
