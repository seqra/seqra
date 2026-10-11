package org.opentaint.jvm.sast.dataflow

/** Additive variants. Existing classes keep AnalysisTest.useBidiEntry=false.
 *  Only their class declarations are open for these inherited variants. */
class BidiTreeCleanerFieldSensitivityAnalysisTest : CleanerFieldSensitivityAnalysisTest() {
    override val useBidiEntry = true
    override val bidiLimits = (1..9).toList()
}
class BidiTreeDeepCleanSummaryAnalysisTest : DeepCleanSummaryAnalysisTest() {
    override val useBidiEntry = true
    override val bidiLimits = (1..9).toList()
}
class BidiTreeCleanerDslAnalysisTest : CleanerDslAnalysisTest() {
    override val useBidiEntry = true
    override val bidiLimits = (1..9).toList()
}
class BidiTreeCleanerDslControlFlowAnalysisTest : CleanerDslControlFlowAnalysisTest() {
    override val useBidiEntry = true
    override val bidiLimits = (1..9).toList()
}
class BidiTreeAnyFieldPrimitiveAnalysisTest : AnyFieldPrimitiveAnalysisTest() {
    override val useBidiEntry = true
}

/* Reachability and return tests also retain their original default configurations. */
class BidiJavaDataFlowReachabilityTest : JavaDataFlowReachabilityTest() {
    override val useBidiEntry = true
    override val bidiLimits = (1..9).toList()
}
class BidiKotlinDataFlowReachabilityTest : KotlinDataFlowReachabilityTest() {
    override val useBidiEntry = true
    override val bidiLimits = (1..9).toList()
}
class BidiMultiReturnDataFlowTest : MultiReturnDataFlowTest() {
    override val useBidiEntry = true
    override val bidiLimits = (1..9).toList()
}
