package org.opentaint.ir.analysis.config

import org.opentaint.ir.analysis.ifds.CommonAccessPath
import org.opentaint.ir.analysis.ifds.ElementAccessor
import org.opentaint.ir.analysis.ifds.Maybe
import org.opentaint.ir.analysis.ifds.fmap
import org.opentaint.ir.analysis.ifds.toMaybe
import org.opentaint.ir.analysis.ifds.toPathOrNull
import org.opentaint.ir.analysis.util.getArgument
import org.opentaint.ir.analysis.util.thisInstance
import org.opentaint.ir.api.common.CommonMethod
import org.opentaint.ir.api.common.Project
import org.opentaint.ir.api.common.cfg.CommonAssignInst
import org.opentaint.ir.api.common.cfg.CommonInst
import org.opentaint.ir.api.common.cfg.CommonInstanceCallExpr
import org.opentaint.ir.api.common.cfg.CommonValue
import org.opentaint.ir.api.common.ext.callExpr
import org.opentaint.ir.taint.configuration.AnyArgument
import org.opentaint.ir.taint.configuration.Argument
import org.opentaint.ir.taint.configuration.Position
import org.opentaint.ir.taint.configuration.PositionResolver
import org.opentaint.ir.taint.configuration.Result
import org.opentaint.ir.taint.configuration.ResultAnyElement
import org.opentaint.ir.taint.configuration.This

class CallPositionToAccessPathResolver(
    private val callStatement: CommonInst<*, *>,
) : PositionResolver<Maybe<CommonAccessPath>> {
    private val callExpr = callStatement.callExpr
        ?: error("Call statement should have non-null callExpr")

    override fun resolve(position: Position): Maybe<CommonAccessPath> = when (position) {
        AnyArgument -> Maybe.none()
        is Argument -> callExpr.args[position.index].toPathOrNull().toMaybe()
        This -> (callExpr as? CommonInstanceCallExpr)?.instance?.toPathOrNull().toMaybe()
        Result -> (callStatement as? CommonAssignInst)?.lhv?.toPathOrNull().toMaybe()
        ResultAnyElement -> (callStatement as? CommonAssignInst)?.lhv?.toPathOrNull().toMaybe()
            .fmap { it + ElementAccessor }
    }
}

class CallPositionToValueResolver(
    private val callStatement: CommonInst<*, *>,
) : PositionResolver<Maybe<CommonValue>> {
    private val callExpr = callStatement.callExpr
        ?: error("Call statement should have non-null callExpr")

    override fun resolve(position: Position): Maybe<CommonValue> = when (position) {
        AnyArgument -> Maybe.none()
        is Argument -> Maybe.some(callExpr.args[position.index])
        This -> (callExpr as? CommonInstanceCallExpr)?.instance.toMaybe()
        Result -> (callStatement as? CommonAssignInst)?.lhv.toMaybe()
        ResultAnyElement -> Maybe.none()
    }
}

class EntryPointPositionToValueResolver(
    val cp: Project,
    val method: CommonMethod<*, *>,
) : PositionResolver<Maybe<CommonValue>> {
    override fun resolve(position: Position): Maybe<CommonValue> = when (position) {
        This -> Maybe.some(method.thisInstance)

        is Argument -> {
            val p = method.parameters[position.index]
            cp.getArgument(p).toMaybe()
        }

        AnyArgument, Result, ResultAnyElement -> error("Unexpected $position")
    }
}

class EntryPointPositionToAccessPathResolver(
    val cp: Project,
    val method: CommonMethod<*, *>,
) : PositionResolver<Maybe<CommonAccessPath>> {
    override fun resolve(position: Position): Maybe<CommonAccessPath> = when (position) {
        This -> method.thisInstance.toPathOrNull().toMaybe()

        is Argument -> {
            val p = method.parameters[position.index]
            cp.getArgument(p)?.toPathOrNull().toMaybe()
        }

        AnyArgument, Result, ResultAnyElement -> error("Unexpected $position")
    }
}
