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

package org.opentaint.dataflow.ifds

import org.opentaint.ir.api.common.cfg.CommonValue

data class AccessPath(
    val value: CommonValue?,
    val accesses: List<Accessor>,
) {
    init {
        if (value == null) {
            require(accesses.isNotEmpty())
            val a = accesses[0]
            require(a is FieldAccessor)
            require(a.isStatic)
        }
    }

    fun limit(n: Int): AccessPath = AccessPath(value, accesses.take(n))

    operator fun plus(accesses: List<Accessor>): AccessPath {
        for (accessor in accesses) {
            if (accessor is FieldAccessor && accessor.isStatic) {
                throw IllegalArgumentException("Unexpected static field: ${accessor.name}")
            }
        }

        return AccessPath(value, this.accesses + accesses)
    }

    operator fun plus(accessor: Accessor): AccessPath {
        if (accessor is FieldAccessor && accessor.isStatic) {
            throw IllegalArgumentException("Unexpected static field: ${accessor.name}")
        }

        return AccessPath(value, this.accesses + accessor)
    }

    override fun toString(): String {
        return value.toString() + accesses.joinToString("") { it.toSuffix() }
    }
}

val AccessPath.isOnHeap: Boolean
    get() = accesses.isNotEmpty()

val AccessPath.isStatic: Boolean
    get() = value == null

operator fun AccessPath.minus(other: AccessPath): List<Accessor>? {
    if (value != other.value) return null
    if (accesses.take(other.accesses.size) != other.accesses) return null
    return accesses.drop(other.accesses.size)
}
