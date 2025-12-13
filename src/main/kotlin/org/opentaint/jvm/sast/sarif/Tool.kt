package org.opentaint.jvm.sast.sarif

import io.github.detekt.sarif4k.ReportingConfiguration
import io.github.detekt.sarif4k.ReportingDescriptor
import io.github.detekt.sarif4k.Tool
import io.github.detekt.sarif4k.Level
import io.github.detekt.sarif4k.ToolComponent
import io.github.detekt.sarif4k.MultiformatMessageString
import io.github.detekt.sarif4k.PropertyBag
import org.opentaint.dataflow.configuration.CommonTaintConfigurationSinkMeta
import org.opentaint.semgrep.pattern.RuleMetadata
import org.opentaint.semgrep.pattern.readStrings

private fun generateSarifRuleDescription(metadata: RuleMetadata): ReportingDescriptor {
    val level = when (metadata.severity) {
        CommonTaintConfigurationSinkMeta.Severity.Note -> Level.Note
        CommonTaintConfigurationSinkMeta.Severity.Warning -> Level.Warning
        CommonTaintConfigurationSinkMeta.Severity.Error -> Level.Error
    }

    val tags = if (metadata.metadata == null) emptyList() else {
        val cwes = metadata.metadata!!.readStrings("cwe") ?: emptyList()
        val owasps = metadata.metadata!!.readStrings("owasp")?.map { "OWASP-$it" } ?: emptyList()
        val confidence = metadata.metadata!!.readStrings("confidence")?.map { "$it CONFIDENCE" } ?: emptyList()
        val category = metadata.metadata!!.readStrings("category") ?: emptyList()
        cwes + owasps + confidence + category
    }

    return ReportingDescriptor(
        id = metadata.ruleId,
        name = metadata.ruleId,
        defaultConfiguration = ReportingConfiguration(level = level),
        fullDescription = MultiformatMessageString(text = metadata.message),
        shortDescription = MultiformatMessageString(text = "Opentaint: ${metadata.ruleId}"),
        help = MultiformatMessageString(text = metadata.message),
        properties = PropertyBag(tags)
    )
}

fun generateSarifAnalyzerToolDescription(metadatas: List<RuleMetadata>): Tool {
    val toolOrganization = System.getenv("SARIF_ORGANIZATION") ?: "Opentaint"
    val toolVersion = System.getenv("SARIF_VERSION") ?: "0.0.0"
    val rules = metadatas.map { generateSarifRuleDescription(it) }

    return Tool(
        driver = ToolComponent(name = "SAST", organization = toolOrganization, version = toolVersion, rules = rules)
    )
}
