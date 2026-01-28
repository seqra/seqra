package org.opentaint.jvm.sast.project.rules

import org.opentaint.dataflow.configuration.jvm.serialized.SerializedTaintConfig
import org.opentaint.dataflow.jvm.ap.ifds.taint.TaintRulesProvider
import org.opentaint.ir.api.jvm.JIRClasspath
import org.opentaint.jvm.sast.dataflow.JIRMethodExitRuleProvider
import org.opentaint.jvm.sast.dataflow.JIRMethodGetDefaultProvider
import org.opentaint.jvm.sast.dataflow.JIRTaintRulesProvider
import org.opentaint.jvm.sast.dataflow.rules.TaintConfiguration
import org.opentaint.jvm.sast.project.ProjectAnalysisContext
import org.opentaint.jvm.sast.project.spring.SpringRuleProvider
import org.opentaint.jvm.sast.util.loadDefaultConfig
import org.opentaint.semgrep.pattern.TaintRuleFromSemgrep
import org.opentaint.semgrep.pattern.createTaintConfig

fun List<TaintRuleFromSemgrep>.semgrepRulesWithDefaultConfig(
    cp: JIRClasspath
): JIRTaintRulesProvider {
    val defaultRules = loadDefaultConfig()
    val defaultPassRules = SerializedTaintConfig(passThrough = defaultRules.passThrough)

    val config = TaintConfiguration(cp)
    config.loadConfig(defaultPassRules)
    this.forEach { config.loadConfig(it.createTaintConfig()) }

    return JIRTaintRulesProvider(config)
}

fun ProjectAnalysisContext.analysisConfig(initialConfig: TaintRulesProvider): TaintRulesProvider {
    var config = initialConfig
    config = JIRMethodExitRuleProvider(config)
    config = JIRMethodGetDefaultProvider(config) { projectClasses.isProjectClass(it) }
    if (springWebProjectContext != null) {
        config = SpringRuleProvider(config, springWebProjectContext)
    }
    return config
}
