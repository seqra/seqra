package org.opentaint.ir.taint.configuration

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

fun interface PositionResolver<out R> {
    fun resolve(position: Position): R
}

@Serializable
sealed interface Position

@Serializable
@SerialName("AnyArgument")
object AnyArgument : Position {
    override fun toString(): String = javaClass.simpleName
}

@Serializable
@SerialName("Argument")
data class Argument(@SerialName("number") val index: Int) : Position

@Serializable
@SerialName("This")
object This : Position {
    override fun toString(): String = javaClass.simpleName
}

@Serializable
@SerialName("Result")
object Result : Position {
    override fun toString(): String = javaClass.simpleName
}

@Serializable
@SerialName("ResultAnyElement")
object ResultAnyElement : Position {
    override fun toString(): String = javaClass.simpleName
}
