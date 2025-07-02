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

@file:JvmName("BackwardApplicationGraphs")

package org.opentaint.dataflow.graph

import org.opentaint.ir.api.common.CommonMethod
import org.opentaint.ir.api.common.CommonProject
import org.opentaint.ir.api.common.analysis.ApplicationGraph
import org.opentaint.ir.api.common.cfg.CommonInst

private class BackwardApplicationGraphImpl<Method, Statement>(
    val forward: ApplicationGraph<Method, Statement>,
) : ApplicationGraph<Method, Statement>
    where Method : CommonMethod,
          Statement : CommonInst {

    override val project: CommonProject
        get() = forward.project

    override fun predecessors(node: Statement) = forward.successors(node)
    override fun successors(node: Statement) = forward.predecessors(node)

    override fun callees(node: Statement) = forward.callees(node)
    override fun callers(method: Method) = forward.callers(method)

    override fun entryPoints(method: Method) = forward.exitPoints(method)
    override fun exitPoints(method: Method) = forward.entryPoints(method)

    override fun methodOf(node: Statement) = forward.methodOf(node)
}

val <Method, Statement> ApplicationGraph<Method, Statement>.reversed: ApplicationGraph<Method, Statement>
    where Method : CommonMethod,
          Statement : CommonInst
    get() = when (this) {
        is BackwardApplicationGraphImpl -> this.forward
        else -> BackwardApplicationGraphImpl(this)
    }
