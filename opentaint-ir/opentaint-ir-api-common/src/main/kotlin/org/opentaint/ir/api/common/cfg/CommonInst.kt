package org.opentaint.ir.api.common.cfg

import org.opentaint.ir.api.common.CommonMethod

interface CommonInst {
    val location: CommonInstLocation
    // val operands: List<CommonExpr>

    // TODO: replace with extension property
    val method: CommonMethod
        get() = location.method

    interface Visitor<out T> {
        fun visitExternalCommonInst(inst: CommonInst): T

        fun visitCommonAssignInst(inst: CommonAssignInst): T
        fun visitCommonCallInst(inst: CommonCallInst): T
        fun visitCommonReturnInst(inst: CommonReturnInst): T
        fun visitCommonGotoInst(inst: CommonGotoInst): T
        fun visitCommonIfInst(inst: CommonIfInst): T

        interface Default<out T> : Visitor<T> {
            fun defaultVisitCommonInst(inst: CommonInst): T

            override fun visitExternalCommonInst(inst: CommonInst): T = defaultVisitCommonInst(inst)

            override fun visitCommonAssignInst(inst: CommonAssignInst): T = defaultVisitCommonInst(inst)
            override fun visitCommonCallInst(inst: CommonCallInst): T = defaultVisitCommonInst(inst)
            override fun visitCommonReturnInst(inst: CommonReturnInst): T = defaultVisitCommonInst(inst)
            override fun visitCommonGotoInst(inst: CommonGotoInst): T = defaultVisitCommonInst(inst)
            override fun visitCommonIfInst(inst: CommonIfInst): T = defaultVisitCommonInst(inst)
        }
    }

    fun <T> accept(visitor: Visitor<T>): T = acceptCommonInst(visitor)
    fun <T> acceptCommonInst(visitor: Visitor<T>): T {
        return visitor.visitExternalCommonInst(this)
    }
}

interface CommonInstLocation {
    val method: CommonMethod
    // val index: Int
    // val lineNumber: Int
}

interface CommonAssignInst : CommonInst {
    val lhv: CommonValue
    val rhv: CommonExpr

    override fun <T> acceptCommonInst(visitor: CommonInst.Visitor<T>): T {
        return visitor.visitCommonAssignInst(this)
    }
}

// TODO: add 'callExpr: CoreExpr' property
interface CommonCallInst : CommonInst {
    override fun <T> acceptCommonInst(visitor: CommonInst.Visitor<T>): T {
        return visitor.visitCommonCallInst(this)
    }
}

interface CommonReturnInst : CommonInst {
    val returnValue: CommonValue?

    override fun <T> acceptCommonInst(visitor: CommonInst.Visitor<T>): T {
        return visitor.visitCommonReturnInst(this)
    }
}

interface CommonGotoInst : CommonInst {
    override fun <T> acceptCommonInst(visitor: CommonInst.Visitor<T>): T {
        return visitor.visitCommonGotoInst(this)
    }
}

interface CommonIfInst : CommonInst {
    override fun <T> acceptCommonInst(visitor: CommonInst.Visitor<T>): T {
        return visitor.visitCommonIfInst(this)
    }
}
