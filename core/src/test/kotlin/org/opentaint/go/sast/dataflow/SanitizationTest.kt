package org.opentaint.go.sast.dataflow

import org.junit.jupiter.api.TestInstance
import kotlin.test.Test

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SanitizationTest : AnalysisTest() {

    @Test fun sanitize001F() = assertNotReachable("test.sanitize001F")
    @Test fun sanitize002T() = assertReachable("test.sanitize002T")
    @Test fun sanitize003F() = assertNotReachable("test.sanitize003F")
    @Test fun sanitize004T() = assertReachable("test.sanitize004T")

    @Test fun sanitizeCond001T() = assertReachable("test.sanitizeCond001T")
    @Test fun sanitizeCond002F() = assertNotReachable("test.sanitizeCond002F")

    @Test fun sanitizeLoop001T() = assertReachable("test.sanitizeLoop001T")
    @Test fun sanitizeLoop002F() = assertNotReachable("test.sanitizeLoop002F")

    @Test fun sanitizePartial001T() = assertReachable("test.sanitizePartial001T")
    @Test fun sanitizePartial002F() = assertNotReachable("test.sanitizePartial002F")

    @Test fun sanitizeField001T() = assertReachable("test.sanitizeField001T")
    @Test fun sanitizeField002F() = assertNotReachable("test.sanitizeField002F")
}
