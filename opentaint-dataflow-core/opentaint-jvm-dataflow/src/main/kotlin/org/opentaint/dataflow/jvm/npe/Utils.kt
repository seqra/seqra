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

import org.opentaint.ir.api.jvm.cfg.JIRExpr
import org.opentaint.ir.api.jvm.cfg.JIRInst
import org.opentaint.ir.api.jvm.cfg.JIRInstanceCallExpr
import org.opentaint.ir.api.jvm.cfg.JIRLengthExpr
import org.opentaint.ir.api.jvm.cfg.values
import org.opentaint.dataflow.ifds.AccessPath
import org.opentaint.dataflow.ifds.minus
import org.opentaint.dataflow.jvm.util.JIRTraits
import org.opentaint.dataflow.util.startsWith

internal fun AccessPath?.isDereferencedAt(traits: JIRTraits, expr: JIRExpr): Boolean = with(traits) {
    if (this@isDereferencedAt == null) {
        return false
    }

    if (expr is JIRInstanceCallExpr) {
        val instancePath = convertToPathOrNull(expr.instance)
        if (instancePath.startsWith(this@isDereferencedAt)) {
            return true
        }
    }

    if (expr is JIRLengthExpr) {
        val arrayPath = convertToPathOrNull(expr.array)
        if (arrayPath.startsWith(this@isDereferencedAt)) {
            return true
        }
    }

    return expr.values
        .mapNotNull { convertToPathOrNull(it) }
        .any { (it - this@isDereferencedAt)?.isNotEmpty() == true }
}

internal fun AccessPath?.isDereferencedAt(traits: JIRTraits, inst: JIRInst): Boolean {
    if (this == null) return false
    return inst.operands.any { isDereferencedAt(traits, it) }
}
