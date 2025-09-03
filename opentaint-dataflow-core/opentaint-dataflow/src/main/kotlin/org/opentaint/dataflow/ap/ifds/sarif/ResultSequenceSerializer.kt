package org.opentaint.dataflow.ap.ifds.sarif

import io.github.detekt.sarif4k.Result
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import org.opentaint.dataflow.util.SequenceSerializer

class ResultSequenceSerializer : KSerializer<Sequence<Result>> {
    private val serializer = SequenceSerializer(Result.serializer())
    override val descriptor: SerialDescriptor get() = serializer.descriptor
    override fun deserialize(decoder: Decoder): Sequence<Result> = serializer.deserialize(decoder)
    override fun serialize(encoder: Encoder, value: Sequence<Result>) {
        serializer.serialize(encoder, value)
    }
}
