package org.opentaint.ir.api.common.cfg

import org.opentaint.ir.api.common.CommonMethod

interface CommonInst {
    val location: CommonInstLocation

    val method: CommonMethod
        get() = location.method
}

interface CommonInstLocation {
    val method: CommonMethod
}

interface CommonAssignInst : CommonInst {
    val lhv: CommonValue
    val rhv: CommonExpr
}

interface CommonCallInst : CommonInst

interface CommonReturnInst : CommonInst {
    val returnValue: CommonValue?
}

interface CommonGotoInst : CommonInst

interface CommonIfInst : CommonInst
