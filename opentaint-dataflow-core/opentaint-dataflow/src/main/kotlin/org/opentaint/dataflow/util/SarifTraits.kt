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
import org.opentaint.ir.api.common.cfg.CommonInst
import org.opentaint.ir.api.common.cfg.CommonCallExpr
import org.opentaint.ir.api.common.cfg.CommonAssignInst
import org.opentaint.ir.api.common.cfg.CommonExpr

interface SarifTraits<out Method, out Statement>
    where Method : CommonMethod,
          Statement : CommonInst {

    fun getCallee(callExpr: CommonCallExpr): Method
    fun getCalleeClassName(callExpr: CommonCallExpr): String
    fun getCallExpr(statement: @UnsafeVariance Statement): CommonCallExpr?
    fun getAssign(statement: @UnsafeVariance Statement): CommonAssignInst?
    fun getReadableValue(expr: CommonExpr): String?
    fun getReadableAssignee(statement: @UnsafeVariance Statement): String?

    data class LocalInfo(val idx: Int, val name: String)
    fun getLocals(expr: CommonExpr): List<LocalInfo>

    fun lineNumber(statement: @UnsafeVariance Statement): Int
    fun locationFQN(statement: @UnsafeVariance Statement): String
    fun locationMachineName(statement: @UnsafeVariance Statement): String
}
