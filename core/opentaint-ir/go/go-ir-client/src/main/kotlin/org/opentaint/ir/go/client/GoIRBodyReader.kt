package org.opentaint.ir.go.client

import org.opentaint.ir.go.api.*
import org.opentaint.ir.go.type.*

/** Decodes a body directly in its owner’s lookup context, without an intermediate IR copy. */
interface GoIRBodyReader {
    fun readBody(owner: GoIRFunction, context: GoIRBodyContext): GoIRBody?
}

/** Composable resolution policy supplied by the program view, independent of wire decoding. */
open class GoIRBodyContext {
    open fun type(type: GoIRType): GoIRType = type
    open fun function(reference: GoIrFunctionReference): GoIrFunctionReference = reference
    open fun global(reference: GoIRGlobalReference): GoIRGlobalReference = reference
    open fun fieldIndex(type: GoIRType, index: Int, name: String): Int = index
}
