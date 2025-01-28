package org.opentaint.ir.analysis.impl

import org.opentaint.ir.analysis.AnalysisMain
import org.junit.jupiter.api.Test

class CliTest {
    @Test
    fun `test basic analysis cli api`() {
        val args = listOf(
            "-a", CliTest::class.java.getResource("/config.json")?.file ?: error("Can't find file with config"),
            "-c", "tmp-analysis-db",
            "-s", "org.opentaint.ir.analysis.samples.NPEExamples"
        )
        AnalysisMain().run(args)
    }
}