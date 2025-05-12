package org.opentaint.ir.taint.configuration

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass

interface TaintActionVisitor<out R> {
    fun visit(action: CopyAllMarks): R
    fun visit(action: CopyMark): R
    fun visit(action: AssignMark): R
    fun visit(action: RemoveAllMarks): R
    fun visit(action: RemoveMark): R

    fun visit(action: Action): R
}

interface Action {
    fun <R> accept(visitor: TaintActionVisitor<R>): R
}

val actionModule = SerializersModule {
    polymorphic(Action::class) {
        subclass(CopyAllMarks::class)
        subclass(CopyMark::class)
        subclass(AssignMark::class)
        subclass(RemoveAllMarks::class)
        subclass(RemoveMark::class)
    }
}

// TODO add marks for aliases (if you pass an object and return it from the function)

@Serializable
@SerialName("CopyAllMarks")
data class CopyAllMarks(
    val from: Position,
    val to: Position,
) : Action {
    override fun <R> accept(visitor: TaintActionVisitor<R>): R = visitor.visit(this)
}

@Serializable
@SerialName("CopyMark")
data class CopyMark(
    val mark: TaintMark,
    val from: Position,
    val to: Position,
) : Action {
    override fun <R> accept(visitor: TaintActionVisitor<R>): R = visitor.visit(this)
}

@Serializable
@SerialName("AssignMark")
data class AssignMark(
    val mark: TaintMark,
    val position: Position,
) : Action {
    override fun <R> accept(visitor: TaintActionVisitor<R>): R = visitor.visit(this)
}

@Serializable
@SerialName("RemoveAllMarks")
data class RemoveAllMarks(
    val position: Position,
) : Action {
    override fun <R> accept(visitor: TaintActionVisitor<R>): R = visitor.visit(this)
}

@Serializable
@SerialName("RemoveMark")
data class RemoveMark(
    val mark: TaintMark,
    val position: Position,
) : Action {
    override fun <R> accept(visitor: TaintActionVisitor<R>): R = visitor.visit(this)
}
