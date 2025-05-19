package org.opentaint.ir.analysis.util

import org.opentaint.ir.analysis.ifds.AccessPath
import org.opentaint.ir.analysis.ifds.ElementAccessor
import org.opentaint.ir.analysis.ifds.FieldAccessor
import org.opentaint.ir.api.common.CommonMethod
import org.opentaint.ir.api.common.cfg.CommonExpr
import org.opentaint.ir.api.common.cfg.CommonInst
import org.opentaint.ir.api.common.cfg.CommonThis
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

/**
 * Extensions for analysis.
 */
interface Traits<out Method, out Statement>
    where Method : CommonMethod<Method, Statement>,
          Statement : CommonInst<Method, Statement> {

    val @UnsafeVariance Method.thisInstance: CommonThis
    val @UnsafeVariance Method.isConstructor: Boolean

    fun CommonExpr.toPathOrNull(): AccessPath?
    fun CommonValue.toPathOrNull(): AccessPath?
    fun CommonValue.toPath(): AccessPath

}

// JVM
object JIRTraits : Traits<JIRMethod, JIRInst> {

    override val JIRMethod.thisInstance: JIRThis
        get() = JIRThis(enclosingClass.toType())

    @Suppress("EXTENSION_SHADOWED_BY_MEMBER")
    override val JIRMethod.isConstructor: Boolean
        get() = isConstructor

    override fun CommonExpr.toPathOrNull(): AccessPath? {
        check(this is JIRExpr)
        return toPathOrNull()
    }

    fun JIRExpr.toPathOrNull(): AccessPath? = when (this) {
        is JIRValue -> toPathOrNull()
        is JIRCastExpr -> operand.toPathOrNull()
        else -> null
    }

    override fun CommonValue.toPathOrNull(): AccessPath? {
        check(this is JIRValue)
        return toPathOrNull()
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

    override fun CommonValue.toPath(): AccessPath {
        check(this is JIRValue)
        return toPath()
    }

    fun JIRValue.toPath(): AccessPath {
        return toPathOrNull() ?: error("Unable to build access path for value $this")
    }
}

        // TODO
        get() = false

        TODO()
        // return project.classTypeOf(this)
    }

    override fun CommonExpr.toPathOrNull(): AccessPath? {
        return toPathOrNull()
    }

        else -> null
    }

    override fun CommonValue.toPathOrNull(): AccessPath? {
        return toPathOrNull()
    }

        }

        }

        else -> null
    }

    override fun CommonValue.toPath(): AccessPath {
        return toPath()
    }

        return toPathOrNull() ?: error("Unable to build access path for value $this")
    }
}
