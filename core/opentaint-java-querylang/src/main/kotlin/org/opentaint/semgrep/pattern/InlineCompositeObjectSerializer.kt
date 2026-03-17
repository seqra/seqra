package org.opentaint.semgrep.pattern

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.ClassSerialDescriptorBuilder
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.CompositeDecoder
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import java.util.Optional

abstract class InlineCompositeObjectSerializer<T, O>(
    name: String,
    private val objSerializer: KSerializer<O>,
    private val inlinedFields: List<Pair<String, KSerializer<*>>>,
) : KSerializer<T> {
    override val descriptor: SerialDescriptor by lazy {
        buildClassSerialDescriptor(name) {
            inlinedFields.forEach { (fieldName, fieldSerializer) ->
                element(fieldName, fieldSerializer.descriptor)
            }
            inlineElements(objSerializer.descriptor)
        }
    }

    abstract fun deserialize(obj: O, fields: Map<String, Optional<Any>>): T

    private val inlinedFieldsSerializers by lazy { inlinedFields.map { it.second } }

    override fun deserialize(decoder: Decoder): T {
        val fullDecoder = InlinedClassDecoder(decoder, descriptor, inlinedFieldsSerializers)
        val obj = objSerializer.deserialize(fullDecoder)

        val fieldValues = hashMapOf<String, Optional<Any>>()
        inlinedFields.forEachIndexed { idx, (fieldName, _) ->
            val value = fullDecoder.decodedFields[idx] ?: return@forEachIndexed
            fieldValues[fieldName] = value
        }

        return deserialize(obj, fieldValues)
    }

    override fun serialize(encoder: Encoder, value: T) {
        TODO("Not yet implemented")
    }

    @OptIn(ExperimentalSerializationApi::class)
    private fun ClassSerialDescriptorBuilder.inlineElements(descriptor: SerialDescriptor) = with(descriptor) {
        for (i in 0 until elementsCount) {
            element(getElementName(i), getElementDescriptor(i), getElementAnnotations(i), isElementOptional(i))
        }
    }
}

private class InlinedClassDecoder(
    val decoder: Decoder,
    val fullDescriptor: SerialDescriptor,
    val fieldSerializers: List<KSerializer<*>>
) : Decoder by decoder {
    val decodedFields = hashMapOf<Int, Optional<Any>>()

    override fun beginStructure(descriptor: SerialDescriptor): CompositeDecoder =
        Composite(decoder.beginStructure(fullDescriptor))

    private inner class Composite(val composite: CompositeDecoder) : CompositeDecoder by composite {
        override fun decodeElementIndex(descriptor: SerialDescriptor): Int {
            while (true) {
                val idx = composite.decodeElementIndex(fullDescriptor)
                if (idx == -1) return idx

                if (idx in fieldSerializers.indices) {
                    val value = composite.decodeSerializableElement(fullDescriptor, idx, fieldSerializers[idx])
                    decodedFields[idx] = Optional.ofNullable(value)
                    continue
                }

                return idx - fieldSerializers.size
            }
        }
    }
}
