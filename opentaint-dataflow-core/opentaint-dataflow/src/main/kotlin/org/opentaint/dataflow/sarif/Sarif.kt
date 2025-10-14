/*
 *  Copyright 2022 Opentaint contributors (opentaint.dev)
 * <p>
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 * <p>
 *  http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package org.opentaint.dataflow.sarif

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
import org.opentaint.ir.api.common.cfg.CommonInst
import org.opentaint.dataflow.ifds.Vertex
import org.opentaint.dataflow.util.Traits

private const val SARIF_SCHEMA =
    "https://raw.githubusercontent.com/oasis-tcs/sarif-spec/master/Schemata/sarif-schema-2.1.0.json"
private const val OPENTAINT_IR_INFORMATION_URI =
    "https://github.com/Opentaint/opentaint-ir/blob/develop/opentaint-ir-analysis/README.md"
private const val DEFAULT_PATH_COUNT = 3

fun <Statement : CommonInst> sarifReportFromVulnerabilities(
    traits: Traits<*, Statement>,
    vulnerabilities: List<VulnerabilityInstance<*, Statement>>,
    maxPathsCount: Int = DEFAULT_PATH_COUNT,
    isDeduplicate: Boolean = true,
    sourceFileResolver: SourceFileResolver<Statement> = SourceFileResolver { null },
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
                        informationURI = OPENTAINT_IR_INFORMATION_URI,
                    )
                ),
                results = vulnerabilities.map { instance ->
                    Result(
                        ruleID = instance.description.ruleId,
                        message = Message(
                            text = instance.description.message
                        ),
                        level = instance.description.level,
                        locations = listOfNotNull(
                            instToSarifLocation(
                                traits,
                                instance.traceGraph.sink.statement,
                                sourceFileResolver
                            )
                        ),
                        codeFlows = instance.traceGraph
                            .getAllTraces()
                            .take(maxPathsCount)
                            .map { traceToSarifCodeFlow(traits, it, sourceFileResolver, isDeduplicate) }
                            .toList(),
                    )
                }
            )
        )
    )
}

fun <Statement : CommonInst> instToSarifLocation(
    traits: Traits<*, Statement>,
    inst: Statement,
    sourceFileResolver: SourceFileResolver<Statement>,
): Location = with(traits) {
    val sourceLocation = sourceFileResolver.resolve(inst)
    return Location(
        physicalLocation = sourceLocation?.let {
            PhysicalLocation(
                artifactLocation = ArtifactLocation(uri = it),
                region = Region(
                    startLine = lineNumber(inst).toLong()
                )
            )
        },
        logicalLocations = listOf(
            LogicalLocation(
                fullyQualifiedName = locationFQN(inst),
                decoratedName = locationMachineName(inst)
            )
        )
    )
}

private fun <Statement : CommonInst> traceToSarifCodeFlow(
    traits: Traits<*, Statement>,
    trace: List<Vertex<*, Statement>>,
    sourceFileResolver: SourceFileResolver<Statement>,
    isDeduplicate: Boolean = true,
): CodeFlow {
    return CodeFlow(
        threadFlows = listOf(
            ThreadFlow(
                locations = trace.map {
                    ThreadFlowLocation(
                        location = instToSarifLocation(traits, it.statement, sourceFileResolver),
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
        val aLine = a.location?.physicalLocation?.region?.startLine
        val bLine = b.location?.physicalLocation?.region?.startLine
        if (aLine != bLine) {
            b
        } else {
            null
        }
    }.filterNotNull()
}
