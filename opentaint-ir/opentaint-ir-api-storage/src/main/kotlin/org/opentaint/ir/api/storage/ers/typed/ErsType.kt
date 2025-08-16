package org.opentaint.ir.api.jvm.storage.ers.typed

import kotlin.reflect.KClass
import kotlin.reflect.KProperty

/**
 * Base class for user-defined ERS entity types.
 *
 * Usage example:
 * ```
 * object PersonType : ErsType {
 *     val name by property(String::class) // searchable by default
 *     val age by property(Int::class, searchability = ErsSearchability.NonSearchable)
 *     val friend by link(PersonType)
 * }
 * ```
 */
interface ErsType {
    val typeName: String get() = javaClass.name.replace(".", "_")
}

fun <ENTITY_TYPE : ErsType, VALUE : Any> ENTITY_TYPE.property(valueClass: KClass<VALUE>)
    = property(valueClass, ErsSearchability.Searchable)

fun <ENTITY_TYPE : ErsType, VALUE : Any, SEARCH : ErsSearchability> ENTITY_TYPE.property(
    valueClass: KClass<VALUE>,
    searchability: SEARCH
) = NameDependentDelegate { name ->
    ErsProperty(name, this, valueClass.java, searchability)
}

fun <SOURCE_TYPE : ErsType, TARGET_TYPE : ErsType> SOURCE_TYPE.link(targetType: TARGET_TYPE) =
    NameDependentDelegate { name ->
        ErsLink(name, this, targetType)
    }

data class ErsProperty<ENTITY_TYPE : ErsType, VALUE : Any, SEARCH : ErsSearchability> internal constructor(
    val name: String,
    val ownerType: ENTITY_TYPE,
    val valueClass: Class<VALUE>,
    val searchability: SEARCH
)

sealed interface ErsSearchability {
    object Searchable : ErsSearchability
    object NonSearchable : ErsSearchability
}

data class ErsLink<SOURCE_TYPE : ErsType, TARGET_TYPE : ErsType> internal constructor(
    val name: String,
    val sourceType: SOURCE_TYPE,
    val targetType: TARGET_TYPE
)

class NameDependentDelegate<T>(private val initValue: (name: String) -> T) {
    operator fun getValue(thisRef: Any?, property: KProperty<*>): T {
        return initValue(property.name)
    }
}
