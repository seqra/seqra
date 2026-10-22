package org.opentaint.go.sast.dataflow

import org.opentaint.dataflow.ifds.UnitResolver
import org.opentaint.dataflow.ifds.UnitType
import org.opentaint.dataflow.ifds.UnknownUnit
import org.opentaint.ir.go.api.GoIRFunction
import org.opentaint.ir.go.api.GoIRModelFunction

class GoUnitResolver : UnitResolver<GoIRFunction> {
    data class GoPackageUnit(val pkgImportPath: String) : UnitType

    override fun resolve(method: GoIRFunction): UnitType {
        val pkg = method.pkg ?: return UnknownUnit
        return if (pkg.isProject || method is GoIRModelFunction) {
            GoPackageUnit(pkg.importPath)
        } else {
            UnknownUnit
        }
    }
}
