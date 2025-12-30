package org.opentaint.dataflow.ifds

interface UnitType

data object SingletonUnit : UnitType {
    override fun toString(): String = javaClass.simpleName
}

data object UnknownUnit : UnitType {
    override fun toString(): String = javaClass.simpleName
}

/**
 * Sets a mapping from a [Method] to abstract domain [UnitType].
 *
 * Therefore, it splits all methods into units, containing one or more method each
 * (unit is a set of methods with same value of [UnitType] returned by [resolve]).
 *
 * To get more info about how it is used in analysis, see [runAnalysis].
 */
fun interface UnitResolver<Method> {
    fun resolve(method: Method): UnitType
}
