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

import org.opentaint.ir.api.common.CommonMethod
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
import org.opentaint.dataflow.ifds.AccessPath
import org.opentaint.dataflow.ifds.ElementAccessor
import org.opentaint.dataflow.ifds.Maybe
import org.opentaint.dataflow.ifds.fmap
import org.opentaint.dataflow.ifds.toMaybe
import org.opentaint.dataflow.util.Traits

class CallPositionToAccessPathResolver(
    private val traits: Traits<CommonMethod, CommonInst>,
    private val callStatement: CommonInst,
) : PositionResolver<Maybe<AccessPath>> {
    private val callExpr = traits.getCallExpr(callStatement)
        ?: error("Call statement should have non-null callExpr")

    override fun resolve(position: Position): Maybe<AccessPath> = with(traits) {
        when (position) {
            AnyArgument -> Maybe.none()
            is Argument -> convertToPathOrNull(callExpr.args[position.index]).toMaybe()
            This -> (callExpr as? CommonInstanceCallExpr)?.instance?.let { convertToPathOrNull(it) }.toMaybe()
            Result -> (callStatement as? CommonAssignInst)?.lhv?.let { convertToPathOrNull(it) }.toMaybe()
            ResultAnyElement -> (callStatement as? CommonAssignInst)?.lhv?.let { convertToPathOrNull(it) }.toMaybe()
                .fmap { it + ElementAccessor }
        }
    }
}

class CallPositionToValueResolver(
    traits: Traits<CommonMethod, CommonInst>,
    private val callStatement: CommonInst,
) : PositionResolver<Maybe<CommonValue>> {
    private val callExpr = traits.getCallExpr(callStatement)
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
    private val traits: Traits<CommonMethod, CommonInst>,
    private val method: CommonMethod,
) : PositionResolver<Maybe<CommonValue>> {
    override fun resolve(position: Position): Maybe<CommonValue> = with(traits) {
        when (position) {
            This -> Maybe.some(getThisInstance(method))

            is Argument -> {
                val p = method.parameters[position.index]
                getArgument(p).toMaybe()
            }

            AnyArgument, Result, ResultAnyElement -> error("Unexpected $position")
        }
    }
}

class EntryPointPositionToAccessPathResolver(
    private val traits: Traits<CommonMethod, CommonInst>,
    private val method: CommonMethod,
) : PositionResolver<Maybe<AccessPath>> {
    override fun resolve(position: Position): Maybe<AccessPath> = with(traits) {
        when (position) {
            This -> convertToPathOrNull(getThisInstance(method)).toMaybe()

            is Argument -> {
                val p = method.parameters[position.index]
                getArgument(p)?.let { convertToPathOrNull(it) }.toMaybe()
            }

            AnyArgument, Result, ResultAnyElement -> error("Unexpected $position")
        }
    }
}
