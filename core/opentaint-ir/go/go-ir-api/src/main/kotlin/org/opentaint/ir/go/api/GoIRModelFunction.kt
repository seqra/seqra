package org.opentaint.ir.go.api

/** A function supplied by a model, including helpers with no original declaration. */
interface GoIRModelFunction : GoIRFunction

/** A model implementation of an existing program function. */
interface GoIRApproximatedFunction : GoIRModelFunction {
    val original: GoIRFunction
}
