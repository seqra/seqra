package org.opentaint.ir.go.impl

import org.opentaint.ir.go.api.*
import org.opentaint.ir.go.type.GoIRType
import org.opentaint.ir.go.value.GoIRConstantValue

class GoIRGlobalImpl(
    override val name: String,
    override val fullName: String,
    override val type: GoIRType,
    pkg: GoIRPackage,
    override val isExported: Boolean,
    override val position: GoIRPosition?,
) : GoIRGlobal {
    private var owner = pkg
    override val pkg: GoIRPackage get() = owner

    internal fun rebindPackage(pkg: GoIRPackage) {
        owner = pkg
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is GoIRGlobal) return false
        return fullName == other.fullName
    }

    override fun hashCode(): Int = fullName.hashCode()
}

class GoIRConstImpl(
    override val name: String,
    override val fullName: String,
    override val type: GoIRType,
    override val value: GoIRConstantValue,
    pkg: GoIRPackage,
    override val isExported: Boolean,
    override val position: GoIRPosition?,
) : GoIRConst {
    private var owner = pkg
    override val pkg: GoIRPackage get() = owner

    internal fun rebindPackage(pkg: GoIRPackage) {
        owner = pkg
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is GoIRConst) return false
        return fullName == other.fullName
    }

    override fun hashCode(): Int = fullName.hashCode()
}
