package org.opentaint.ir.configuration

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

interface TaintActionVisitor<R> {
    fun visit(action: CopyAllMarks): R
    fun visit(action: CopyMark): R
    fun visit(action: AssignMark): R
    fun visit(action: RemoveAllMarks): R
    fun visit(action: RemoveMark): R
}

interface Action {
    fun <R> accept(visitor: TaintActionVisitor<R>): R
}

// TODO add marks for aliases (if you pass an object and return it from the function)
@Serializable
@SerialName("CopyAllMarks")
data class CopyAllMarks(val from: Position, val to: Position) : Action {
    override fun <R> accept(visitor: TaintActionVisitor<R>): R = visitor.visit(this)
}

@Serializable
@SerialName("AssignMark")
data class AssignMark(val position: Position, val mark: TaintMark) : Action {
    override fun <R> accept(visitor: TaintActionVisitor<R>): R = visitor.visit(this)
}

@Serializable
@SerialName("RemoveAllMarks")
data class RemoveAllMarks(val position: Position) : Action {
    override fun <R> accept(visitor: TaintActionVisitor<R>): R = visitor.visit(this)
}

@Serializable
@SerialName("RemoveMark")
data class RemoveMark(val position: Position, val mark: TaintMark) : Action {
    override fun <R> accept(visitor: TaintActionVisitor<R>): R = visitor.visit(this)
}

@Serializable
@SerialName("CopyMark")
data class CopyMark(val from: Position, val to: Position, val mark: TaintMark) : Action {
    override fun <R> accept(visitor: TaintActionVisitor<R>): R = visitor.visit(this)
}
