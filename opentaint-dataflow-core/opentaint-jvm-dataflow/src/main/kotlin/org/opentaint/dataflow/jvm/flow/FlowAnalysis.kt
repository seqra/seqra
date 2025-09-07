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

package org.opentaint.dataflow.jvm.flow

import org.opentaint.ir.api.jvm.cfg.JIRBytecodeGraph

interface FlowAnalysis<NODE, T> {
    val ins: MutableMap<NODE, T>
    val outs: MutableMap<NODE, T>
    val graph: JIRBytecodeGraph<NODE>
    val isForward: Boolean

    fun newFlow(): T
    fun newEntryFlow(): T
    fun merge(in1: T, in2: T, out: T)
    fun copy(source: T?, dest: T)
    fun run()
}
