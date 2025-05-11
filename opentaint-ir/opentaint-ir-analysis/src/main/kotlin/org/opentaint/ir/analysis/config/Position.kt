package org.opentaint.ir.analysis.config

import org.opentaint.ir.analysis.paths.AccessPath
import org.opentaint.ir.analysis.paths.ElementAccessor
import org.opentaint.ir.analysis.paths.toPathOrNull
import org.opentaint.ir.api.cfg.JIRAssignInst
import org.opentaint.ir.api.cfg.JIRInst
import org.opentaint.ir.api.cfg.JIRInstanceCallExpr
import org.opentaint.ir.api.cfg.JIRValue
import org.opentaint.ir.api.ext.cfg.callExpr
import org.opentaint.ir.taint.configuration.AnyArgument
import org.opentaint.ir.taint.configuration.Argument
import org.opentaint.ir.taint.configuration.Position
import org.opentaint.ir.taint.configuration.PositionResolver
import org.opentaint.ir.taint.configuration.Result
import org.opentaint.ir.taint.configuration.ResultAnyElement
import org.opentaint.ir.taint.configuration.This

class CallPositionToAccessPathResolver(
    private val callStatement: JIRInst,
) : PositionResolver<AccessPath> {
    private val callExpr = callStatement.callExpr
        ?: error("Call statement should have non-null callExpr")

    override fun resolve(position: Position): AccessPath = when (position) {
        AnyArgument -> error("Unexpected $position")

        is Argument -> callExpr.args[position.index].toPathOrNull()
            ?: error("Cannot resolve $position for $callStatement")

        This -> (callExpr as? JIRInstanceCallExpr)?.instance?.toPathOrNull()
            ?: error("Cannot resolve $position for $callStatement")

        Result -> if (callStatement is JIRAssignInst) {
            callStatement.lhv.toPathOrNull()
        } else {
            callExpr.toPathOrNull()
        } ?: error("Cannot resolve $position for $callStatement")

        ResultAnyElement -> {
            val path = if (callStatement is JIRAssignInst) {
                callStatement.lhv.toPathOrNull()
            } else {
                callExpr.toPathOrNull()
            } ?: error("Cannot resolve $position for $callStatement")
            path / ElementAccessor(null)
        }
    }
}

class CallPositionToJIRValueResolver(
    private val callStatement: JIRInst,
) : PositionResolver<JIRValue> {
    private val callExpr = callStatement.callExpr
        ?: error("Call statement should have non-null callExpr")

    override fun resolve(position: Position): JIRValue = when (position) {
        AnyArgument -> error("Unexpected $position")

        is Argument -> callExpr.args[position.index]

        This -> (callExpr as? JIRInstanceCallExpr)?.instance
            ?: error("Cannot resolve $position for $callStatement")

        Result -> if (callStatement is JIRAssignInst) {
            callStatement.lhv
        } else {
            error("Cannot resolve $position for $callStatement")
        }

        ResultAnyElement -> error("Cannot resolve $position for $callStatement")
    }
}
