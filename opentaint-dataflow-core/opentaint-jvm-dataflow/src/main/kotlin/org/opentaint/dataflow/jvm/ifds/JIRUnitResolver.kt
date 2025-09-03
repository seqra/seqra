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

@file:Suppress("FunctionName")

package org.opentaint.dataflow.jvm.ifds

import org.opentaint.ir.api.jvm.JIRClassOrInterface
import org.opentaint.ir.api.jvm.JIRMethod
import org.opentaint.ir.api.jvm.ext.packageName
import org.opentaint.dataflow.ifds.SingletonUnit
import org.opentaint.dataflow.ifds.UnitResolver
import org.opentaint.dataflow.ifds.UnitType

data class MethodUnit(val method: JIRMethod) : UnitType {
    override fun toString(): String {
        return "MethodUnit(${method.name})"
    }
}

data class ClassUnit(val clazz: JIRClassOrInterface) : UnitType {
    override fun toString(): String {
        return "ClassUnit(${clazz.simpleName})"
    }
}

data class PackageUnit(val packageName: String) : UnitType {
    override fun toString(): String {
        return "PackageUnit($packageName)"
    }
}

fun interface JIRUnitResolver : UnitResolver<JIRMethod>

val MethodUnitResolver = JIRUnitResolver { method ->
    MethodUnit(method)
}

private val ClassUnitResolverWithNested = JIRUnitResolver { method ->
    val clazz = generateSequence(method.enclosingClass) { it.outerClass }.last()
    ClassUnit(clazz)
}
private val ClassUnitResolverWithoutNested = JIRUnitResolver { method ->
    val clazz = method.enclosingClass
    ClassUnit(clazz)
}

fun ClassUnitResolver(includeNested: Boolean) =
    if (includeNested) {
        ClassUnitResolverWithNested
    } else {
        ClassUnitResolverWithoutNested
    }

val PackageUnitResolver = JIRUnitResolver { method ->
    PackageUnit(method.enclosingClass.packageName)
}

val SingletonUnitResolver = JIRUnitResolver {
    SingletonUnit
}
