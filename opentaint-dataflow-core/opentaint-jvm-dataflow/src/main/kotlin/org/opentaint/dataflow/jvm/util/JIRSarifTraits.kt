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

import org.opentaint.ir.api.jvm.JIRClasspath
import org.opentaint.ir.api.jvm.JIRMethod
import org.opentaint.ir.api.jvm.cfg.JIRCallExpr
import org.opentaint.ir.api.jvm.cfg.JIRInst
import org.opentaint.ir.api.jvm.cfg.JIRThis
import org.opentaint.ir.api.jvm.ext.toType
import org.opentaint.dataflow.util.SarifTraits

class JIRSarifTraits(
    val cp: JIRClasspath,
) : SarifTraits<JIRMethod, JIRInst> {


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
