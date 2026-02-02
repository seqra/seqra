package org.opentaint.jvm.sast.sarif

import io.github.detekt.sarif4k.Level
import io.github.detekt.sarif4k.MultiformatMessageString
import io.github.detekt.sarif4k.PropertyBag
import io.github.detekt.sarif4k.ReportingConfiguration
import io.github.detekt.sarif4k.ReportingDescriptor
import io.github.detekt.sarif4k.Tool
import io.github.detekt.sarif4k.ToolComponent
import org.opentaint.dataflow.configuration.CommonTaintConfigurationSinkMeta
import org.opentaint.jvm.sast.project.SarifGenerationOptions
import org.opentaint.semgrep.pattern.RuleMetadata
import org.opentaint.semgrep.pattern.SemgrepRuleUtils
import org.opentaint.semgrep.pattern.readStrings
import java.io.File

private fun generateSarifRuleDescription(metadata: RuleMetadata, options: SarifGenerationOptions): ReportingDescriptor {
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

    val shortDescription = metadata.metadata?.readStrings("shortDescription")?.firstOrNull()
        ?: "Opentaint Finding: ${options.formatRuleId(metadata.ruleId)}"

    return ReportingDescriptor(
        id = options.formatRuleId(metadata.ruleId),
        name = options.formatRuleId(metadata.ruleId),
        defaultConfiguration = ReportingConfiguration(level = level),
        fullDescription = MultiformatMessageString(text = metadata.message),
        shortDescription = MultiformatMessageString(text = shortDescription),
        help = MultiformatMessageString(text = metadata.message),
        properties = PropertyBag(tags)
    )
}

fun generateSarifAnalyzerToolDescription(metadatas: List<RuleMetadata>, options: SarifGenerationOptions): Tool {
    val rules = metadatas.map { generateSarifRuleDescription(it, options) }

    return Tool(
        driver = ToolComponent(
            name = "Opentaint",
            organization = "Opentaint",
            version = options.toolVersion,
            semanticVersion = options.toolSemanticVersion,
            rules = rules
        )
    )
}

fun SarifGenerationOptions.formatRuleId(ruleId: String): String {
    if (!useSemgrepStyleId) return ruleId

    val (rulePath, rawRuleId) = SemgrepRuleUtils.extractRuleSetNameAndId(ruleId)
        ?: return ruleId

    val idParts = rulePath.split(File.separatorChar)
        .dropLast(1) + listOf(rawRuleId)

    return idParts.joinToString(separator = ".") { it.trim() }
}
