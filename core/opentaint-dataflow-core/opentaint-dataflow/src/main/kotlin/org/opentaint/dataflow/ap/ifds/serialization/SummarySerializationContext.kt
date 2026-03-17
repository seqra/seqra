package org.opentaint.dataflow.ap.ifds.serialization

import org.opentaint.ir.api.common.CommonMethod
import org.opentaint.dataflow.ap.ifds.Accessor

interface SummarySerializationContext {
    fun getIdByMethod(method: CommonMethod): Long
    fun getIdByAccessor(accessor: Accessor): Long

    fun getMethodById(id: Long): CommonMethod
    fun getAccessorById(id: Long): Accessor

    fun loadSummaries(method: CommonMethod): ByteArray?
    fun storeSummaries(method: CommonMethod, summaries: ByteArray)

    fun flush()
}