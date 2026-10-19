package org.opentaint.dataflow.bidi.fixtures

import org.opentaint.dataflow.bidi.ap.*
import org.opentaint.dataflow.util.Cancellation
import org.opentaint.dataflow.util.RefManager

data class TestMark(val name: String, val owner: MarkGroupKey = MarkGroupKey.Model(name))

/** The same name/owner uniformity contract as production, without a JVM dependency. */
fun testDeclarations(vararg marks: TestMark): MarkDeclarations {
    val keys = linkedMapOf<String, FullKey>()
    val owners = linkedMapOf<String, MarkGroupKey>()
    val flags = linkedMapOf<MarkGroupKey, Boolean>()
    for ((name, owner) in marks) {
        val previousOwner = owners.putIfAbsent(name, owner)
        require(previousOwner == null || previousOwner == owner) { "Conflicting owners for $name" }
        val primitive = name.endsWith("%%primitive%%")
        val previousFlag = flags.putIfAbsent(owner, primitive)
        require(previousFlag == null || previousFlag == primitive) { "Mixed primitive tracking: $owner" }
        keys[name] = FullKey(encodeGroup(owner, primitive), name)
    }
    return FrozenMarkDeclarations(keys)
}

fun fixtureMarkDeclarations(names: Iterable<String>): MarkDeclarations =
    testDeclarations(*names.map(::TestMark).toTypedArray())

/** Finite symbolic namespace shared by the existing common-IR fixtures.
 * Names are declared here before any AP manager is created; no handles are interned.
 * Tests with other names or shared owners pass their own declaration snapshot. */
val COMMON_FIXTURE_MARK_NAMES: Set<String> = buildSet {
    addAll(listOf(
        "A", "AP5 T", "Absent", "B", "C", "C T", "CONJ T", "CONJ T1", "DLINK T",
        "END T", "End", "F76 T", "F76 U", "Fresh", "G", "G T", "H", "I T", "Out",
        "S T", "STATE", "T", "U", "V", "W", "WRAP T", "absent", "callee source",
        "cleaned T", "confirmed T", "delay signal", "end", "end T", "end U", "end V",
        "entry", "evaluated", "excluded-T", "extra", "factory T", "flow bootstrap T",
        "follow", "fresh", "fresh mark", "fresh-mark", "full T", "ignored end",
        "incoming exclusion", "input", "joint T", "joint U", "next", "nonzero",
        "normalization T", "old factory T", "other", "other mark", "other;V",
        "other;state;v;3", "outer#left;T", "outer#left;state;v;1", "outer#right;U",
        "outer#right;state;v;2", "output", "partial T", "pass T", "persistent T",
        "record exclusion", "refining T", "request", "requirement", "restriction T",
        "result", "retained", "returned", "rule#with#literal-hash;classifier;v;0",
        "second", "seed", "shared T", "sink", "source", "source T", "source U",
        "source versus pass T", "spatial G", "spatial H", "summary-only T", "trigger",
        "unrelated", "unrelated exit", "worker T", "wrong", "CONJ T2", "normalization U",
        "shared U", "partial U", "END U", "factory U", "pass U", "exit-source", "end-fact",
        "normal", "exceptional", "first", "queued",
    ))
    // Existing bounded concurrency, predicate and request tests use these ranges.
    repeat(128) { i -> add("T$i"); add("U$i"); add("M$i"); add("mark-$i") }
}

val COMMON_FIXTURE_DECLARATIONS: MarkDeclarations = fixtureMarkDeclarations(COMMON_FIXTURE_MARK_NAMES)

fun testApManager(cancellation: Cancellation, refManager: RefManager = RefManager(),
                  declarations: MarkDeclarations = COMMON_FIXTURE_DECLARATIONS): ApManager =
    ApManager(cancellation, refManager, MarkTable(declarations))

/** Test-owned enumeration from supplied names, never a runtime catalog inventory. */
fun fixtureGroups(marks: MarkTable, fullNames: Iterable<String>): List<TaintMark> =
    fullNames.map { marks.group(marks.groupOf(marks.full(it))) }.distinct()
