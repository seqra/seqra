package org.opentaint.ir.analysis.impl

import kotlinx.coroutines.runBlocking
import org.opentaint.ir.impl.features.InMemoryHierarchy
import org.opentaint.ir.impl.features.Usages
import org.opentaint.ir.impl.features.usagesExt
import org.opentaint.ir.testing.BaseTest
import org.opentaint.ir.testing.WithDB
import org.junit.jupiter.api.Test

class AnalysisTest : BaseTest() {
    companion object : WithDB(Usages, InMemoryHierarchy)

    @Test
    fun `analyse something`() = runBlocking {
        val graph = JIRApplicationGraphImpl(cp, cp.usagesExt())
    }
}