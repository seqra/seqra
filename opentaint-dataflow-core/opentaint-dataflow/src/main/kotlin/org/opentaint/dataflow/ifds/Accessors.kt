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

sealed interface Accessor {
    fun toSuffix(): String
}

data class FieldAccessor(
    val name: String,
    val isStatic: Boolean = false,
) : Accessor {
    override fun toSuffix(): String = ".${name}"
    override fun toString(): String = name
}

object ElementAccessor : Accessor {
    override fun toSuffix(): String = "[*]"
    override fun toString(): String = "*"
}
