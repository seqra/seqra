package org.opentaint.dataflow.go.rules

import org.opentaint.dataflow.configuration.CommonTaintRulesProvider
import org.opentaint.dataflow.configuration.jvm.serialized.PositionBase
import org.opentaint.dataflow.configuration.jvm.serialized.PositionBaseWithModifiers

sealed interface TaintRules {
    data class Source(val function: String, val mark: String, val pos: PositionBase) : TaintRules
    data class Sink(val function: String, val mark: String, val pos: PositionBase, val id: String) : TaintRules
    data class Pass(val function: String, val from: PositionBaseWithModifiers, val to: PositionBaseWithModifiers) : TaintRules
}

data class GoTaintConfig(
    val sources: List<TaintRules.Source>,
    val sinks: List<TaintRules.Sink>,
    val propagators: List<TaintRules.Pass>,
) : CommonTaintRulesProvider
