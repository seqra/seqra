package org.opentaint.ir.api.common.cfg

import org.opentaint.ir.api.common.CommonMethod

interface CommonInst {
    val location: CommonInstLocation

    // TODO: replace with extension property
    val method: CommonMethod
        get() = location.method
}

interface CommonInstLocation {
    val method: CommonMethod
    // val index: Int
    // val lineNumber: Int
}

interface CommonAssignInst : CommonInst {
    val lhv: CommonValue
    val rhv: CommonExpr
}

// TODO: add 'callExpr: CoreExpr' property
interface CommonCallInst : CommonInst

interface CommonReturnInst : CommonInst {
    val returnValue: CommonValue?
}

interface CommonGotoInst : CommonInst

interface CommonIfInst : CommonInst
