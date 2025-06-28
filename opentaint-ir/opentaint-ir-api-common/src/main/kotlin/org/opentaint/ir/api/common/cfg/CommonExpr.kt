package org.opentaint.ir.api.common.cfg

interface CommonExpr {
    val typeName: String
}

interface CommonCallExpr : CommonExpr {
    val args: List<CommonValue>
}

interface CommonInstanceCallExpr : CommonCallExpr {
    val instance: CommonValue
}
