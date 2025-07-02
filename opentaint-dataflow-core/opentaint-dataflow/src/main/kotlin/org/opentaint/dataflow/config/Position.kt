/*
 *  Copyright 2022 Opentaint contributors (opentaint.dev)
 * <p>
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 * <p>
 *  http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package org.opentaint.dataflow.config

import org.opentaint.dataflow.ifds.AccessPath
import org.opentaint.dataflow.ifds.ElementAccessor
import org.opentaint.dataflow.ifds.Maybe
import org.opentaint.dataflow.ifds.fmap
import org.opentaint.dataflow.ifds.toMaybe
import org.opentaint.dataflow.util.Traits
import org.opentaint.ir.api.common.CommonMethod
import org.opentaint.ir.api.common.CommonProject
import org.opentaint.ir.api.common.cfg.CommonAssignInst
import org.opentaint.ir.api.common.cfg.CommonInst
import org.opentaint.ir.api.common.cfg.CommonInstanceCallExpr
import org.opentaint.ir.api.common.cfg.CommonValue
import org.opentaint.ir.taint.configuration.AnyArgument
import org.opentaint.ir.taint.configuration.Argument
import org.opentaint.ir.taint.configuration.Position
import org.opentaint.ir.taint.configuration.PositionResolver
import org.opentaint.ir.taint.configuration.Result
import org.opentaint.ir.taint.configuration.ResultAnyElement
import org.opentaint.ir.taint.configuration.This

context(Traits<CommonMethod, CommonInst>)
class CallPositionToAccessPathResolver(
    private val callStatement: CommonInst,
) : PositionResolver<Maybe<AccessPath>> {
    private val callExpr = callStatement.getCallExpr()
        ?: error("Call statement should have non-null callExpr")

    override fun resolve(position: Position): Maybe<AccessPath> = when (position) {
        AnyArgument -> Maybe.none()
        is Argument -> callExpr.args[position.index].toPathOrNull().toMaybe()
        This -> (callExpr as? CommonInstanceCallExpr)?.instance?.toPathOrNull().toMaybe()
        Result -> (callStatement as? CommonAssignInst)?.lhv?.toPathOrNull().toMaybe()
        ResultAnyElement -> (callStatement as? CommonAssignInst)?.lhv?.toPathOrNull().toMaybe()
            .fmap { it + ElementAccessor }
    }
}

context(Traits<CommonMethod, CommonInst>)
class CallPositionToValueResolver(
    private val callStatement: CommonInst,
) : PositionResolver<Maybe<CommonValue>> {
    private val callExpr = callStatement.getCallExpr()
        ?: error("Call statement should have non-null callExpr")

    override fun resolve(position: Position): Maybe<CommonValue> = when (position) {
        AnyArgument -> Maybe.none()
        is Argument -> Maybe.some(callExpr.args[position.index])
        This -> (callExpr as? CommonInstanceCallExpr)?.instance.toMaybe()
        Result -> (callStatement as? CommonAssignInst)?.lhv.toMaybe()
        ResultAnyElement -> Maybe.none()
    }
}

context(Traits<CommonMethod, CommonInst>)
class EntryPointPositionToValueResolver(
    private val method: CommonMethod,
    private val project: CommonProject,
) : PositionResolver<Maybe<CommonValue>> {
    override fun resolve(position: Position): Maybe<CommonValue> = when (position) {
        This -> Maybe.some(method.thisInstance)

        is Argument -> {
            val p = method.parameters[position.index]
            project.getArgument(p).toMaybe()
        }

        AnyArgument, Result, ResultAnyElement -> error("Unexpected $position")
    }
}

context(Traits<CommonMethod, CommonInst>)
class EntryPointPositionToAccessPathResolver(
    private val method: CommonMethod,
    private val project: CommonProject,
) : PositionResolver<Maybe<AccessPath>> {
    override fun resolve(position: Position): Maybe<AccessPath> = when (position) {
        This -> method.thisInstance.toPathOrNull().toMaybe()

        is Argument -> {
            val p = method.parameters[position.index]
            project.getArgument(p)?.toPathOrNull().toMaybe()
        }

        AnyArgument, Result, ResultAnyElement -> error("Unexpected $position")
    }
}
