@file:JvmName("RunnersLibrary")
package org.opentaint.ir.analysis.library

import org.opentaint.ir.analysis.engine.IfdsBaseUnitRunner
import org.opentaint.ir.analysis.engine.SequentialBidiIfdsUnitRunner
import org.opentaint.ir.analysis.library.analyzers.AliasAnalyzerFactory
import org.opentaint.ir.analysis.library.analyzers.NpeAnalyzerFactory
import org.opentaint.ir.analysis.library.analyzers.NpePrecalcBackwardAnalyzerFactory
import org.opentaint.ir.analysis.library.analyzers.SqlInjectionAnalyzerFactory
import org.opentaint.ir.analysis.library.analyzers.SqlInjectionBackwardAnalyzerFactory
import org.opentaint.ir.analysis.library.analyzers.TaintAnalysisNode
import org.opentaint.ir.analysis.library.analyzers.TaintAnalyzerFactory
import org.opentaint.ir.analysis.library.analyzers.TaintBackwardAnalyzerFactory
import org.opentaint.ir.analysis.library.analyzers.TaintNode
import org.opentaint.ir.analysis.library.analyzers.UnusedVariableAnalyzerFactory
import org.opentaint.ir.api.JIRMethod
import org.opentaint.ir.api.cfg.JIRExpr
import org.opentaint.ir.api.cfg.JIRInst

//TODO: add docs here
val UnusedVariableRunner = IfdsBaseUnitRunner(UnusedVariableAnalyzerFactory)

fun newSqlInjectionRunner(maxPathLength: Int = 5) = SequentialBidiIfdsUnitRunner(
    IfdsBaseUnitRunner(SqlInjectionAnalyzerFactory(maxPathLength)),
    IfdsBaseUnitRunner(SqlInjectionBackwardAnalyzerFactory(maxPathLength)),
)

fun newNpeRunner(maxPathLength: Int = 5) = SequentialBidiIfdsUnitRunner(
    IfdsBaseUnitRunner(NpeAnalyzerFactory(maxPathLength)),
    IfdsBaseUnitRunner(NpePrecalcBackwardAnalyzerFactory(maxPathLength)),
)

fun newAliasRunner(
    generates: (JIRInst) -> List<TaintAnalysisNode>,
    sanitizes: (JIRExpr, TaintNode) -> Boolean,
    sinks: (JIRInst) -> List<TaintAnalysisNode>,
    maxPathLength: Int = 5
) = IfdsBaseUnitRunner(AliasAnalyzerFactory(generates, sanitizes, sinks, maxPathLength))

fun newTaintRunner(
    isSourceMethod: (JIRMethod) -> Boolean,
    isSanitizeMethod: (JIRMethod) -> Boolean,
    isSinkMethod: (JIRMethod) -> Boolean,
    maxPathLength: Int = 5
) = SequentialBidiIfdsUnitRunner(
    IfdsBaseUnitRunner(TaintAnalyzerFactory(isSourceMethod, isSanitizeMethod, isSinkMethod, maxPathLength)),
    IfdsBaseUnitRunner(TaintBackwardAnalyzerFactory(isSourceMethod, isSinkMethod, maxPathLength))
)

fun newTaintRunner(
    sourceMethodMatchers: List<String>,
    sanitizeMethodMatchers: List<String>,
    sinkMethodMatchers: List<String>,
    maxPathLength: Int = 5
) = SequentialBidiIfdsUnitRunner(
    IfdsBaseUnitRunner(TaintAnalyzerFactory(sourceMethodMatchers, sanitizeMethodMatchers, sinkMethodMatchers, maxPathLength)),
    IfdsBaseUnitRunner(TaintBackwardAnalyzerFactory(sourceMethodMatchers, sinkMethodMatchers, maxPathLength))
)