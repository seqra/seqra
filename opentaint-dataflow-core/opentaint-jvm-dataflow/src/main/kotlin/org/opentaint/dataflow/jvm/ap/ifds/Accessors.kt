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

package org.opentaint.dataflow.jvm.ap.ifds

import org.opentaint.ir.taint.configuration.TaintMark

sealed interface AccessPathBase {
    override fun toString(): String

    object This : AccessPathBase {
        override fun toString(): String = "<this>"
    }

    data class LocalVar(val idx: Int) : AccessPathBase {
        override fun toString(): String = "var($idx)"
    }

    data class Argument(val idx: Int) : AccessPathBase {
        override fun toString(): String = "arg($idx)"
    }

    data class Constant(val typeName: String, val value: String) : AccessPathBase {
        override fun toString(): String = "const<$typeName>($value)"
    }

    data class ClassStatic(val typeName: String) : AccessPathBase {
        override fun toString(): String = "<static>($typeName)"
    }
}

sealed class Accessor : Comparable<Accessor> {
    abstract fun toSuffix(): String
    protected abstract val accessorClassId: Int

    override fun compareTo(other: Accessor): Int {
        if (accessorClassId != other.accessorClassId) {
            return accessorClassId.compareTo(other.accessorClassId)
        }

        return when (this) {
            ElementAccessor, FinalAccessor -> 0 // Definitely equal
            is FieldAccessor -> this.compareToFieldAccessor(other as FieldAccessor)
            is TaintMarkAccessor -> this.compareToTaintMarkAccessor(other as TaintMarkAccessor)
        }
    }
}

data class TaintMarkAccessor(val mark: TaintMark): Accessor() {
    override fun toSuffix(): String = "![$mark]"
    override fun toString(): String = "![$mark]"

    override val accessorClassId: Int = 3

    fun compareToTaintMarkAccessor(other: TaintMarkAccessor): Int {
        return mark.name.compareTo(other.mark.name)
    }
}

data class FieldAccessor(
    val className: String,
    val fieldName: String,
    val fieldType: String
) : Accessor() {
    override fun toSuffix(): String = ".$fieldName"
    override fun toString(): String = "${className}#${fieldName}:$fieldType"

    override val accessorClassId: Int = 2

    fun compareToFieldAccessor(other: FieldAccessor): Int {
        var result = fieldName.length.compareTo(other.fieldName.length)

        if (result == 0) {
            result = fieldName.compareTo(other.fieldName)
        }

        if (result == 0) {
            result = className.compareTo(other.className)
        }

        if (result == 0) {
            result = fieldType.compareTo(other.fieldType)
        }

        return result
    }
}

object ElementAccessor : Accessor() {
    override fun toSuffix(): String = "[*]"
    override fun toString(): String = "*"

    override val accessorClassId: Int = 0
}

object FinalAccessor : Accessor() {
    override fun toSuffix(): String = ".\$"
    override fun toString(): String = "\$"

    override val accessorClassId: Int = 1
}
