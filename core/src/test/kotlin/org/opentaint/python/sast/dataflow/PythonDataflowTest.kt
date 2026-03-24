package org.opentaint.python.sast.dataflow

import org.junit.jupiter.api.TestInstance
import kotlin.test.Test

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PythonDataflowTest : AnalysisTest() {

    @Test
    fun testSimpleSample() {
        val entryPoint = cp.findFunctionOrNull("Sample.sample")
            ?: error("Entry point not found")


    }
}
