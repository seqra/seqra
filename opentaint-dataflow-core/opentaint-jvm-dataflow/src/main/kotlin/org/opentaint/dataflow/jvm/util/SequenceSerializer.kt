package org.opentaint.dataflow.jvm.util

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.SerialKind
import kotlinx.serialization.descriptors.StructureKind
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

class SequenceSerializer<T>(
    private val elementSerializer: KSerializer<T>
) : KSerializer<Sequence<T>> {
    override val descriptor: SerialDescriptor = Descriptor(elementSerializer.descriptor)

    override fun deserialize(decoder: Decoder): Sequence<T> {
        error("Deserialization is not supported")
    }

    override fun serialize(encoder: Encoder, value: Sequence<T>) {
        val sequenceStruct = encoder.beginStructure(descriptor)

        for ((i, element) in value.withIndex()) {
            sequenceStruct.encodeSerializableElement(descriptor, i, elementSerializer, element)
        }

        sequenceStruct.endStructure(descriptor)
    }

    @OptIn(ExperimentalSerializationApi::class)
    private class Descriptor(private val elementDescriptor: SerialDescriptor) : SerialDescriptor {
        override val kind: SerialKind get() = StructureKind.LIST
        override val serialName: String get() = "kotlin.Sequence"
        override val elementsCount: Int = 1

        override fun getElementName(index: Int): String = index.toString()
        override fun getElementIndex(name: String): Int =
            name.toIntOrNull() ?: throw IllegalArgumentException("$name is not a valid list index")

        override fun isElementOptional(index: Int): Boolean {
            require(index >= 0) { "Illegal index $index, $serialName expects only non-negative indices" }
            return false
        }

        override fun getElementAnnotations(index: Int): List<Annotation> {
            require(index >= 0) { "Illegal index $index, $serialName expects only non-negative indices" }
            return emptyList()
        }

        override fun getElementDescriptor(index: Int): SerialDescriptor {
            require(index >= 0) { "Illegal index $index, $serialName expects only non-negative indices" }
            return elementDescriptor
        }

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is Descriptor) return false
            if (elementDescriptor == other.elementDescriptor && serialName == other.serialName) return true
            return false
        }

        override fun hashCode(): Int {
            return elementDescriptor.hashCode() * 31 + serialName.hashCode()
        }

        override fun toString(): String = "$serialName($elementDescriptor)"
    }
}
