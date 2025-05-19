package org.opentaint.ir.analysis.util

import org.opentaint.ir.api.common.CommonMethod
import org.opentaint.ir.api.common.cfg.CommonInst
import org.opentaint.ir.api.common.cfg.CommonThis
import org.opentaint.ir.api.jvm.JIRMethod
import org.opentaint.ir.api.jvm.cfg.JIRInst
import org.opentaint.ir.api.jvm.cfg.JIRThis
import org.opentaint.ir.api.jvm.ext.toType

/**
 * Extensions for analysis.
 */
interface Traits<out Method, out Statement>
    where Method : CommonMethod<Method, Statement>,
          Statement : CommonInst<Method, Statement> {

    fun thisInstance(method: @UnsafeVariance Method): CommonThis
    fun isConstructor(method: @UnsafeVariance Method): Boolean
}

// JVM
object JIRTraits : Traits<JIRMethod, JIRInst> {
    override fun thisInstance(method: JIRMethod): JIRThis {
        return JIRThis(method.enclosingClass.toType())
    }

    override fun isConstructor(method: JIRMethod): Boolean {
        return method.isConstructor
    }
}

    }

        // TODO
        return false
    }

        TODO()
        // return project.classTypeOf(this)
    }
}
