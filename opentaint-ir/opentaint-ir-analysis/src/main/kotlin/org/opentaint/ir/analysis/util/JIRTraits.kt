package org.opentaint.ir.analysis.util

import org.opentaint.ir.analysis.ifds.AccessPath
import org.opentaint.ir.analysis.ifds.ElementAccessor
import org.opentaint.ir.analysis.ifds.FieldAccessor
import org.opentaint.ir.analysis.util.toPathOrNull
import org.opentaint.ir.api.common.cfg.CommonExpr
import org.opentaint.ir.api.common.cfg.CommonValue
import org.opentaint.ir.api.jvm.JIRMethod
import org.opentaint.ir.api.jvm.cfg.JIRArrayAccess
import org.opentaint.ir.api.jvm.cfg.JIRCastExpr
import org.opentaint.ir.api.jvm.cfg.JIRExpr
import org.opentaint.ir.api.jvm.cfg.JIRFieldRef
import org.opentaint.ir.api.jvm.cfg.JIRInst
import org.opentaint.ir.api.jvm.cfg.JIRSimpleValue
import org.opentaint.ir.api.jvm.cfg.JIRThis
import org.opentaint.ir.api.jvm.cfg.JIRValue
import org.opentaint.ir.api.jvm.ext.toType
import org.opentaint.ir.analysis.util.thisInstance as _thisInstance
import org.opentaint.ir.analysis.util.toPath as _toPath
import org.opentaint.ir.analysis.util.toPathOrNull as _toPathOrNull

object JIRTraits : Traits<JIRMethod, JIRInst> {

    override val JIRMethod.thisInstance: JIRThis
        get() = _thisInstance

    @Suppress("EXTENSION_SHADOWED_BY_MEMBER")
    override val JIRMethod.isConstructor: Boolean
        get() = isConstructor

    override fun CommonExpr.toPathOrNull(): AccessPath? {
        check(this is JIRExpr)
        return _toPathOrNull()
    }

    override fun CommonValue.toPathOrNull(): AccessPath? {
        check(this is JIRValue)
        return _toPathOrNull()
    }

    override fun CommonValue.toPath(): AccessPath {
        check(this is JIRValue)
        return _toPath()
    }
}

val JIRMethod.thisInstance: JIRThis
    get() = JIRThis(enclosingClass.toType())

fun JIRExpr.toPathOrNull(): AccessPath? = when (this) {
    is JIRValue -> toPathOrNull()
    is JIRCastExpr -> operand.toPathOrNull()
    else -> null
}

fun JIRValue.toPathOrNull(): AccessPath? = when (this) {
    is JIRSimpleValue -> AccessPath(this, emptyList())

    is JIRArrayAccess -> {
        array.toPathOrNull()?.let {
            it + ElementAccessor
        }
    }

    is JIRFieldRef -> {
        val instance = instance
        if (instance == null) {
            require(field.isStatic) { "Expected static field" }
            AccessPath(null, listOf(FieldAccessor(field.field)))
        } else {
            instance.toPathOrNull()?.let {
                it + FieldAccessor(field.field)
            }
        }
    }

    else -> null
}

fun JIRValue.toPath(): AccessPath {
    return toPathOrNull() ?: error("Unable to build access path for value $this")
}
