package org.opentaint.ir.analysis.util

import org.opentaint.ir.analysis.ifds.AccessPath
import org.opentaint.ir.analysis.ifds.toPath
import org.opentaint.ir.analysis.ifds.toPathOrNull
import org.opentaint.ir.api.common.CommonMethod
import org.opentaint.ir.api.common.cfg.CommonExpr
import org.opentaint.ir.api.common.cfg.CommonInst
import org.opentaint.ir.api.common.cfg.CommonThis
import org.opentaint.ir.api.common.cfg.CommonValue
import org.opentaint.ir.api.jvm.JIRMethod
import org.opentaint.ir.api.jvm.cfg.JIRExpr
import org.opentaint.ir.api.jvm.cfg.JIRInst
import org.opentaint.ir.api.jvm.cfg.JIRThis
import org.opentaint.ir.api.jvm.cfg.JIRValue
import org.opentaint.ir.api.jvm.ext.toType

/**
 * Extensions for analysis.
 */
abstract class Traits<out Method, out Statement>
    where Method : CommonMethod<Method, Statement>,
          Statement : CommonInst<Method, Statement> {

    abstract fun thisInstance(method: @UnsafeVariance Method): CommonThis
    abstract fun isConstructor(method: @UnsafeVariance Method): Boolean

    abstract fun toPathOrNull(expr: CommonExpr): AccessPath?
    abstract fun toPath(value: CommonValue): AccessPath

    val scope: Scope = Scope()

    inner class Scope {
        val @UnsafeVariance Method.thisInstance: CommonThis
            get() = thisInstance(this)

        val @UnsafeVariance Method.isConstructor: Boolean
            get() = isConstructor(this)

        fun CommonExpr.toPathOrNull(): AccessPath? = toPathOrNull(this)

        fun CommonValue.toPath(): AccessPath = toPath(this)
    }
}

// JVM
object JIRTraits : Traits<JIRMethod, JIRInst>() {
    override fun thisInstance(method: JIRMethod): JIRThis {
        return JIRThis(method.enclosingClass.toType())
    }

    override fun isConstructor(method: JIRMethod): Boolean {
        return method.isConstructor
    }

    override fun toPathOrNull(expr: CommonExpr): AccessPath? {
        check(expr is JIRExpr)
        return expr.toPathOrNull()
    }

    override fun toPath(value: CommonValue): AccessPath {
        check(value is JIRValue)
        return value.toPath()
    }
}

    }

        // TODO
        return false
    }

        TODO()
        // return project.classTypeOf(this)
    }

    override fun toPathOrNull(expr: CommonExpr): AccessPath? {
        return expr.toPathOrNull()
    }

    override fun toPath(value: CommonValue): AccessPath {
        return value.toPath()
    }
}
