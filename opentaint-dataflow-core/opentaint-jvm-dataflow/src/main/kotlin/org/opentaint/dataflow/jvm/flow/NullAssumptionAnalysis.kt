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

import org.opentaint.ir.api.jvm.JIRRefType
import org.opentaint.ir.api.jvm.cfg.JIRArrayAccess
import org.opentaint.ir.api.jvm.cfg.JIRAssignInst
import org.opentaint.ir.api.jvm.cfg.JIRCallExpr
import org.opentaint.ir.api.jvm.cfg.JIRCastExpr
import org.opentaint.ir.api.jvm.cfg.JIREnterMonitorInst
import org.opentaint.ir.api.jvm.cfg.JIRFieldRef
import org.opentaint.ir.api.jvm.cfg.JIRGraph
import org.opentaint.ir.api.jvm.cfg.JIRInst
import org.opentaint.ir.api.jvm.cfg.JIRInstanceCallExpr
import org.opentaint.ir.api.jvm.cfg.JIRLocal
import org.opentaint.ir.api.jvm.cfg.JIRValue
import org.opentaint.ir.api.jvm.ext.cfg.arrayRef
import org.opentaint.ir.api.jvm.ext.cfg.callExpr
import org.opentaint.ir.api.jvm.ext.cfg.fieldRef

class NullAnalysisMap : HashMap<JIRValue, NullableState> {

    constructor() : super()
    constructor(m: Map<JIRValue, NullableState>) : super(m)

    override fun get(key: JIRValue): NullableState {
        return super.get(key) ?: NullableState.UNKNOWN
    }
}

enum class NullableState {
    UNKNOWN,
    NULL,
    NON_NULL;
}

/**
 * An inter-procedural nullness assumption analysis that computes for each location and each value in a method if the value
 * (before or after that location) is treated as definitely null, definitely non-null or neither. This information could be
 * useful in deciding whether to insert code that accesses a potentially null object.
 *
 * If the original program assumes a value is non-null, then adding a use of that value will not introduce any NEW nullness
 * errors into the program. This code may be buggy, or just plain wrong. It has not been checked.
 */
open class NullAssumptionAnalysis(graph: JIRGraph) : BackwardFlowAnalysis<JIRInst, NullAnalysisMap>(graph) {

    override val ins: MutableMap<JIRInst, NullAnalysisMap> = hashMapOf()
    override val outs: MutableMap<JIRInst, NullAnalysisMap> = hashMapOf()

    override fun flowThrough(
        instIn: NullAnalysisMap?,
        ins: JIRInst,
        instOut: NullAnalysisMap,
    ) {
        val out = instIn?.let { NullAnalysisMap(it) } ?: NullAnalysisMap()

        // programmer assumes we have a non-null value
        if (ins is JIREnterMonitorInst) {
            out[ins.monitor] = NullableState.NON_NULL
        }

        // if we have an array ref, set the info for this ref to TOP,
        // because we need to be conservative here
        ins.arrayRef?.let {
            onArrayAccess(it, out)
        }
        // same for field refs, but also set the receiver object to non-null, if there is one
        ins.fieldRef?.let {
            onFieldRef(it, out)
        }
        // same for invoke expr., also set the receiver object to non-null, if there is one
        ins.callExpr?.let {
            onCallExpr(it, out)
        }

        // allow sub-classes to define certain values as always-non-null
        for (entry in out.entries) {
            if (isAlwaysNonNull(entry.key)) {
                entry.setValue(NullableState.NON_NULL)
            }
        }

        // if we have a definition (assignment) statement to a ref-like type, handle it,
        if (ins is JIRAssignInst) {
            // need to copy the current out set because we need to assign under this assumption;
            // so this copy becomes the in-set to handleRefTypeAssignment
            if (ins.lhv.type is JIRRefType) {
                onRefTypeAssignment(ins, NullAnalysisMap(out), out)
            }
        }

        // save memory by only retaining information about locals
        val outIter = out.keys.iterator()
        while (outIter.hasNext()) {
            val v = outIter.next()
            if (!(v is JIRLocal)) {
                outIter.remove()
            }
        }

        // now copy the computed info to out
        copy(out, instOut)
    }

    protected open fun isAlwaysNonNull(v: JIRValue): Boolean {
        return false
    }

    private fun onArrayAccess(arrayRef: JIRArrayAccess, out: NullAnalysisMap) {
        // here we know that the array must point to an object, but the array value might be anything
        out[arrayRef.array] = NullableState.NON_NULL
    }

    private fun onFieldRef(fieldRef: JIRFieldRef, out: NullAnalysisMap) {
        // here we know that the receiver must point to an object
        val instance = fieldRef.instance
        if (instance != null) {
            out[instance] = NullableState.NON_NULL
        }
    }

    private fun onCallExpr(invokeExpr: JIRCallExpr, out: NullAnalysisMap) {
        if (invokeExpr is JIRInstanceCallExpr) {
            // here we know that the receiver must point to an object
            out[invokeExpr.instance] = NullableState.NON_NULL
        }
    }

    private fun onRefTypeAssignment(assignStmt: JIRAssignInst, rhsInfo: NullAnalysisMap, out: NullAnalysisMap) {
        val right = when (val rhv = assignStmt.rhv) {
            is JIRCastExpr -> rhv.operand
            is JIRValue -> rhv
            else -> null
        }
        if (right != null) {

            // An assignment invalidates any assumptions of null/non-null for lhs
            // We COULD be more accurate by assigning those assumptions to the rhs prior to this statement
            rhsInfo[right] = NullableState.UNKNOWN

            // assign from rhs to lhs
            out[assignStmt.lhv] = rhsInfo[right]
        }
    }

    override fun copy(source: NullAnalysisMap?, dest: NullAnalysisMap) {
        dest.clear()
        if (source != null) {
            dest.putAll(source)
        }
    }

    override fun newEntryFlow(): NullAnalysisMap {
        return NullAnalysisMap()
    }

    override fun newFlow(): NullAnalysisMap {
        return NullAnalysisMap()
    }

    override fun merge(in1: NullAnalysisMap, in2: NullAnalysisMap, out: NullAnalysisMap) {
        val values = HashSet<JIRValue>()
        values.addAll(in1.keys)
        values.addAll(in2.keys)
        out.clear()
        for (v in values) {
            val leftAndRight = HashSet<Any>()
            leftAndRight.add(in1[v])
            leftAndRight.add(in2[v])
            val result = if (leftAndRight.contains(NullableState.UNKNOWN)) {
                // if on either side we know nothing... then together we know nothing for sure
                NullableState.UNKNOWN
            } else if (leftAndRight.contains(NullableState.NON_NULL)) {
                if (leftAndRight.contains(NullableState.NULL)) {
                    // NULL and NON_NULL merges to BOTTOM
                    NullableState.UNKNOWN
                } else {
                    // NON_NULL and NON_NULL stays NON_NULL
                    NullableState.NON_NULL
                }
            } else if (leftAndRight.contains(NullableState.NULL)) {
                // NULL and NULL stays NULL
                NullableState.NULL
            } else {
                // only BOTTOM remains
                NullableState.UNKNOWN
            }
            out[v] = result
        }
    }

    /**
     * Returns `true` if the analysis could determine that `value` is always treated as null after and including the instruction inst.
     *
     * @param inst instruction of the respective body
     * @param value a local or constant value of that body
     * @return true if value is always null right before this statement
     */
    fun isAssumedNullBefore(inst: JIRInst, value: JIRValue): Boolean {
        return ins(inst)[value] == NullableState.NULL
    }

    /**
     * Returns `true` if the analysis could determine that value is always treated as non-null after and including the
     * statement s.
     *
     * @param inst instruction of the respective body
     * @param value a local or constant value of that body
     * @return true if value is always non-null right before this statement
     */
    fun isAssumedNonNullBefore(inst: JIRInst, value: JIRValue): Boolean {
        return ins(inst)[value] == NullableState.NON_NULL
    }

}
