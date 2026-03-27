@file:JvmName("GoIRPrograms")
package org.opentaint.ir.go.ext

import org.opentaint.ir.go.api.GoIRFunction
import org.opentaint.ir.go.api.GoIRNamedType
import org.opentaint.ir.go.api.GoIRProgram

fun GoIRProgram.findFunctionByFullName(fullName: String): GoIRFunction? =
    allFunctions().find { it.fullName == fullName }

fun GoIRProgram.findNamedTypeByFullName(fullName: String): GoIRNamedType? =
    allNamedTypes().find { it.fullName == fullName }

/**
 * Find a function by short name (e.g., "hello") within any package.
 * Matches on the function's [GoIRFunction.name] property.
 */
fun GoIRProgram.findFunctionByName(name: String): GoIRFunction? =
    allFunctions().find { it.name == name }

/**
 * Find a named type by short name (e.g., "Point") within any package.
 */
fun GoIRProgram.findNamedTypeByName(name: String): GoIRNamedType? =
    allNamedTypes().find { it.name == name }
