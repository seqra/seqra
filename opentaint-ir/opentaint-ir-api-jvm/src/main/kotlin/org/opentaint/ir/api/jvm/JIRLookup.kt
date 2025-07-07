package org.opentaint.ir.api.jvm

/**
 * lookup for fields and methods in [JIRClassOrInterface] and [JIRClassType].
 *
 * It's not necessary that looked up method will return instance preserved in [JIRClassOrInterface.declaredFields] or
 * [JIRClassOrInterface.declaredMethods] collections
 */
@JvmDefaultWithoutCompatibility
interface JIRLookup<Field : JIRAccessible, Method : JIRAccessible> {

    /**
     * lookup for field with specific name
     * @param name of field
     */
    fun field(name: String): Field? = field(name, typeName = null, fieldKind = FieldKind.ANY)

    /**
     * lookup for field with specific name and expected type. Used during instructions parsing. In this case field type is preserved
     * in Java bytecode
     *
     * @param name of field
     * @param typeName expected type of field
     */
    fun field(name: String, typeName: TypeName?, fieldKind: FieldKind): Field?

    /**
     * Lookup for method based on name and description:
     * - in current class search for private methods too
     * - in parent classes and interfaces search only for visible methods
     *
     * @param name method name
     * @param description jvm description of method
     */
    fun method(name: String, description: String): Method?

    /**
     * Lookup for static method based on name and description
     *
     * @param name method name
     * @param description jvm description of method
     */
    fun staticMethod(name: String, description: String): Method?

    /**
     * Lookup for methods placed in special instructions i.e `private` and `super` calls.
     *
     * @param name method name
     * @param description jvm description of method
     */
    fun specialMethod(name: String, description: String): Method?

    enum class FieldKind {
        INSTANCE, STATIC, ANY
    }
}
