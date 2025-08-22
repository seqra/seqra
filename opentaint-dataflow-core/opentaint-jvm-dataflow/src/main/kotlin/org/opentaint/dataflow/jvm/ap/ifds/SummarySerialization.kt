package org.opentaint.dataflow.jvm.ap.ifds

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import org.opentaint.ir.api.jvm.cfg.JIRInst
import org.opentaint.dataflow.jvm.ap.ifds.access.FinalFactAp
import org.opentaint.dataflow.jvm.ap.ifds.access.InitialFactAp
import org.opentaint.dataflow.jvm.ap.ifds.access.tree.AccessPath
import org.opentaint.dataflow.jvm.ap.ifds.access.tree.AccessTree
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
            initialStatement = methodEntryPoint.statement.repr(),
            exitStatement = statement.repr(),
            initialFact = null,
            exitFact = null
        )

        is Edge.ZeroToFact -> EdgeRepresentation(
            initialStatement = methodEntryPoint.statement.repr(),
            exitStatement = statement.repr(),
            initialFact = null,
            exitFact = factAp.repr()
        )

        is Edge.FactToFact -> EdgeRepresentation(
            initialStatement = methodEntryPoint.statement.repr(),
            exitStatement = statement.repr(),
            initialFact = initialFactAp.repr(),
            exitFact = factAp.repr()
        )
    }

    private fun FinalFactAp.repr(): FactRepresentation {
        val apRepr = mutableListOf<List<String>>()
        this as? AccessTree ?: TODO("Serialization is not supported for $this")
        access.forEachPath { apRepr.add(it.repr()) }
        return FactRepresentation(base.toString(), apRepr, exclusions.repr())
    }

    private fun InitialFactAp.repr(): FactRepresentation {
        this as? AccessPath ?: TODO("Serialization is not supported for $this")
        val apRepr = access?.let { listOf(it.repr()) }
        return FactRepresentation(base.toString(), apRepr, exclusions.repr())
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
