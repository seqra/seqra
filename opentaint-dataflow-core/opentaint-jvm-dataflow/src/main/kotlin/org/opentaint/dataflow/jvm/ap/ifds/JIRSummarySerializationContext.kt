package org.opentaint.dataflow.jvm.ap.ifds

import org.opentaint.ir.api.common.CommonMethod
import org.opentaint.ir.api.jvm.JIRClasspath
import org.opentaint.ir.api.jvm.JIRMethod
import org.opentaint.dataflow.ap.ifds.Accessor
import org.opentaint.dataflow.ap.ifds.serialization.SummarySerializationContext

class JIRSummarySerializationContext(private val cp: JIRClasspath): SummarySerializationContext {
    private val jirSummariesFeature = cp.db.features.filterIsInstance<JIRSummariesFeature>().singleOrNull() ?:
        error("Expected exactly one JIRSummariesFeature installed on classpath")

    override fun getIdByMethod(method: CommonMethod): Long {
        jirDowncast<JIRMethod>(method)
        return jirSummariesFeature.getIdByMethod(method)
    }

    override fun getIdByAccessor(accessor: Accessor): Long {
        return jirSummariesFeature.getIdByAccessor(accessor)
    }

    override fun getMethodById(id: Long): JIRMethod {
        return jirSummariesFeature.getMethodById(id, cp)
    }

    override fun getAccessorById(id: Long): Accessor {
        return jirSummariesFeature.getAccessorById(id)
    }

    override fun loadSummaries(method: CommonMethod): ByteArray? {
        jirDowncast<JIRMethod>(method)
        return jirSummariesFeature.loadSummaries(method)
    }

    override fun storeSummaries(method: CommonMethod, summaries: ByteArray) {
        jirDowncast<JIRMethod>(method)
        return jirSummariesFeature.storeSummaries(method, summaries)
    }

    override fun flush() = Unit // No need to do flush because JIRDB will do it automatically
}