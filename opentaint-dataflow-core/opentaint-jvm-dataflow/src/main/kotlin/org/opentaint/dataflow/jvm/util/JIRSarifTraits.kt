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

package org.opentaint.dataflow.jvm.util

import org.opentaint.ir.api.common.cfg.CommonAssignInst
import org.opentaint.ir.api.common.cfg.CommonCallExpr
import org.opentaint.ir.api.common.cfg.CommonExpr
import org.opentaint.ir.api.jvm.JIRClasspath
import org.opentaint.ir.api.jvm.JIRMethod
import org.opentaint.ir.api.jvm.cfg.JIRCallExpr
import org.opentaint.ir.api.jvm.cfg.JIRInst
import org.opentaint.ir.api.jvm.cfg.JIRThis
import org.opentaint.ir.api.jvm.cfg.JIRValue
import org.opentaint.ir.api.jvm.cfg.JIRFieldRef
import org.opentaint.ir.api.jvm.cfg.JIRAssignInst
import org.opentaint.ir.api.jvm.cfg.JIRExpr
import org.opentaint.ir.api.jvm.cfg.JIRLocalVar
import org.opentaint.ir.api.jvm.cfg.values
import org.opentaint.ir.api.jvm.ext.toType
import org.opentaint.ir.api.jvm.ext.cfg.callExpr
import org.opentaint.dataflow.util.SarifTraits

class JIRSarifTraits(
    val cp: JIRClasspath,
) : SarifTraits<JIRMethod, JIRInst> {


    override fun getCallee(callExpr: CommonCallExpr): JIRMethod {
        check(callExpr is JIRCallExpr)
        return callExpr.callee
    }

    override fun getCalleeClassName(callExpr: CommonCallExpr): String {
        check(callExpr is JIRCallExpr)
        return callExpr.callee.enclosingClass.simpleName
    }

    override fun getCallExpr(statement: JIRInst): JIRCallExpr? {
        return statement.callExpr
    }

    override fun getReadableValue(expr: CommonExpr): String? {
        if (expr !is JIRValue) return null
        return when (expr) {
            is JIRFieldRef -> "${expr.instance ?: expr.field.enclosingType.jirClass.simpleName}.${expr.field.name}"
            else -> expr.toString()
        }
    }

    override fun getReadableAssignee(statement: JIRInst): String? {
        if (statement !is JIRAssignInst) return null
        return when (val expr = statement.lhv) {
            is JIRFieldRef -> "${expr.instance ?: expr.field.enclosingType.jirClass.simpleName}.${expr.field.name}"
            else -> expr.toString()
        }
    }

    override fun getLocals(expr: CommonExpr): List<SarifTraits.LocalInfo> {
        expr as JIRExpr
        return expr.values.filterIsInstance<JIRLocalVar>().map { SarifTraits.LocalInfo(it.index, it.name) }
    }

    override fun getAssign(statement: JIRInst): CommonAssignInst? {
        if (statement !is JIRAssignInst) return null
        return statement
    }

    override fun lineNumber(statement: JIRInst): Int {
        return statement.lineNumber
    }

    override fun locationFQN(statement: JIRInst): String {
        val method = statement.location.method
        return "${method.enclosingClass.name}#${method.name}"
    }

    override fun locationMachineName(statement: JIRInst): String =
        "${statement.method}:${statement.location.index}:($statement)"
}

val JIRMethod.thisInstance: JIRThis
    get() = JIRThis(enclosingClass.toType())

val JIRCallExpr.callee: JIRMethod
    get() = method.method
