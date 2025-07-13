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

package org.opentaint.dataflow.jvm.unused

import org.opentaint.ir.api.common.CommonMethod
import org.opentaint.ir.api.common.cfg.CommonExpr
import org.opentaint.ir.api.common.cfg.CommonInst
import org.opentaint.ir.api.jvm.cfg.JIRArrayAccess
import org.opentaint.ir.api.jvm.cfg.JIRAssignInst
import org.opentaint.ir.api.jvm.cfg.JIRBranchingInst
import org.opentaint.ir.api.jvm.cfg.JIRInst
import org.opentaint.ir.api.jvm.cfg.JIRLocal
import org.opentaint.ir.api.jvm.cfg.JIRSpecialCallExpr
import org.opentaint.ir.api.jvm.cfg.JIRTerminatingInst
import org.opentaint.dataflow.ifds.AccessPath
import org.opentaint.dataflow.util.Traits

context(Traits<CommonMethod, CommonInst>)
internal fun AccessPath.isUsedAt(
    expr: CommonExpr,
): Boolean {
    return getValues(expr).any {
        convertToPathOrNull(it) == this
    }
}

context(Traits<CommonMethod, CommonInst>)
internal fun AccessPath.isUsedAt(
    inst: CommonInst,
): Boolean {
    val callExpr = getCallExpr(inst)

    if (callExpr != null) {
        // Don't count constructor calls as usages
        if (callExpr is JIRSpecialCallExpr
            && callExpr.method.method.isConstructor
            && isUsedAt(callExpr.instance)
        ) {
            return false
        }

        return isUsedAt(callExpr)
    }
    if (inst is JIRAssignInst) {
        if (inst.lhv is JIRArrayAccess && isUsedAt(inst.lhv)) {
            return true
        }
        return isUsedAt(inst.rhv) && (inst.lhv !is JIRLocal || inst.rhv !is JIRLocal)
    }
    if (inst is JIRTerminatingInst || inst is JIRBranchingInst) {
        inst as JIRInst
        return inst.operands.any { isUsedAt(it) }
    }
    return false
}
