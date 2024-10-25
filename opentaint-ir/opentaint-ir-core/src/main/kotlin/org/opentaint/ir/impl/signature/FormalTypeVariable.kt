package org.opentaint.ir.impl.signature

interface FormalTypeVariable

internal class Formal(val symbol: String? = null, val boundTypeTokens: List<SType>? = null) : FormalTypeVariable