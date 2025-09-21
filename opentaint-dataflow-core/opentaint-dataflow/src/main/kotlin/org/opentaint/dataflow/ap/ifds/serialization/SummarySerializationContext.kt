package org.opentaint.dataflow.ap.ifds.serialization

import org.opentaint.ir.api.common.CommonMethod
import org.opentaint.dataflow.ap.ifds.Accessor
import java.util.*

class SummarySerializationContext {
    val serializedAccessors = HashMap<Accessor, Int>()
    val deserializedAccessors: MutableMap<Int, Accessor> = hashMapOf()

    val serializedMethods = HashMap<CommonMethod, Int>()
    val deserializedMethods: MutableMap<Int, CommonMethod> = hashMapOf()

    fun reset() {
        serializedAccessors.clear()
        deserializedAccessors.clear()

        serializedMethods.clear()
        deserializedMethods.clear()
    }
}