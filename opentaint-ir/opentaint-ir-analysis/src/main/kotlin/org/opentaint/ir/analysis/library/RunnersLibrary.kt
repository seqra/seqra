@file:JvmName("RunnersLibrary")
package org.opentaint.ir.analysis.library

import org.opentaint.ir.analysis.engine.BaseIfdsUnitRunnerFactory
import org.opentaint.ir.analysis.engine.BidiIfdsUnitRunnerFactory
import org.opentaint.ir.analysis.library.analyzers.AliasAnalyzerFactory
import org.opentaint.ir.analysis.library.analyzers.NpeAnalyzerFactory
import org.opentaint.ir.analysis.library.analyzers.NpePrecalcBackwardAnalyzerFactory
import org.opentaint.ir.analysis.library.analyzers.SqlInjectionAnalyzerFactory
import org.opentaint.ir.analysis.library.analyzers.SqlInjectionBackwardAnalyzerFactory
import org.opentaint.ir.analysis.library.analyzers.TaintAnalysisNode
import org.opentaint.ir.analysis.library.analyzers.TaintNode
import org.opentaint.ir.analysis.library.analyzers.UnusedVariableAnalyzerFactory
import org.opentaint.ir.api.jvm.cfg.JIRExpr
import org.opentaint.ir.api.jvm.cfg.JIRInst

//TODO: add docs here
val UnusedVariableRunnerFactory = BaseIfdsUnitRunnerFactory(UnusedVariableAnalyzerFactory)

fun newSqlInjectionRunnerFactory(maxPathLength: Int = 5) = BidiIfdsUnitRunnerFactory(
    BaseIfdsUnitRunnerFactory(SqlInjectionAnalyzerFactory(maxPathLength)),
    BaseIfdsUnitRunnerFactory(SqlInjectionBackwardAnalyzerFactory(maxPathLength)),
)

fun newNpeRunnerFactory(maxPathLength: Int = 5) = BidiIfdsUnitRunnerFactory(
    BaseIfdsUnitRunnerFactory(NpeAnalyzerFactory(maxPathLength)),
    BaseIfdsUnitRunnerFactory(NpePrecalcBackwardAnalyzerFactory(maxPathLength)),
    isParallel = false
)

fun newAliasRunnerFactory(
    generates: (JIRInst) -> List<TaintAnalysisNode>,
    sanitizes: (JIRExpr, TaintNode) -> Boolean,
    sinks: (JIRInst) -> List<TaintAnalysisNode>,
    maxPathLength: Int = 5
) = BaseIfdsUnitRunnerFactory(AliasAnalyzerFactory(generates, sanitizes, sinks, maxPathLength))