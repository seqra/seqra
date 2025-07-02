package org.opentaint.dataflow.jvm.ap.ifds

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import org.opentaint.ir.api.jvm.cfg.JIRInst
import org.opentaint.ir.taint.configuration.TaintMark
import org.opentaint.dataflow.jvm.util.SequenceSerializer

class EdgeSerializer : KSerializer<Edge> {
    private val edgeReprSerializer = EdgeRepresentation.serializer()

    override val descriptor: SerialDescriptor get() = edgeReprSerializer.descriptor

    override fun serialize(encoder: Encoder, value: Edge) {
        val repr = value.repr()
        edgeReprSerializer.serialize(encoder, repr)
    }

    override fun deserialize(decoder: Decoder): Edge {
        error("Deserialization is not supported")
    }

    private fun JIRInst.repr(): String = "$this"

    private fun Edge.repr(): EdgeRepresentation = when (this) {
        is Edge.ZeroToZero -> EdgeRepresentation(
            initialStatement = initialStatement.repr(),
            exitStatement = statement.repr(),
            initialFact = null,
            exitFact = null
        )

        is Edge.ZeroToFact -> EdgeRepresentation(
            initialStatement = initialStatement.repr(),
            exitStatement = statement.repr(),
            initialFact = null,
            exitFact = fact.repr()
        )

        is Edge.FactToFact -> EdgeRepresentation(
            initialStatement = initialStatement.repr(),
            exitStatement = statement.repr(),
            initialFact = initialFact.repr(),
            exitFact = fact.repr()
        )
    }

    private fun Fact.TaintedTree.repr(): FactRepresentation {
        val apRepr = mutableListOf<List<String>>()
        ap.access.forEachPath { apRepr.add(it.repr()) }
        return FactRepresentation(mark, ap.base.toString(), apRepr, ap.exclusions.repr())
    }

    private fun Fact.TaintedPath.repr(): FactRepresentation {
        val apRepr = ap.access?.let { listOf(it.repr()) }
        return FactRepresentation(mark, ap.base.toString(), apRepr, ap.exclusions.repr())
    }

    private fun ExclusionSet.repr(): List<String>? = when (this) {
        ExclusionSet.Universe -> null
        ExclusionSet.Empty -> emptyList()
        is ExclusionSet.Concrete -> set.repr()
    }

    private fun Iterable<Accessor>.repr(): List<String> = map {
        it.toString()
    }

    @Serializable
    data class EdgeRepresentation(
        val initialStatement: String,
        val exitStatement: String,
        val initialFact: FactRepresentation?,
        val exitFact: FactRepresentation?
    )

    @Serializable
    data class FactRepresentation(
        val mark: TaintMark,
        val base: String,
        val ap: List<List<String>>?,
        val exclusion: List<String>?
    )
}

class EdgeSequenceSerializer : KSerializer<Sequence<Edge>> {
    private val serializer = SequenceSerializer(EdgeSerializer())
    override val descriptor: SerialDescriptor get() = serializer.descriptor
    override fun deserialize(decoder: Decoder): Sequence<Edge> = serializer.deserialize(decoder)
    override fun serialize(encoder: Encoder, value: Sequence<Edge>) {
        serializer.serialize(encoder, value)
    }
}

@Serializable
data class ClassSummaries(
    val className: String,
    val summaries: List<MethodSummaries>
)

@Serializable
data class MethodSummaries(
    val method: String,
    @Serializable(with = EdgeSequenceSerializer::class)
    val summaries: Sequence<Edge>
)
