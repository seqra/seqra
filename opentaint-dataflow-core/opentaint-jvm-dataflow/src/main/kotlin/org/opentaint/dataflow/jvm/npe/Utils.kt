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

package org.opentaint.dataflow.jvm.npe

import org.opentaint.dataflow.ifds.AccessPath
import org.opentaint.dataflow.ifds.minus
import org.opentaint.dataflow.util.Traits
import org.opentaint.dataflow.util.startsWith
import org.opentaint.ir.api.common.CommonMethod
import org.opentaint.ir.api.common.cfg.CommonExpr
import org.opentaint.ir.api.common.cfg.CommonInst
import org.opentaint.ir.api.jvm.cfg.JIRInstanceCallExpr
import org.opentaint.ir.api.jvm.cfg.JIRLengthExpr

context(Traits<CommonMethod, CommonInst>)
internal fun AccessPath?.isDereferencedAt(expr: CommonExpr): Boolean {
    if (this == null) {
        return false
    }

    if (expr is JIRInstanceCallExpr) {
        val instancePath = expr.instance.toPathOrNull()
        if (instancePath.startsWith(this)) {
            return true
        }
    }

    if (expr is JIRLengthExpr) {
        val arrayPath = expr.array.toPathOrNull()
        if (arrayPath.startsWith(this)) {
            return true
        }
    }

    return expr
        .getValues()
        .mapNotNull { it.toPathOrNull() }
        .any { (it - this)?.isNotEmpty() == true }
}

context(Traits<CommonMethod, CommonInst>)
internal fun AccessPath?.isDereferencedAt(inst: CommonInst): Boolean {
    if (this == null) return false
    return inst.getOperands().any { isDereferencedAt(it) }
}
