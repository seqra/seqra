@file:JvmName("RunnersLibrary")

package org.opentaint.ir.analysis.library

import org.opentaint.ir.analysis.engine.BaseIfdsUnitRunnerFactory
import org.opentaint.ir.analysis.engine.BidiIfdsUnitRunnerFactory
import org.opentaint.ir.analysis.library.analyzers.JIRAliasAnalyzerFactory
import org.opentaint.ir.analysis.library.analyzers.JIRNpeAnalyzerFactory
import org.opentaint.ir.analysis.library.analyzers.jIRNpePrecalcBackwardAnalyzerFactory
import org.opentaint.ir.analysis.library.analyzers.JIRSqlInjectionAnalyzerFactory
import org.opentaint.ir.analysis.library.analyzers.JIRSqlInjectionBackwardAnalyzerFactory
import org.opentaint.ir.analysis.library.analyzers.TaintAnalysisNode
import org.opentaint.ir.analysis.library.analyzers.TaintNode
import org.opentaint.ir.analysis.library.analyzers.JIRUnusedVariableAnalyzerFactory
import org.opentaint.ir.api.jvm.JIRMethod
import org.opentaint.ir.api.jvm.cfg.JIRExpr
import org.opentaint.ir.api.jvm.cfg.JIRInst
import org.opentaint.ir.api.jvm.cfg.JIRInstLocation

//TODO: add docs here
val UnusedVariableRunnerFactory =
    BaseIfdsUnitRunnerFactory<JIRMethod, JIRInstLocation, JIRInst>(JIRUnusedVariableAnalyzerFactory)

fun newJIRSqlInjectionRunnerFactory(maxPathLength: Int = 5) =
    BidiIfdsUnitRunnerFactory<JIRMethod, JIRInstLocation, JIRInst>(
        BaseIfdsUnitRunnerFactory(JIRSqlInjectionAnalyzerFactory(maxPathLength)),
        BaseIfdsUnitRunnerFactory(JIRSqlInjectionBackwardAnalyzerFactory(maxPathLength)),
    )

fun newJIRNpeRunnerFactory(maxPathLength: Int = 5) = BidiIfdsUnitRunnerFactory<JIRMethod, JIRInstLocation, JIRInst>(
    BaseIfdsUnitRunnerFactory(JIRNpeAnalyzerFactory(maxPathLength)),
    BaseIfdsUnitRunnerFactory(jIRNpePrecalcBackwardAnalyzerFactory(maxPathLength)),
    isParallel = false
)

fun newJIRAliasRunnerFactory(
    generates: (JIRInst) -> List<TaintAnalysisNode>,
    sanitizes: (JIRExpr, TaintNode) -> Boolean,
    sinks: (JIRInst) -> List<TaintAnalysisNode>,
    maxPathLength: Int = 5
) = BaseIfdsUnitRunnerFactory<JIRMethod, JIRInstLocation, JIRInst>(
    JIRAliasAnalyzerFactory(generates, sanitizes, sinks, maxPathLength)
)