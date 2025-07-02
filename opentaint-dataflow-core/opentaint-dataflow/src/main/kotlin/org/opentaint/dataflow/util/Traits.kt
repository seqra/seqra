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

package org.opentaint.dataflow.util

import org.opentaint.ir.api.common.CommonMethod
import org.opentaint.ir.api.common.CommonMethodParameter
import org.opentaint.ir.api.common.CommonProject
import org.opentaint.ir.api.common.cfg.CommonArgument
import org.opentaint.ir.api.common.cfg.CommonAssignInst
import org.opentaint.ir.api.common.cfg.CommonCallExpr
import org.opentaint.ir.api.common.cfg.CommonExpr
import org.opentaint.ir.api.common.cfg.CommonInst
import org.opentaint.ir.api.common.cfg.CommonThis
import org.opentaint.ir.api.common.cfg.CommonValue
import org.opentaint.ir.taint.configuration.ConstantValue
import org.opentaint.ir.taint.configuration.TypeMatches
import org.opentaint.dataflow.ifds.AccessPath

/**
 * Extensions for analysis.
 */
interface Traits<out Method, out Statement>
    where Method : CommonMethod,
          Statement : CommonInst {

    val @UnsafeVariance Method.thisInstance: CommonThis
    val @UnsafeVariance Method.isConstructor: Boolean

    fun CommonExpr.toPathOrNull(): AccessPath?
    fun CommonValue.toPathOrNull(): AccessPath?
    fun CommonValue.toPath(): AccessPath

    val CommonCallExpr.callee: Method

    fun CommonProject.getArgument(param: CommonMethodParameter): CommonArgument?
    fun CommonProject.getArgumentsOf(method: @UnsafeVariance Method): List<CommonArgument>

    fun CommonValue.isConstant(): Boolean
    fun CommonValue.eqConstant(constant: ConstantValue): Boolean
    fun CommonValue.ltConstant(constant: ConstantValue): Boolean
    fun CommonValue.gtConstant(constant: ConstantValue): Boolean
    fun CommonValue.matches(pattern: String): Boolean

    // TODO: remove
    fun CommonExpr.toPaths(): List<AccessPath> = listOfNotNull(toPathOrNull())

    fun @UnsafeVariance Statement.getCallExpr(): CommonCallExpr?
    fun CommonExpr.getValues(): Set<CommonValue>
    fun @UnsafeVariance Statement.getOperands(): List<CommonExpr>
    fun @UnsafeVariance Statement.getBranchExprCondition(): CommonExpr?

    fun @UnsafeVariance Statement.getArrayAllocation(): CommonExpr?
    fun @UnsafeVariance Statement.getArrayAccessIndex(): CommonValue?

    fun @UnsafeVariance Statement.isLoopHead(): Boolean

    fun @UnsafeVariance Statement.lineNumber(): Int?
    fun @UnsafeVariance Statement.locationFQN(): String?

    fun CommonValue.typeMatches(condition: TypeMatches): Boolean

    fun CommonAssignInst.taintFlowRhsValues(): List<CommonExpr>

    fun @UnsafeVariance Statement.taintPassThrough(): List<Pair<CommonValue, CommonValue>>?
}
