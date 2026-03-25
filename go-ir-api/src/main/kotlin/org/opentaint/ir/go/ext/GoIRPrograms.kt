@file:JvmName("GoIRPrograms")
package org.opentaint.ir.go.ext

import org.opentaint.ir.go.api.GoIRFunction
import org.opentaint.ir.go.api.GoIRNamedType
import org.opentaint.ir.go.api.GoIRProgram

fun GoIRProgram.findFunctionByFullName(fullName: String): GoIRFunction? =
    allFunctions().find { it.fullName == fullName }

fun GoIRProgram.findNamedTypeByFullName(fullName: String): GoIRNamedType? =
    allNamedTypes().find { it.fullName == fullName }
