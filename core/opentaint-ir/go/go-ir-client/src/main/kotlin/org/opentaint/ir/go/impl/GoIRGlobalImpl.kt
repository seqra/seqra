package org.opentaint.ir.go.impl

import org.opentaint.ir.go.api.*
import org.opentaint.ir.go.type.GoIRType
import org.opentaint.ir.go.value.GoIRConstantValue

class GoIRGlobalImpl(
    override val name: String,
    override val fullName: String,
    override val type: GoIRType,
    override val pkg: GoIRPackage,
    override val isExported: Boolean,
    override val position: GoIRPosition?,
) : GoIRGlobal

class GoIRConstImpl(
    override val name: String,
    override val fullName: String,
    override val type: GoIRType,
    override val value: GoIRConstantValue,
    override val pkg: GoIRPackage,
    override val isExported: Boolean,
    override val position: GoIRPosition?,
) : GoIRConst
