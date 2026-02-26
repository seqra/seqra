package org.opentaint.jvm.sast.sarif

import io.github.detekt.sarif4k.Message
import io.github.detekt.sarif4k.Result
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToStream
import org.opentaint.dataflow.ap.ifds.access.FinalFactAp
import org.opentaint.ir.api.common.CommonMethod
import org.opentaint.ir.api.common.cfg.CommonInst
import org.opentaint.jvm.sast.JIRSourceFileResolver
import org.opentaint.jvm.sast.ast.AstSpanResolverProvider
import org.opentaint.jvm.sast.project.SarifGenerationOptions
import java.io.OutputStream

class DebugFactReachabilitySarifGenerator(
    private val options: SarifGenerationOptions,
    sourceFileResolver: JIRSourceFileResolver,
    private val traits: SarifTraits<CommonMethod, CommonInst>,
) {
    private val spanResolver = AstSpanResolverProvider(traits as JIRSarifTraits)
    private val locationResolver = LocationResolver(sourceFileResolver, traits, spanResolver)

    private val json = Json {
        prettyPrint = true
    }

    @OptIn(ExperimentalSerializationApi::class)
    fun generateSarif(
        output: OutputStream,
        facts: Map<CommonInst, Set<FinalFactAp>>,
    ) {
        val locations = generateFactLocations(facts)
        val resolvedLocations = locationResolver.resolve(locations)

        val results = resolvedLocations.asSequence().mapIndexedNotNull { index, location ->
            val loc = location.location ?: return@mapIndexedNotNull null
            val ruleId = "s_$index"
            Result(ruleID = ruleId, message = Message(text = loc.message?.text), locations = listOf(loc))
        }

        val run = LazyToolRunReport(
            tool = generateSarifAnalyzerToolDescription(metadatas = emptyList(), options),
            results = results,
        )

        val sarifReport = LazySarifReport.fromRuns(listOf(run))
        json.encodeToStream(sarifReport, output)
    }

    private fun generateFactLocations(statementFacts: Map<CommonInst, Set<FinalFactAp>>): List<IntermediateLocation> {
        val result = mutableListOf<IntermediateLocation>()

        for ((stmt, facts) in statementFacts) {
            result += IntermediateLocation(
                inst = stmt,
                info = getInstructionInfo(stmt),
                kind = "unknown",
                message = "$facts",
                type = LocationType.Simple
            )
        }

        return result
    }

    private fun getInstructionInfo(statement: CommonInst): InstructionInfo = with(traits) {
        InstructionInfo(
            fullyQualified = locationFQN(statement),
            machineName = locationMachineName(statement),
            lineNumber = lineNumber(statement),
        )
    }
}
