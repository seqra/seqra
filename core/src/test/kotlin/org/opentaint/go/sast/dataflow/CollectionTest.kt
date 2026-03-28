package org.opentaint.go.sast.dataflow

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.TestInstance
import kotlin.test.Test

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CollectionTest : AnalysisTest() {

    // Slice element tests
    @Test fun sliceElem001T() = assertReachable("test.sliceElem001T")
    @Test fun sliceElem002T() = assertReachable("test.sliceElem002T")
    @Test fun sliceLiteral001T() = assertReachable("test.sliceLiteral001T")
    @Test fun sliceCopy001T() = assertReachable("test.sliceCopy001T")
    @Test fun slicePassToFunc001T() = assertReachable("test.slicePassToFunc001T")
    @Test fun sliceReturnElem001T() = assertReachable("test.sliceReturnElem001T")
    // Conservative: element-level uses weak update (key-insensitive), so overwrite doesn't kill
    @Test fun sliceOverwrite001F() = assertReachable("test.sliceOverwrite001F")

    // Map element tests
    @Test fun mapElem001T() = assertReachable("test.mapElem001T")
    @Test fun mapElem002T() = assertReachable("test.mapElem002T")
    @Test fun mapLiteral001T() = assertReachable("test.mapLiteral001T")
    @Test fun mapPassToFunc001T() = assertReachable("test.mapPassToFunc001T")
    @Test fun mapReturnElem001T() = assertReachable("test.mapReturnElem001T")

    // Array element tests
    @Test fun arrayElem001T() = assertReachable("test.arrayElem001T")
    @Test fun arrayElem002T() = assertReachable("test.arrayElem002T")
    @Test fun arrayPassToFunc001T() = assertReachable("test.arrayPassToFunc001T")

    // Slice of structs
    // Element access + field access composition: items[0].value needs accessor chain
    @Disabled("Slice-of-structs field composition not yet implemented")
    @Test fun sliceOfStructs001T() = assertReachable("test.sliceOfStructs001T")
    @Test fun sliceOfStructs002F() = assertNotReachable("test.sliceOfStructs002F")
}
