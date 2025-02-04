package org.opentaint.ir.analysis.engine

import org.opentaint.ir.analysis.points2.Devirtualizer
import org.opentaint.ir.api.JIRMethod
import org.opentaint.ir.api.analysis.ApplicationGraph
import org.opentaint.ir.api.cfg.JIRInst

interface IFDSInstance {
    fun addStart(method: JIRMethod)

    fun analyze(): Map<JIRMethod, IFDSMethodSummary>
}

interface IFDSInstanceProvider {
    fun <UnitType> createInstance(
        graph: ApplicationGraph<JIRMethod, JIRInst>,
        analyzer: Analyzer,
        devirtualizer: Devirtualizer,
        context: AnalysisContext,
        unitResolver: UnitResolver<UnitType>,
        unit: UnitType
    ): IFDSInstance
}