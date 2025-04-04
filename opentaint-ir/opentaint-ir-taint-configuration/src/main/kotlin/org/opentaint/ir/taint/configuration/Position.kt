package org.opentaint.ir.taint.configuration

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

interface PositionResolver<R> {
    fun resolve(position: Position): R
}

@Serializable
sealed interface Position

@Serializable
@SerialName("Argument")
data class Argument(val number: Int) : Position

@Serializable
@SerialName("AnyArgument")
object AnyArgument : Position

@Serializable
@SerialName("This")
object ThisArgument : Position

@Serializable
@SerialName("Result")
object Result : Position
