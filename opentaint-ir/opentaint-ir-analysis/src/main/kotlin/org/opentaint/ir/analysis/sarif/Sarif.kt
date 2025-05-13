package org.opentaint.ir.analysis.sarif

import io.github.detekt.sarif4k.ArtifactLocation
import io.github.detekt.sarif4k.CodeFlow
import io.github.detekt.sarif4k.Location
import io.github.detekt.sarif4k.LogicalLocation
import io.github.detekt.sarif4k.Message
import io.github.detekt.sarif4k.MultiformatMessageString
import io.github.detekt.sarif4k.PhysicalLocation
import io.github.detekt.sarif4k.Region
import io.github.detekt.sarif4k.Result
import io.github.detekt.sarif4k.Run
import io.github.detekt.sarif4k.SarifSchema210
import io.github.detekt.sarif4k.ThreadFlow
import io.github.detekt.sarif4k.ThreadFlowLocation
import io.github.detekt.sarif4k.Tool
import io.github.detekt.sarif4k.ToolComponent
import io.github.detekt.sarif4k.Version
import org.opentaint.ir.analysis.ifds.Vertex
import org.opentaint.ir.api.JIRMethod
import org.opentaint.ir.api.cfg.JIRInst
import java.io.File

private const val SARIF_SCHEMA =
    "https://raw.githubusercontent.com/oasis-tcs/sarif-spec/master/Schemata/sarif-schema-2.1.0.json"
private const val OPENTAINT-IR_INFORMATION_URI =
    "https://github.com/Opentaint/opentaint-ir/blob/develop/opentaint-ir-analysis/README.md"
private const val DEFAULT_PATH_COUNT = 3

fun sarifReportFromVulnerabilities(
    vulnerabilities: List<VulnerabilityInstance<*>>,
    maxPathsCount: Int = DEFAULT_PATH_COUNT,
    isDeduplicate: Boolean = true,
    sourceFileResolver: SourceFileResolver = SourceFileResolver { null },
): SarifSchema210 {
    return SarifSchema210(
        schema = SARIF_SCHEMA,
        version = Version.The210,
        runs = listOf(
            Run(
                tool = Tool(
                    driver = ToolComponent(
                        name = "opentaint-ir-analysis",
                        organization = "Opentaint",
                        version = "1.4.5",
                        informationURI = OPENTAINT-IR_INFORMATION_URI,
                    )
                ),
                results = vulnerabilities.map { instance ->
                    Result(
                        ruleID = instance.description.ruleId,
                        message = Message(
                            text = instance.description.message
                        ),
                        level = instance.description.level,
                        locations = listOf(instToSarifLocation(instance.traceGraph.sink.statement, sourceFileResolver)),
                        codeFlows = instance.traceGraph
                            .getAllTraces()
                            .take(maxPathsCount)
                            .map { traceToSarifCodeFlow(it, sourceFileResolver, isDeduplicate) }
                            .toList(),
                    )
                }
            )
        )
    )
}

private val JIRMethod.fullyQualifiedName: String
    get() = "${enclosingClass.name}#${name}"

private fun instToSarifLocation(inst: JIRInst, sourceFileResolver: SourceFileResolver): Location {
    val sourceLocation = sourceFileResolver.resolve(inst)
        ?: run {
            val registeredLocation = inst.location.method.declaration.location
            val classFile = inst.location.method.enclosingClass.name
                .replace('.', '/') + ".class"
            File(registeredLocation.path).resolve(classFile).path
        }
    return Location(
        physicalLocation = PhysicalLocation(
            artifactLocation = ArtifactLocation(
                uri = sourceLocation
            ),
            region = Region(
                startLine = inst.location.lineNumber.toLong()
            )
        ),
        logicalLocations = listOf(
            LogicalLocation(
                fullyQualifiedName = inst.location.method.fullyQualifiedName
            )
        )
    )
}

private fun traceToSarifCodeFlow(
    trace: List<Vertex<*>>,
    sourceFileResolver: SourceFileResolver,
    isDeduplicate: Boolean = true,
): CodeFlow {
    return CodeFlow(
        threadFlows = listOf(
            ThreadFlow(
                locations = trace.map {
                    ThreadFlowLocation(
                        location = instToSarifLocation(it.statement, sourceFileResolver),
                        state = mapOf(
                            "fact" to MultiformatMessageString(
                                text = it.fact.toString()
                            )
                        )
                    )
                }.let {
                    if (isDeduplicate) it.deduplicate() else it
                }
            )
        )
    )
}

private fun List<ThreadFlowLocation>.deduplicate(): List<ThreadFlowLocation> {
    if (isEmpty()) return emptyList()

    return listOf(first()) + zipWithNext { a, b ->
        val aLine = a.location!!.physicalLocation!!.region!!.startLine!!
        val bLine = b.location!!.physicalLocation!!.region!!.startLine!!
        if (aLine != bLine) {
            b
        } else {
            null
        }
    }.filterNotNull()
}
