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

sealed interface Accessor {
    fun toSuffix(): String
}

data class FieldAccessor(
    val className: String,
    val fieldName: String,
    val fieldType: String
) : Accessor, Comparable<FieldAccessor> {
    override fun toSuffix(): String = ".$fieldName"
    override fun toString(): String = "${className}#${fieldName}:$fieldType"

    override fun compareTo(other: FieldAccessor): Int {
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

object ElementAccessor : Accessor {
    override fun toSuffix(): String = "[*]"
    override fun toString(): String = "*"
}

object FinalAccessor : Accessor {
    override fun toSuffix(): String = ".\$"
    override fun toString(): String = "\$"
}
