package org.opentaint.dataflow.ap.ifds

import org.opentaint.ir.api.common.CommonMethod
import org.opentaint.ir.api.common.cfg.CommonCallExpr
import org.opentaint.ir.api.common.cfg.CommonInst
import org.opentaint.ir.api.common.cfg.CommonValue
import org.opentaint.dataflow.ap.ifds.access.FactAp
import org.opentaint.dataflow.ap.ifds.access.FinalFactAp
import org.opentaint.dataflow.ap.ifds.access.InitialFactAp

interface MethodCallFactMapper {
    fun mapMethodExitToReturnFlowFact(
        callStatement: CommonInst,
        methodExit: CommonInst,
        factAp: FinalFactAp,
        checker: FactTypeChecker
    ): FinalFactAp?

    fun mapMethodExitToReturnFlowFact(
        callStatement: CommonInst,
        methodExit: CommonInst,
        factAp: InitialFactAp
    ): InitialFactAp?

    fun mapMethodCallToStartFlowFact(
        callee: CommonMethod,
        callExpr: CommonCallExpr,
        factAp: FinalFactAp,
        checker: FactTypeChecker,
        onMappedFact: (FinalFactAp, AccessPathBase) -> Unit
    )

    fun mapMethodCallToStartFlowFact(
        callee: CommonMethod,
        callExpr: CommonCallExpr,
        fact: InitialFactAp,
        onMappedFact: (InitialFactAp, AccessPathBase) -> Unit
    )

    fun factCanBeModifiedByMethodCall(returnValue: CommonValue?, callExpr: CommonCallExpr, factAp: FactAp): Boolean
    fun isValidMethodExitFact(methodExit: CommonInst, factAp: FactAp): Boolean
    fun methodExitFactBases(methodExits: List<CommonInst>): List<AccessPathBase>
}