package org.opentaint.jvm.sast.dataflow

import org.opentaint.ir.api.common.CommonMethod
import org.opentaint.dataflow.ap.ifds.Accessor
import org.opentaint.dataflow.ap.ifds.serialization.SummarySerializationContext

object DummySerializationContext : SummarySerializationContext {
    override fun getIdByMethod(method: CommonMethod) = error("Should not be called")

    override fun getIdByAccessor(accessor: Accessor) = error("Should not be called")

    override fun getMethodById(id: Long) = error("Should not be called")

    override fun getAccessorById(id: Long) = error("Should not be called")

    override fun loadSummaries(method: CommonMethod): ByteArray? = null

    override fun storeSummaries(method: CommonMethod, summaries: ByteArray) = error("Should not be called")

    override fun flush() = error("Should not be called")

}