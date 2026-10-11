package org.opentaint.dataflow.bidi.fixtures

/** Test values use names, not manager ids. An adapter must retain every annotation. */
sealed interface FixtureAccessor {
    data class Field(val owner: String, val name: String, val type: String) : FixtureAccessor
    data class Class(val name: String) : FixtureAccessor
    data object Element : FixtureAccessor
}

sealed interface FixtureBase {
    data class Local(val name: String) : FixtureBase
    data class Argument(val index: Int) : FixtureBase
    data object Static : FixtureBase
    data object Zero : FixtureBase
}

enum class FixtureTail { EXACT, STAR, MAY, MUST }
enum class FixtureLayer { NORMAL, DEMAND }
enum class FixtureDirection { FORWARD, BACKWARD }

sealed interface FixtureMark {
    data class Concrete(val name: String) : FixtureMark
    data class Abstract(val excluded: Set<String> = emptySet()) : FixtureMark
}

/** A raw reference pattern can represent test input that a typed factory must reject. */
data class FixturePattern(
    val base: FixtureBase,
    val path: List<FixtureAccessor>,
    val tail: FixtureTail,
    val mark: FixtureMark,
    val fieldExclusion: Set<FixtureAccessor> = emptySet(),
)

/** Full raw output identity includes the layer as well as both exclusion annotations. */
data class FixtureFact(val pattern: FixturePattern, val layer: FixtureLayer)

/** The forward entry remains in the key when a test runs backward. */
data class FixtureMethodKey(val method: String, val context: String, val forwardEntry: String)

sealed interface FixtureRequestKind {
    data class Mark(val mark: String) : FixtureRequestKind
    data class Position(val path: List<FixtureAccessor>) : FixtureRequestKind
}

data class FixtureRequest(
    val method: FixtureMethodKey,
    val premise: FixturePattern,
    val kind: FixtureRequestKind,
)

/** Operation outputs are a multiset; requests have their own exact keys. */
data class FixtureResult(val facts: List<FixtureFact>, val requests: Set<FixtureRequest> = emptySet()) {
    fun factCounts(): Map<FixtureFact, Int> = facts.groupingBy { it }.eachCount()
}

data class FixtureSummary(val method: FixtureMethodKey, val premises: Set<FixturePattern>, val conclusion: FixtureFact)
data class FixtureDemand(val method: FixtureMethodKey, val entry: FixturePattern, val exit: FixturePattern?)

data class FixtureCaller(
    val method: FixtureMethodKey,
    val premises: Set<FixturePattern>,
    val layer: FixtureLayer,
    val call: String,
)

data class FixtureLink(
    val callee: FixtureMethodKey,
    val caller: FixtureCaller,
    val linkLayer: FixtureLayer,
    val added: FixtureFact,
)

enum class FixtureFormKind { STATEMENT, CALL, ENTRY, EXIT }

/** Stable objects provide identity keys for test joins and rule alternatives. */
class FixtureForm internal constructor(
    val method: FixtureMethodKey,
    val kind: FixtureFormKind,
    val statement: String,
    val direction: FixtureDirection,
    val alternative: String,
)

/** S00 harness cache only. It supplies stable test inputs, not a production-cache oracle. */
class StableFixtureForms {
    private data class Slot(
        val method: String,
        val context: String?,
        val entry: String?,
        val kind: FixtureFormKind,
        val statement: String,
        val direction: FixtureDirection,
        val alternative: String,
    )

    private val forms = HashMap<Slot, FixtureForm>()

    fun get(
        method: FixtureMethodKey,
        kind: FixtureFormKind,
        statement: String,
        direction: FixtureDirection = FixtureDirection.FORWARD,
        alternative: String = "default",
    ): FixtureForm {
        val contextual = kind == FixtureFormKind.CALL || kind == FixtureFormKind.ENTRY
        val slot = Slot(method.method, method.context.takeIf { contextual }, method.forwardEntry.takeIf { contextual },
            kind, statement, direction, alternative)
        return forms.getOrPut(slot) {
            FixtureForm(method, kind, statement, direction, alternative)
        }
    }
}

/** Small annotated vectors. Their expected values are data, with no production operation calls. */
object BidiFixtures {
    val f = FixtureAccessor.Field("Box", "f", "Box")
    val g = FixtureAccessor.Field("Box", "g", "Box")
    val freshField = FixtureAccessor.Field("Box", "fresh", "Box")
    val classAccessor = FixtureAccessor.Class("Box")
    val x = FixtureBase.Local("x")
    val y = FixtureBase.Local("y")
    const val T = "taint-T"
    const val U = "taint-U"
    const val FRESH_MARK = "fresh-mark"

    val method = FixtureMethodKey("Box.pass(Box):Box", "context-A", "entry-0")
    val otherContext = method.copy(context = "context-B")
    val otherEntry = method.copy(forwardEntry = "entry-1")

    val zero = FixturePattern(FixtureBase.Zero, emptyList(), FixtureTail.EXACT, FixtureMark.Concrete("<zero>"))
    val flowPremise = FixturePattern(x, emptyList(), FixtureTail.STAR, FixtureMark.Abstract())
    val annotatedFlow = FixtureFact(
        FixturePattern(x, listOf(f), FixtureTail.STAR, FixtureMark.Abstract(setOf(T)), setOf(g)),
        FixtureLayer.NORMAL,
    )
    val must = FixtureFact(
        FixturePattern(y, listOf(f), FixtureTail.MUST, FixtureMark.Concrete(T), setOf(g)),
        FixtureLayer.NORMAL,
    )
    val may = FixtureFact(must.pattern.copy(tail = FixtureTail.MAY, fieldExclusion = emptySet()), FixtureLayer.DEMAND)

    // AP W8: a demoted must fact has Empty E. A selected normal piece retains NORMAL until hand-off.
    val mustDemotion = FixtureResult(listOf(may))
    // This arbitrary-pattern normalization vector does not assert native-summary selection.
    val normalizedFlowHandOff = FixtureDemand(
        method,
        annotatedFlow.pattern.copy(mark = FixtureMark.Abstract()),
        flowPremise,
    )

    // AP F75: selected-mark cleaning adds X and requests even when T is already excluded.
    val sameBaseCleanerInput = annotatedFlow.copy(pattern = annotatedFlow.pattern.copy(mark = FixtureMark.Abstract(setOf(U))))
    val sameBaseCleanerExpected = FixtureResult(
        listOf(sameBaseCleanerInput.copy(pattern = sameBaseCleanerInput.pattern.copy(mark = FixtureMark.Abstract(setOf(T, U))))),
        setOf(FixtureRequest(method, flowPremise, FixtureRequestKind.Mark(T))),
    )
    val alreadyExcludedCleanerExpected = FixtureResult(
        listOf(annotatedFlow),
        setOf(FixtureRequest(method, flowPremise, FixtureRequestKind.Mark(T))),
    )
    val staticPositionRequest = FixtureRequest(
        method,
        flowPremise,
        FixtureRequestKind.Position(listOf(classAccessor, f)),
    )

    // CurrentConjunction: selection does not change a normal multi-premise conclusion.
    val a = FixturePattern(FixtureBase.Argument(0), emptyList(), FixtureTail.EXACT, FixtureMark.Concrete(T))
    val b = FixturePattern(FixtureBase.Argument(1), emptyList(), FixtureTail.EXACT, FixtureMark.Concrete(U))
    val c = FixturePattern(y, emptyList(), FixtureTail.EXACT, FixtureMark.Concrete(T))
    val normalConjunction = FixtureSummary(method, setOf(a, b), FixtureFact(c, FixtureLayer.NORMAL))
    val conjunctionHandOff = setOf(FixtureDemand(method, c, a), FixtureDemand(method, c, b))
    val normalMustConjunction = FixtureSummary(method, setOf(a, b), must)
    val selectedMustHandOff = setOf(FixtureDemand(method, may.pattern, a), FixtureDemand(method, may.pattern, b))
    val reversedConjunctionBranches = FixtureResult(
        listOf(FixtureFact(a, FixtureLayer.DEMAND), FixtureFact(b, FixtureLayer.DEMAND)),
    )

    // F76: the reader's concrete STAR/E premise is transient; its exact target drops the suffix.
    val nativeInput = a.copy(mark = FixtureMark.Concrete(U))
    val exactToMustRecord = FixtureSummary(method, setOf(nativeInput), must)
    val nativeReadPremise = must.pattern.copy(tail = FixtureTail.STAR)
    val nativeReadTarget = FixtureFact(nativeInput, FixtureLayer.NORMAL)
    val nativeReadIncoming = FixtureFact(
        must.pattern.copy(path = listOf(f, freshField), tail = FixtureTail.EXACT, fieldExclusion = emptySet()),
        FixtureLayer.NORMAL,
    )
    val nativeReadExpected = FixtureResult(listOf(nativeReadTarget))
}
