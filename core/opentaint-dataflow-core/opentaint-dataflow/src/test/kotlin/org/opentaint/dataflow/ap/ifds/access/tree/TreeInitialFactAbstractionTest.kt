package org.opentaint.dataflow.ap.ifds.access.tree

import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.ap.ifds.AccessPathBase.This
import org.opentaint.dataflow.ap.ifds.Accessor
import org.opentaint.dataflow.ap.ifds.ExclusionSet
import org.opentaint.dataflow.ap.ifds.FactTypeChecker
import org.opentaint.dataflow.ap.ifds.FinalAccessor
import org.opentaint.dataflow.ap.ifds.FieldAccessor
import org.opentaint.dataflow.ap.ifds.TaintMarkAccessor
import org.opentaint.dataflow.ap.ifds.TypeInfoAccessor
import org.opentaint.dataflow.ap.ifds.TypeInfoGroupAccessor
import org.opentaint.dataflow.ap.ifds.ValueAccessor
import org.opentaint.dataflow.ap.ifds.access.AnyAccessorUnrollStrategy
import org.opentaint.dataflow.ap.ifds.access.ApManager
import org.opentaint.dataflow.ap.ifds.access.FinalFactAp
import org.opentaint.dataflow.ap.ifds.access.InitialFactAp
import org.opentaint.ir.api.common.CommonMethod
import org.opentaint.ir.api.common.CommonMethodParameter
import org.opentaint.ir.api.common.CommonTypeName
import org.opentaint.ir.api.common.cfg.CommonInst
import org.opentaint.ir.api.common.cfg.CommonInstLocation
import org.opentaint.ir.api.common.cfg.ControlFlowGraph
import kotlin.test.Test
import kotlin.test.assertTrue

class TreeInitialFactAbstractionTest {
    private companion object {
        const val TYPE_A = "A"
        const val TYPE_B = "B"
        const val TYPE_C = "C"
        const val TYPE_D = "D"

        val FIELD_A_B = FieldAccessor(TYPE_A, "b", TYPE_B)
        val FIELD_B_C = FieldAccessor(TYPE_B, "c", TYPE_C)
        val FIELD_B_E = FieldAccessor(TYPE_B, "e", TYPE_D)
        val FIELD_C_D = FieldAccessor(TYPE_C, "d", TYPE_D)

        val MARK = TaintMarkAccessor("test-mark")
        val MARK_2 = TaintMarkAccessor("test-mark-2")
        val TYPE_INFO_A = TypeInfoAccessor("A")
        val TYPE_INFO_B = TypeInfoAccessor("B")
    }

    private data class Scenario(
        val name: String,
        val analyzed: List<InitialFactAp>,
        val added: FinalFactAp,
        val expectedFacts: List<InitialFactAp> = emptyList(),
        val expectedEmpty: Boolean = false,
    )

    private val apManager: ApManager = TreeApManager(AnyAccessorUnrollStrategy.AnyAccessorDisabled)

    @Test
    fun `scenario matrix`() {
        val scenarios = listOf(
            Scenario(
                name = "1 exclusion hit on c returns a.b.c",
                analyzed = listOf(initialFact(This, FIELD_A_B).exclude(FIELD_B_C)),
                added = finalFact(This, FIELD_A_B, FIELD_B_C, FIELD_C_D),
                expectedFacts = listOf(initialFact(This, FIELD_A_B, FIELD_B_C)),
            ),
            Scenario(
                name = "2 exclusion miss on e returns empty",
                analyzed = listOf(initialFact(This, FIELD_A_B).exclude(FIELD_B_E)),
                added = finalFact(This, FIELD_A_B, FIELD_B_C, FIELD_C_D),
                expectedEmpty = true,
            ),
            Scenario(
                name = "3 analyzed mark no exclusions returns empty",
                analyzed = listOf(initialFact(This, MARK)),
                added = finalFact(This, FIELD_A_B, ValueAccessor, MARK),
                expectedEmpty = true,
            ),
            Scenario(
                name = "4 no analyzed facts for this base returns most abstract",
                analyzed = listOf(initialFact(AccessPathBase.Argument(0), FIELD_A_B).exclude(FIELD_B_C)),
                added = finalFact(This, FIELD_A_B, FIELD_B_C, FIELD_C_D),
                expectedFacts = listOf(initialFact(This)),
            ),
            Scenario(
                name = "5 root exclusion on b returns a.b",
                analyzed = listOf(initialFact(This).exclude(FIELD_A_B)),
                added = finalFact(This, FIELD_A_B, FIELD_B_C),
                expectedFacts = listOf(initialFact(This, FIELD_A_B)),
            ),
            Scenario(
                name = "6 root non matching exclusion returns empty",
                analyzed = listOf(initialFact(This).exclude(FIELD_B_E)),
                added = finalFact(This, FIELD_A_B, FIELD_B_C),
                expectedEmpty = true,
            ),
            Scenario(
                name = "7 deeper exclusion on d currently collapses to a.b",
                analyzed = listOf(initialFact(This, FIELD_A_B, FIELD_B_C).exclude(FIELD_C_D)),
                added = finalFact(This, FIELD_A_B, FIELD_B_C, FIELD_C_D),
                expectedFacts = listOf(initialFact(This, FIELD_A_B)),
            ),
            Scenario(
                name = "8 deeper non matching exclusion currently collapses to a.b",
                analyzed = listOf(initialFact(This, FIELD_A_B, FIELD_B_C).exclude(FIELD_B_E)),
                added = finalFact(This, FIELD_A_B, FIELD_B_C, FIELD_C_D),
                expectedFacts = listOf(initialFact(This, FIELD_A_B)),
            ),
            Scenario(
                name = "9 multiple analyzed paths currently produce no abstraction",
                analyzed = listOf(
                    initialFact(This).exclude(FIELD_A_B),
                    initialFact(This, FIELD_A_B).exclude(FIELD_B_E),
                ),
                added = finalFact(This, FIELD_A_B, FIELD_B_C, FIELD_C_D),
                expectedEmpty = true,
            ),
            Scenario(
                name = "10 most abstract analyzed with empty exclusions returns empty",
                analyzed = listOf(initialFact(This)),
                added = finalFact(This, FIELD_A_B),
                expectedEmpty = true,
            ),
            Scenario(
                name = "11 mark exclusion at root currently returns empty",
                analyzed = listOf(initialFact(This).exclude(MARK)),
                added = finalFact(This, FIELD_A_B, ValueAccessor, MARK),
                expectedEmpty = true,
            ),
            Scenario(
                name = "12 value exclusion after mark currently returns empty",
                analyzed = listOf(initialFact(This, MARK).exclude(ValueAccessor)),
                added = finalFact(This, FIELD_A_B, ValueAccessor, MARK),
                expectedEmpty = true,
            ),
            Scenario(
                name = "13 type group exclusion at root currently returns empty",
                analyzed = listOf(initialFact(This).exclude(TypeInfoGroupAccessor)),
                added = finalFact(This, FIELD_A_B, TYPE_INFO_A, TypeInfoGroupAccessor),
                expectedEmpty = true,
            ),
            Scenario(
                name = "14 type accessor exclusion after group currently returns empty",
                analyzed = listOf(initialFact(This, TypeInfoGroupAccessor).exclude(TYPE_INFO_A)),
                added = finalFact(This, FIELD_A_B, TYPE_INFO_A, TypeInfoGroupAccessor),
                expectedEmpty = true,
            ),
            Scenario(
                name = "15 non matching type accessor exclusion returns empty",
                analyzed = listOf(initialFact(This, TypeInfoGroupAccessor).exclude(TYPE_INFO_B)),
                added = finalFact(This, FIELD_A_B, TYPE_INFO_A, TypeInfoGroupAccessor),
                expectedEmpty = true,
            ),
            Scenario(
                name = "16 mark2 exclusion does not match mark1 returns empty",
                analyzed = listOf(initialFact(This).exclude(MARK_2)),
                added = finalFact(This, FIELD_A_B, ValueAccessor, MARK),
                expectedEmpty = true,
            ),
            Scenario(
                name = "17 exclusion on c with short added path returns a.b.c",
                analyzed = listOf(initialFact(This, FIELD_A_B).exclude(FIELD_B_C)),
                added = finalFact(This, FIELD_A_B, FIELD_B_C),
                expectedFacts = listOf(initialFact(This, FIELD_A_B, FIELD_B_C)),
            ),
            Scenario(
                name = "18 exclusion on b with short added path returns a.b",
                analyzed = listOf(initialFact(This).exclude(FIELD_A_B)),
                added = finalFact(This, FIELD_A_B),
                expectedFacts = listOf(initialFact(This, FIELD_A_B)),
            ),
            Scenario(
                name = "19 unrelated base plus matching this-base exclusion uses this-base result",
                analyzed = listOf(
                    initialFact(AccessPathBase.Argument(0), FIELD_A_B).exclude(FIELD_B_C),
                    initialFact(This, FIELD_A_B).exclude(FIELD_B_C),
                ),
                added = finalFact(This, FIELD_A_B, FIELD_B_C, FIELD_C_D),
                expectedFacts = listOf(initialFact(This, FIELD_A_B, FIELD_B_C)),
            ),
            Scenario(
                name = "20 conflicting exclusions on two levels return a.b.c",
                analyzed = listOf(
                    initialFact(This).exclude(FIELD_A_B),
                    initialFact(This, FIELD_A_B).exclude(FIELD_B_C),
                ),
                added = finalFact(This, FIELD_A_B, FIELD_B_C, FIELD_C_D),
                expectedFacts = listOf(initialFact(This, FIELD_A_B, FIELD_B_C)),
            ),
            Scenario(
                name = "21 mark exclusion with only mark path currently returns empty",
                analyzed = listOf(initialFact(This).exclude(MARK)),
                added = finalFact(This, ValueAccessor, MARK),
                expectedEmpty = true,
            ),
            Scenario(
                name = "22 type group exclusion with only type path currently returns empty",
                analyzed = listOf(initialFact(This).exclude(TypeInfoGroupAccessor)),
                added = finalFact(This, TYPE_INFO_A, TypeInfoGroupAccessor),
                expectedEmpty = true,
            ),
            Scenario(
                name = "23 root exclusion on mark with bare mark path returns mark.final",
                analyzed = listOf(initialFact(This).exclude(MARK)),
                added = finalFact(This, MARK),
                expectedFacts = listOf(initialFact(This, MARK, FinalAccessor)),
            ),
            Scenario(
                name = "24 exclusion on value under mark with bare chain currently returns empty",
                analyzed = listOf(initialFact(This, MARK).exclude(ValueAccessor)),
                added = finalFact(This, ValueAccessor, MARK),
                expectedEmpty = true,
            ),
            Scenario(
                name = "25 root exclusion on type group with bare type chain returns type group.final",
                analyzed = listOf(initialFact(This).exclude(TypeInfoGroupAccessor)),
                added = finalFact(This, TypeInfoGroupAccessor),
                expectedFacts = listOf(initialFact(This, TypeInfoGroupAccessor, FinalAccessor)),
            ),
            Scenario(
                name = "26 exclusion on concrete type under group with bare chain currently returns empty",
                analyzed = listOf(initialFact(This, TypeInfoGroupAccessor).exclude(TYPE_INFO_A)),
                added = finalFact(This, TYPE_INFO_A, TypeInfoGroupAccessor),
                expectedEmpty = true,
            ),
            Scenario(
                name = "27 root exclusion on mark with mark2 path returns empty",
                analyzed = listOf(initialFact(This).exclude(MARK)),
                added = finalFact(This, MARK_2),
                expectedEmpty = true,
            ),
            Scenario(
                name = "28 exclusion on typeA under group with typeB path returns empty",
                analyzed = listOf(initialFact(This, TypeInfoGroupAccessor).exclude(TYPE_INFO_A)),
                added = finalFact(This, TYPE_INFO_B, TypeInfoGroupAccessor),
                expectedEmpty = true,
            ),
        )

        scenarios.forEach { scenario ->
            val abstraction = newAbstraction()
            scenario.analyzed.forEach { analyzedFact ->
                abstraction.registerNewInitialFact(analyzedFact, FactTypeChecker.Dummy)
            }

            val produced = abstraction.addAbstractedInitialFact(scenario.added, FactTypeChecker.Dummy)

            if (scenario.expectedEmpty) {
                assertTrue(
                    produced.isEmpty(),
                    "[${scenario.name}] expected no produced facts; analyzed=${scenario.analyzed}; added=${scenario.added}; produced=${producedFactsToString(produced)}",
                )
            }

            scenario.expectedFacts.forEach { expected ->
                assertTrue(
                    produced.any { (initial, final) -> initial == expected && final.equalTo(expected) },
                    "[${scenario.name}] expected fact is missing; analyzed=${scenario.analyzed}; added=${scenario.added}; expected=$expected; produced=${producedFactsToString(produced)}",
                )
            }
        }
    }

    @Test
    fun `same conflicting fact added twice yields abstraction only once`() {
        val abstraction = newAbstraction()
        val analyzed = initialFact(This, FIELD_A_B).exclude(FIELD_B_C)
        abstraction.registerNewInitialFact(analyzed, FactTypeChecker.Dummy)

        val added = finalFact(This, FIELD_A_B, FIELD_B_C, FIELD_C_D)
        val firstProduced = abstraction.addAbstractedInitialFact(added, FactTypeChecker.Dummy)
        val secondProduced = abstraction.addAbstractedInitialFact(added, FactTypeChecker.Dummy)

        assertTrue(
            firstProduced.isNotEmpty(),
            "Expected first add to produce abstraction; analyzed=$analyzed; added=$added; produced=${producedFactsToString(firstProduced)}",
        )
        assertTrue(
            secondProduced.isEmpty(),
            "Expected second add of same fact to produce nothing; analyzed=$analyzed; added=$added; produced=${producedFactsToString(secondProduced)}",
        )
    }

    private fun initialFact(base: AccessPathBase, vararg accessors: Accessor): InitialFactAp {
        var fact = apManager.mostAbstractInitialAp(base)
        accessors.reversed().forEach { accessor ->
            fact = fact.prependAccessor(accessor)
        }
        return fact
    }

    private fun finalFact(base: AccessPathBase, vararg accessors: Accessor): FinalFactAp {
        var fact = apManager.createFinalAp(base, ExclusionSet.Empty)
        accessors.reversed().forEach { accessor ->
            fact = fact.prependAccessor(accessor)
        }
        return fact
    }

    private fun producedFactsToString(produced: List<Pair<InitialFactAp, FinalFactAp>>): String =
        if (produced.isEmpty()) {
            "[]"
        } else {
            produced.joinToString(prefix = "[", postfix = "]") { (initial, _) -> "$initial" }
        }

    private fun newAbstraction() = apManager.initialFactAbstraction(dummyInst)

    private val dummyInst = object : CommonInst {
        override fun toString(): String = "dummy-inst"
        override val location: CommonInstLocation = object : CommonInstLocation {
            override val method: CommonMethod = object : CommonMethod {
                override val name: String = "dummy"
                override val parameters: List<CommonMethodParameter> = emptyList()
                override val returnType: CommonTypeName = object : CommonTypeName {
                    override val typeName: String = "void"
                }

                override fun flowGraph(): ControlFlowGraph<CommonInst> = object : ControlFlowGraph<CommonInst> {
                    override val instructions: List<CommonInst> = emptyList()
                    override val entries: List<CommonInst> = emptyList()
                    override val exits: List<CommonInst> = emptyList()
                    override fun successors(node: CommonInst): Set<CommonInst> = emptySet()
                    override fun predecessors(node: CommonInst): Set<CommonInst> = emptySet()
                }
            }
        }
    }
}
