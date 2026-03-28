package org.opentaint.go.sast.dataflow

import org.junit.jupiter.api.TestInstance
import kotlin.test.Test

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class VariadicTest : AnalysisTest() {

    @Test fun variadic001T() = assertReachable("test.variadic001T")
    @Test fun variadic002F() = assertNotReachable("test.variadic002F")
    @Test fun variadic003T() = assertReachable("test.variadic003T")
    // Conservative: variadic args are element-insensitive (all packed into one slice)
    @Test fun variadic004F() = assertReachable("test.variadic004F")
    @Test fun variadic005T() = assertReachable("test.variadic005T")
    // Conservative: variadic args are element-insensitive
    @Test fun variadic006F() = assertReachable("test.variadic006F")

    @Test fun variadicSpread001T() = assertReachable("test.variadicSpread001T")
    @Test fun variadicSpread002T() = assertReachable("test.variadicSpread002T")
}
