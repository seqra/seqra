package org.opentaint.ir.analysis.sarif

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToStream
import org.opentaint.ir.analysis.engine.IfdsVertex
import org.opentaint.ir.analysis.engine.VulnerabilityInstance
import org.opentaint.ir.api.JIRMethod
import org.opentaint.ir.api.cfg.JIRInst
import java.io.OutputStream
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.Path

@Serializable
data class SarifMessage(val text: String)

@Serializable
data class SarifArtifactLocation(val uri: String)

@Serializable
data class SarifRegion(val startLine: Int)

@Serializable
data class SarifPhysicalLocation(val artifactLocation: SarifArtifactLocation, val region: SarifRegion)

@Serializable
data class SarifLogicalLocation(val fullyQualifiedName: String)

@Serializable
data class SarifLocation(val physicalLocation: SarifPhysicalLocation, val logicalLocations: List<SarifLogicalLocation>) {
    companion object {
        private val JIRMethod.fullyQualifiedName: String
            get() = "${enclosingClass.name}#${name}"

        private val currentPath: Path = Paths.get("").toAbsolutePath()

        fun fromInst(inst: JIRInst): SarifLocation = SarifLocation(
            physicalLocation = SarifPhysicalLocation(
                SarifArtifactLocation("${currentPath.relativize(Path(inst.location.method.declaration.location.path))}"),
                SarifRegion(inst.location.lineNumber)
            ),
            logicalLocations = listOf(
                SarifLogicalLocation(inst.location.method.fullyQualifiedName)
            )
        )
    }
}

@Serializable
data class SarifDomainFact(val text: String)

@Serializable
data class SarifState(val domainFact: SarifDomainFact)

@Serializable
data class SarifThreadFlowLocation(val location: SarifLocation, val state: SarifState)

@Serializable
data class SarifThreadFlow(val locations: List<SarifThreadFlowLocation>)

@Serializable
data class SarifCodeFlow(val threadFlows: List<SarifThreadFlow>) {
    companion object {
        fun fromTrace(trace: List<IfdsVertex>): SarifCodeFlow {
            val threadFlow = trace.map {
                SarifThreadFlowLocation(
                    SarifLocation.fromInst(it.statement),
                    SarifState(SarifDomainFact(it.domainFact.toString()))
                )
            }
            return SarifCodeFlow(listOf(SarifThreadFlow(threadFlow)))
        }
    }
}

@Serializable
data class SarifResult(
    val ruleId: String,
    val message: SarifMessage,
    val level: SarifSeverityLevel,
    val locations: List<SarifLocation>,
    val codeFlows: List<SarifCodeFlow>
) {
    companion object {
        fun fromVulnerabilityInstance(instance: VulnerabilityInstance, maxPathsCount: Int): SarifResult = SarifResult(
            instance.vulnerabilityDescription.ruleId,
            instance.vulnerabilityDescription.message,
            instance.vulnerabilityDescription.level,
            listOf(SarifLocation.fromInst(instance.traceGraph.sink.statement)),
            instance.traceGraph.getAllTraces().take(maxPathsCount).map { SarifCodeFlow.fromTrace(it) }.toList()
        )
    }
}

@Serializable
data class SarifDriver(
    val name: String,
    val version: String,
    val informationUri: String,
)

@Serializable
data class SarifTool(val driver: SarifDriver)

val IfdsTool = SarifTool(
    SarifDriver(
        name = "JaCo-IFDS",
        version = "1.2.0",
        informationUri = "https://github.com/Opentaint/opentaint-ir/blob/develop/opentaint-ir-analysis/README.md"
    )
)

@Serializable
data class SarifRun(val tool: SarifTool, val results: List<SarifResult>)

@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class SarifReport(
    val version: String,

    @SerialName("\$schema")
    val schema: String,

    val runs: List<SarifRun>
) {
    fun encodeToStream(stream: OutputStream) {
        json.encodeToStream(this, stream)
    }

    companion object {
        private const val defaultVersion =
            "2.1.0"
        private const val defaultSchema =
            "https://raw.githubusercontent.com/oasis-tcs/sarif-spec/master/Schemata/sarif-schema-2.1.0.json"

        private const val defaultPathsCount: Int = 3

        private val json = Json {
            prettyPrint = true
            encodeDefaults = false
        }

        fun fromVulnerabilities(
            vulnerabilities: List<VulnerabilityInstance>,
            pathsCount: Int = defaultPathsCount
        ): SarifReport = SarifReport(
            version = defaultVersion,
            schema = defaultSchema,
            runs = listOf(
                SarifRun(
                    IfdsTool,
                    vulnerabilities.map { SarifResult.fromVulnerabilityInstance(it, pathsCount) }
                )
            )
        )
    }
}

@Serializable
enum class SarifSeverityLevel {
    @SerialName("error") ERROR,
    @SerialName("warning") WARNING,
    @SerialName("note") NOTE
}

data class VulnerabilityDescription(
    val message: SarifMessage,
    val ruleId: String,
    val level: SarifSeverityLevel = SarifSeverityLevel.WARNING
)