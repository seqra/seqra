package org.opentaint.go.sast.dataflow

import org.junit.jupiter.api.TestInstance
import kotlin.test.Test

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PointerHeapTest : AnalysisTest() {

    // Basic pointers
    @Test fun pointer001T() = assertReachable("test.pointer001T")
    @Test fun pointer002F() = assertNotReachable("test.pointer002F")

    // Heap-allocated via new
    @Test fun heapNew001T() = assertReachable("test.heapNew001T")
    @Test fun heapNew002F() = assertNotReachable("test.heapNew002F")

    // Heap escape from function
    @Test fun heapEscape001T() = assertReachable("test.heapEscape001T")
    @Test fun heapEscape002F() = assertNotReachable("test.heapEscape002F")

    // Pointer passed as argument
    @Test fun ptrArg001T() = assertReachable("test.ptrArg001T")
    @Test fun ptrArg002F() = assertNotReachable("test.ptrArg002F")

    // Double pointer
    @Test fun ptrToPtr001T() = assertReachable("test.ptrToPtr001T")

    // Slice of pointers
    @Test fun sliceOfPtr001T() = assertReachable("test.sliceOfPtr001T")
    @Test fun sliceOfPtr002F() = assertNotReachable("test.sliceOfPtr002F")

    // Map of pointers
    @Test fun mapOfPtr001T() = assertReachable("test.mapOfPtr001T")
    @Test fun mapOfPtr002F() = assertNotReachable("test.mapOfPtr002F")
}
