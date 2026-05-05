package org.opentaint.dataflow.ap.ifds.access

import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.ap.ifds.Accessor
import org.opentaint.dataflow.ap.ifds.AnyAccessor
import org.opentaint.dataflow.ap.ifds.ClassStaticAccessor
import org.opentaint.dataflow.ap.ifds.ElementAccessor
import org.opentaint.dataflow.ap.ifds.ExclusionSet
import org.opentaint.dataflow.ap.ifds.FactTypeChecker
import org.opentaint.dataflow.ap.ifds.FieldAccessor
import org.opentaint.dataflow.ap.ifds.FinalAccessor
import org.opentaint.dataflow.ap.ifds.TaintMarkAccessor
import org.opentaint.dataflow.ap.ifds.TypeInfoAccessor
import org.opentaint.dataflow.ap.ifds.TypeInfoGroupAccessor
import org.opentaint.dataflow.ap.ifds.ValueAccessor
import org.opentaint.ir.api.common.CommonMethod
import org.opentaint.ir.api.common.CommonMethodParameter
import org.opentaint.ir.api.common.CommonTypeName
import org.opentaint.ir.api.common.cfg.CommonInst
import org.opentaint.ir.api.common.cfg.CommonInstLocation
import org.opentaint.ir.api.common.cfg.ControlFlowGraph
import kotlin.test.Test
import kotlin.test.assertTrue

abstract class InitialFactAbstractionTest {
    private companion object {
        const val TYPE_A = "A"
        const val TYPE_B = "B"
        const val TYPE_C = "C"
        const val TYPE_D = "D"
        const val NO_ANY_FIELD_NAME = "<no-any>"

        val FIELD_A_B = FieldAccessor(TYPE_A, "b", TYPE_B)
        val FIELD_B_C = FieldAccessor(TYPE_B, "c", TYPE_C)
        val FIELD_B_E = FieldAccessor(TYPE_B, "e", TYPE_D)
        val FIELD_C_D = FieldAccessor(TYPE_C, "d", TYPE_D)
        val FIELD_NO_ANY = FieldAccessor(TYPE_B, NO_ANY_FIELD_NAME, TYPE_D)

        val MARK = TaintMarkAccessor("test-mark")
        val MARK_2 = TaintMarkAccessor("test-mark-2")
        val TYPE_INFO_A = TypeInfoAccessor("A")
        val TYPE_INFO_B = TypeInfoAccessor("B")
    }

    abstract fun mkApManager(strategy: AnyAccessorUnrollStrategy): ApManager

    private val apManager: ApManager = mkApManager(UnrollStrategy)

    private object UnrollStrategy : AnyAccessorUnrollStrategy {
        override fun unrollAccessor(accessor: Accessor): Boolean = when (accessor) {
            is ElementAccessor -> true
            is FieldAccessor -> accessor.fieldName != NO_ANY_FIELD_NAME
            is ClassStaticAccessor,
            is AnyAccessor,
            is FinalAccessor,
            is TaintMarkAccessor,
            is TypeInfoAccessor,
            is TypeInfoGroupAccessor -> false

            is ValueAccessor -> error("Unexpected accessor to unroll: $accessor")
        }
    }

    abstract fun merge(fact: FinalFactAp, vararg facts: FinalFactAp): FinalFactAp

    private fun runScenario(
        name: String,
        analyzed: List<InitialFactAp>,
        added: FinalFactAp,
        expectedFacts: List<InitialFactAp> = emptyList(),
        expectedEmpty: Boolean = false,
    ) {
        val abstraction = newAbstraction()
        analyzed.forEach { analyzedFact ->
            abstraction.registerNewInitialFact(analyzedFact, FactTypeChecker.Dummy)
        }

        val produced = abstraction.addAbstractedInitialFact(added, FactTypeChecker.Dummy)

        if (expectedEmpty) {
            val message = buildString {
                appendLine("[$name] expected no produced facts")
                appendLine("analyzed=$analyzed")
                appendLine("added=$added")
                appendLine("produced=${producedFactsToString(produced)}")
            }
            assertTrue(abstractionIsEmpty(produced), message)
        }

        expectedFacts.forEach { expected ->
            val message = buildString {
                appendLine("[$name] expected fact is missing")
                appendLine("analyzed=$analyzed")
                appendLine("added=$added")
                appendLine("expected=$expected")
                appendLine("produced=${producedFactsToString(produced)}")
            }
            assertTrue(
                produced.any { (initial, final) -> initial == expected && final.equalTo(expected) },
                message,
            )
        }
    }



    @Test
    fun `scenario 1 exclusion hit on c returns a b c`() = runScenario(
        "1 exclusion hit on c returns a.b.c",
        listOf(initialFact(AccessPathBase.This, FIELD_A_B).exclude(FIELD_B_C)),
        finalFact(AccessPathBase.This, FIELD_A_B, FIELD_B_C, FIELD_C_D),
        expectedFacts = listOf(initialFact(AccessPathBase.This, FIELD_A_B, FIELD_B_C))
    )

    @Test
    fun `scenario 2 exclusion miss on e returns empty`() = runScenario(
        "2 exclusion miss on e returns empty",
        listOf(initialFact(AccessPathBase.This, FIELD_A_B).exclude(FIELD_B_E)),
        finalFact(AccessPathBase.This, FIELD_A_B, FIELD_B_C, FIELD_C_D),
        expectedEmpty = true
    )

    @Test
    fun `scenario 3 analyzed mark no exclusions returns empty`() = runScenario(
        "3 analyzed mark no exclusions returns empty",
        listOf(initialFact(AccessPathBase.This, MARK)),
        finalFact(AccessPathBase.This, FIELD_A_B, ValueAccessor, MARK),
        expectedEmpty = true
    )

    @Test
    fun `scenario 4 no analyzed facts for this base returns most abstract`() = runScenario(
        "4 no analyzed facts for this base returns most abstract",
        listOf(initialFact(AccessPathBase.ClassStatic, FIELD_A_B).exclude(FIELD_B_C)),
        finalFact(AccessPathBase.This, FIELD_A_B, FIELD_B_C, FIELD_C_D),
        expectedFacts = listOf(initialFact(AccessPathBase.This))
    )

    @Test
    fun `scenario 5 root exclusion on b returns a b`() = runScenario(
        "5 root exclusion on b returns a.b",
        listOf(initialFact(AccessPathBase.This).exclude(FIELD_A_B)),
        finalFact(AccessPathBase.This, FIELD_A_B, FIELD_B_C),
        expectedFacts = listOf(initialFact(AccessPathBase.This, FIELD_A_B))
    )

    @Test
    fun `scenario 6 root non matching exclusion returns empty`() = runScenario(
        "6 root non matching exclusion returns empty",
        listOf(initialFact(AccessPathBase.This).exclude(FIELD_B_E)),
        finalFact(AccessPathBase.This, FIELD_A_B, FIELD_B_C),
        expectedEmpty = true
    )

    @Test
    fun `scenario 9 multiple analyzed paths currently produce no abstraction`() = runScenario(
        "9 multiple analyzed paths currently produce no abstraction",
        listOf(initialFact(AccessPathBase.This).exclude(FIELD_A_B), initialFact(AccessPathBase.This, FIELD_A_B).exclude(FIELD_B_E)),
        finalFact(AccessPathBase.This, FIELD_A_B, FIELD_B_C, FIELD_C_D),
        expectedEmpty = true
    )

    @Test
    fun `scenario 10 most abstract analyzed with empty exclusions returns empty`() = runScenario(
        "10 most abstract analyzed with empty exclusions returns empty",
        listOf(initialFact(AccessPathBase.This)),
        finalFact(AccessPathBase.This, FIELD_A_B),
        expectedEmpty = true
    )

    @Test
    fun `scenario 11 mark exclusion at root currently returns empty`() = runScenario(
        "11 mark exclusion at root currently returns empty",
        listOf(initialFact(AccessPathBase.This).exclude(MARK)),
        finalFact(AccessPathBase.This, FIELD_A_B, ValueAccessor, MARK),
        expectedEmpty = true
    )

    @Test
    fun `scenario 12 value exclusion after mark currently returns empty`() = runScenario(
        "12 value exclusion after mark currently returns empty",
        listOf(initialFact(AccessPathBase.This, MARK).exclude(ValueAccessor)),
        finalFact(AccessPathBase.This, FIELD_A_B, ValueAccessor, MARK),
        expectedEmpty = true
    )

    @Test
    fun `scenario 13 type group exclusion at root currently returns empty`() = runScenario(
        "13 type group exclusion at root currently returns empty",
        listOf(initialFact(AccessPathBase.This).exclude(TypeInfoGroupAccessor)),
        finalFact(AccessPathBase.This, FIELD_A_B, TYPE_INFO_A, TypeInfoGroupAccessor),
        expectedEmpty = true
    )

    @Test
    fun `scenario 14 type accessor exclusion after group currently returns empty`() = runScenario(
        "14 type accessor exclusion after group currently returns empty",
        listOf(initialFact(AccessPathBase.This, TypeInfoGroupAccessor).exclude(TYPE_INFO_A)),
        finalFact(AccessPathBase.This, FIELD_A_B, TYPE_INFO_A, TypeInfoGroupAccessor),
        expectedEmpty = true
    )

    @Test
    fun `scenario 15 non matching type accessor exclusion returns empty`() = runScenario(
        "15 non matching type accessor exclusion returns empty",
        listOf(initialFact(AccessPathBase.This, TypeInfoGroupAccessor).exclude(TYPE_INFO_B)),
        finalFact(AccessPathBase.This, FIELD_A_B, TYPE_INFO_A, TypeInfoGroupAccessor),
        expectedEmpty = true
    )

    @Test
    fun `scenario 16 mark2 exclusion does not match mark1 returns empty`() = runScenario(
        "16 mark2 exclusion does not match mark1 returns empty",
        listOf(initialFact(AccessPathBase.This).exclude(MARK_2)),
        finalFact(AccessPathBase.This, FIELD_A_B, ValueAccessor, MARK),
        expectedEmpty = true
    )

    @Test
    fun `scenario 17 exclusion on c with short added path returns a b c`() = runScenario(
        "17 exclusion on c with short added path returns a.b.c",
        listOf(initialFact(AccessPathBase.This, FIELD_A_B).exclude(FIELD_B_C)),
        finalFact(AccessPathBase.This, FIELD_A_B, FIELD_B_C),
        expectedFacts = listOf(initialFact(AccessPathBase.This, FIELD_A_B, FIELD_B_C))
    )

    @Test
    fun `scenario 18 exclusion on b with short added path returns a b`() = runScenario(
        "18 exclusion on b with short added path returns a.b",
        listOf(initialFact(AccessPathBase.This).exclude(FIELD_A_B)),
        finalFact(AccessPathBase.This, FIELD_A_B),
        expectedFacts = listOf(initialFact(AccessPathBase.This, FIELD_A_B))
    )

    @Test
    fun `scenario 19 unrelated base plus matching this base exclusion uses this base result`() = runScenario(
        "19 unrelated base plus matching this-base exclusion uses this-base result",
        listOf(
            initialFact(AccessPathBase.ClassStatic, FIELD_A_B).exclude(FIELD_B_C),
            initialFact(AccessPathBase.This, FIELD_A_B).exclude(FIELD_B_C)
        ),
        finalFact(AccessPathBase.This, FIELD_A_B, FIELD_B_C, FIELD_C_D),
        expectedFacts = listOf(initialFact(AccessPathBase.This, FIELD_A_B, FIELD_B_C))
    )

    @Test
    fun `scenario 20 conflicting exclusions on two levels return a b c`() = runScenario(
        "20 conflicting exclusions on two levels return a.b.c",
        listOf(initialFact(AccessPathBase.This).exclude(FIELD_A_B), initialFact(AccessPathBase.This, FIELD_A_B).exclude(FIELD_B_C)),
        finalFact(AccessPathBase.This, FIELD_A_B, FIELD_B_C, FIELD_C_D),
        expectedFacts = listOf(initialFact(AccessPathBase.This, FIELD_A_B, FIELD_B_C))
    )

    @Test
    fun `scenario 21 mark exclusion with only mark path currently returns empty`() = runScenario(
        "21 mark exclusion with only mark path currently returns empty",
        listOf(initialFact(AccessPathBase.This).exclude(MARK)),
        finalFact(AccessPathBase.This, ValueAccessor, MARK),
        expectedEmpty = true
    )

    @Test
    fun `scenario 22 type group exclusion with only type path currently returns empty`() = runScenario(
        "22 type group exclusion with only type path currently returns empty",
        listOf(initialFact(AccessPathBase.This).exclude(TypeInfoGroupAccessor)),
        finalFact(AccessPathBase.This, TYPE_INFO_A, TypeInfoGroupAccessor),
        expectedEmpty = true
    )

    @Test
    fun `scenario 23 root exclusion on mark with bare mark path returns mark final`() = runScenario(
        "23 root exclusion on mark with bare mark path returns mark.final",
        listOf(initialFact(AccessPathBase.This).exclude(MARK)),
        finalFact(AccessPathBase.This, MARK),
        expectedFacts = listOf(initialFact(AccessPathBase.This, MARK, FinalAccessor))
    )

    @Test
    fun `scenario 24 exclusion on value under mark with bare chain currently returns empty`() = runScenario(
        "24 exclusion on value under mark with bare chain currently returns empty",
        listOf(initialFact(AccessPathBase.This, MARK).exclude(ValueAccessor)),
        finalFact(AccessPathBase.This, ValueAccessor, MARK),
        expectedEmpty = true
    )

    @Test
    fun `scenario 25 root exclusion on type group with bare type chain returns type group final`() = runScenario(
        "25 root exclusion on type group with bare type chain returns type group.final",
        listOf(initialFact(AccessPathBase.This).exclude(TypeInfoGroupAccessor)),
        finalFact(AccessPathBase.This, TypeInfoGroupAccessor),
        expectedFacts = listOf(initialFact(AccessPathBase.This, TypeInfoGroupAccessor, FinalAccessor))
    )

    @Test
    fun `scenario 26 exclusion on concrete type under group with bare chain currently returns empty`() = runScenario(
        "26 exclusion on concrete type under group with bare chain currently returns empty",
        listOf(initialFact(AccessPathBase.This, TypeInfoGroupAccessor).exclude(TYPE_INFO_A)),
        finalFact(AccessPathBase.This, TYPE_INFO_A, TypeInfoGroupAccessor),
        expectedEmpty = true
    )

    @Test
    fun `scenario 27 root exclusion on mark with mark2 path returns empty`() = runScenario(
        "27 root exclusion on mark with mark2 path returns empty",
        listOf(initialFact(AccessPathBase.This).exclude(MARK)),
        finalFact(AccessPathBase.This, MARK_2),
        expectedEmpty = true
    )

    @Test
    fun `scenario 28 exclusion on type a under group with type b path returns empty`() = runScenario(
        "28 exclusion on typeA under group with typeB path returns empty",
        listOf(initialFact(AccessPathBase.This, TypeInfoGroupAccessor).exclude(TYPE_INFO_A)),
        finalFact(AccessPathBase.This, TYPE_INFO_B, TypeInfoGroupAccessor),
        expectedEmpty = true
    )

    @Test
    fun `scenario 29 merged final root exclusion on b returns a b`() = runScenario(
        "29 merged final root exclusion on b returns a.b",
        listOf(initialFact(AccessPathBase.This).exclude(FIELD_A_B)),
        merge(finalFact(AccessPathBase.This, FIELD_A_B, FIELD_B_C), finalFact(AccessPathBase.This, FIELD_A_B, FIELD_B_E)),
        expectedFacts = listOf(initialFact(AccessPathBase.This, FIELD_A_B))
    )

    @Test
    fun `scenario 30 merged final exclusion on c under b returns a b c`() = runScenario(
        "30 merged final exclusion on c under b returns a.b.c",
        listOf(initialFact(AccessPathBase.This, FIELD_A_B).exclude(FIELD_B_C)),
        merge(finalFact(AccessPathBase.This, FIELD_A_B, FIELD_B_C, FIELD_C_D), finalFact(AccessPathBase.This, FIELD_A_B, FIELD_B_E)),
        expectedFacts = listOf(initialFact(AccessPathBase.This, FIELD_A_B, FIELD_B_C))
    )

    @Test
    fun `scenario 31 merged final non matching exclusion on e returns empty`() = runScenario(
        "31 merged final non matching exclusion on e returns empty",
        listOf(initialFact(AccessPathBase.This, FIELD_A_B).exclude(FIELD_B_E)),
        merge(finalFact(AccessPathBase.This, FIELD_A_B, FIELD_B_C, FIELD_C_D), finalFact(AccessPathBase.This, FIELD_A_B, FIELD_B_C)),
        expectedEmpty = true
    )

    @Test
    fun `scenario 32 merged final mark plus field with mark exclusion returns mark final`() = runScenario(
        "32 merged final mark plus field with mark exclusion returns mark.final",
        listOf(initialFact(AccessPathBase.This).exclude(MARK)),
        merge(finalFact(AccessPathBase.This, MARK), finalFact(AccessPathBase.This, FIELD_A_B, FIELD_B_C)),
        expectedFacts = listOf(initialFact(AccessPathBase.This, MARK, FinalAccessor))
    )

    @Test
    fun `scenario 33 merged final type group plus field exclusion returns type group final`() = runScenario(
        "33 merged final type group plus field exclusion returns type group.final",
        listOf(initialFact(AccessPathBase.This).exclude(TypeInfoGroupAccessor)),
        merge(finalFact(AccessPathBase.This, TypeInfoGroupAccessor), finalFact(AccessPathBase.This, FIELD_A_B, FIELD_B_C)),
        expectedFacts = listOf(initialFact(AccessPathBase.This, TypeInfoGroupAccessor, FinalAccessor))
    )

    @Test
    fun `scenario 34 merged final with any branch and root exclusion on b returns a b`() = runScenario(
        "34 merged final with any branch and root exclusion on b returns a.b",
        listOf(initialFact(AccessPathBase.This).exclude(FIELD_A_B)),
        merge(finalFact(AccessPathBase.This, AnyAccessor, FIELD_B_C), finalFact(AccessPathBase.This, FIELD_A_B, FIELD_B_C)),
        expectedFacts = listOf(initialFact(AccessPathBase.This, FIELD_A_B))
    )

    @Test
    fun `scenario 35 merged final any under b plus concrete c exclusion returns a b c`() = runScenario(
        "35 merged final any under b plus concrete c exclusion returns a.b.c",
        listOf(initialFact(AccessPathBase.This, FIELD_A_B).exclude(FIELD_B_C)),
        merge(finalFact(AccessPathBase.This, FIELD_A_B, AnyAccessor, MARK), finalFact(AccessPathBase.This, FIELD_A_B, FIELD_B_C, FIELD_C_D)),
        expectedFacts = listOf(initialFact(AccessPathBase.This, FIELD_A_B, FIELD_B_C))
    )

    @Test
    fun `scenario 36 merged final any under b with non matching exclusion on e returns a b e`() = runScenario(
        "36 merged final any under b with non matching exclusion on e returns a.b.e",
        listOf(initialFact(AccessPathBase.This, FIELD_A_B).exclude(FIELD_B_E)),
        merge(finalFact(AccessPathBase.This, FIELD_A_B, AnyAccessor, MARK), finalFact(AccessPathBase.This, FIELD_A_B, FIELD_B_C, FIELD_C_D)),
        expectedFacts = listOf(initialFact(AccessPathBase.This, FIELD_A_B, FIELD_B_E))
    )

    @Test
    fun `scenario 37 merged final with root any and concrete branch exclusion on c returns empty`() = runScenario(
        "37 merged final with root any and concrete branch exclusion on c returns empty",
        listOf(initialFact(AccessPathBase.This, FIELD_A_B).exclude(FIELD_B_C)),
        merge(finalFact(AccessPathBase.This, AnyAccessor, FIELD_B_C, FIELD_C_D), finalFact(AccessPathBase.This, FIELD_A_B, FIELD_B_E)),
        expectedEmpty = true
    )

    @Test
    fun `scenario 38 merged final unroll next with any no any leaf and c exclusion returns a b c`() = runScenario(
        "38 merged final unroll next with any no-any leaf and c exclusion returns a.b.c",
        listOf(initialFact(AccessPathBase.This, FIELD_A_B).exclude(FIELD_B_C)),
        merge(finalFact(AccessPathBase.This, FIELD_A_B, AnyAccessor, FIELD_NO_ANY), finalFact(AccessPathBase.This, FIELD_A_B, FIELD_B_C, FIELD_C_D)),
        expectedFacts = listOf(initialFact(AccessPathBase.This, FIELD_A_B, FIELD_B_C))
    )

    @Test
    fun `scenario 39 merged final mark and value chains with root mark exclusion returns mark final`() = runScenario(
        "39 merged final mark and value chains with root mark exclusion returns mark.final",
        listOf(initialFact(AccessPathBase.This).exclude(MARK)),
        merge(finalFact(AccessPathBase.This, MARK), finalFact(AccessPathBase.This, ValueAccessor, MARK)),
        expectedFacts = listOf(initialFact(AccessPathBase.This, MARK, FinalAccessor))
    )

    @Test
    fun `scenario 40 merged final type group and any typed chain with root exclusion returns type group final`() =
        runScenario(
            "40 merged final type group and any typed chain with root exclusion returns type group.final",
            listOf(initialFact(AccessPathBase.This).exclude(TypeInfoGroupAccessor)),
            merge(
                finalFact(AccessPathBase.This, TypeInfoGroupAccessor),
                finalFact(AccessPathBase.This, AnyAccessor, TYPE_INFO_A, TypeInfoGroupAccessor)
            ),
            expectedFacts = listOf(initialFact(AccessPathBase.This, TypeInfoGroupAccessor, FinalAccessor))
        )

    @Test
    fun `any accessor scenario 1 analyzed excludes b added any c under root returns this b`() = runScenario(
        "any-1 analyzed excludes b, added any.c under root returns this.b",
        listOf(initialFact(AccessPathBase.This).exclude(FIELD_A_B)),
        finalFact(AccessPathBase.This, AnyAccessor, FIELD_B_C),
        expectedFacts = listOf(initialFact(AccessPathBase.This, FIELD_A_B))
    )

    @Test
    fun `any accessor scenario 2 analyzed excludes c under b added b any mark returns this b c`() = runScenario(
        "any-2 analyzed excludes c under b, added b.any.mark returns this.b.c",
        listOf(initialFact(AccessPathBase.This, FIELD_A_B).exclude(FIELD_B_C)),
        finalFact(AccessPathBase.This, FIELD_A_B, AnyAccessor, MARK),
        expectedFacts = listOf(initialFact(AccessPathBase.This, FIELD_A_B, FIELD_B_C))
    )

    @Test
    fun `any accessor scenario 3 analyzed excludes c under b added b any d returns this b c`() = runScenario(
        "any-3 analyzed excludes c under b, added b.any.d returns this.b.c",
        listOf(initialFact(AccessPathBase.This, FIELD_A_B).exclude(FIELD_B_C)),
        finalFact(AccessPathBase.This, FIELD_A_B, AnyAccessor, FIELD_C_D),
        expectedFacts = listOf(initialFact(AccessPathBase.This, FIELD_A_B, FIELD_B_C))
    )

    @Test
    fun `any accessor scenario 4 analyzed excludes e under b added b any mark returns this b e`() = runScenario(
        "any-4 analyzed excludes e under b, added b.any.mark returns this.b.e",
        listOf(initialFact(AccessPathBase.This, FIELD_A_B).exclude(FIELD_B_E)),
        finalFact(AccessPathBase.This, FIELD_A_B, AnyAccessor, MARK),
        expectedFacts = listOf(initialFact(AccessPathBase.This, FIELD_A_B, FIELD_B_E))
    )

    @Test
    fun `any accessor scenario 5 analyzed excludes root b added any c`() = runScenario(
        "any-5 analyzed excludes root b, added any.c",
        listOf(initialFact(AccessPathBase.This).exclude(FIELD_A_B)),
        finalFact(AccessPathBase.This, AnyAccessor, FIELD_B_C),
        expectedFacts = listOf(initialFact(AccessPathBase.This, FIELD_A_B))
    )

    @Test
    fun `any accessor scenario 6 analyzed excludes c under b added b any with rule storage`() = runScenario(
        "any-6 analyzed excludes c under b, added b.any with rule-storage",
        listOf(initialFact(AccessPathBase.This, FIELD_A_B).exclude(FIELD_B_C)),
        finalFact(AccessPathBase.This, FIELD_A_B, AnyAccessor, FIELD_NO_ANY),
        expectedFacts = listOf(initialFact(AccessPathBase.This, FIELD_A_B, FIELD_B_C))
    )

    @Test
    fun `any accessor scenario 7 analyzed excludes c under b added b any value`() = runScenario(
        "any-7 analyzed excludes c under b, added b.any.value",
        listOf(initialFact(AccessPathBase.This, FIELD_A_B).exclude(FIELD_B_C)),
        finalFact(AccessPathBase.This, FIELD_A_B, AnyAccessor, ValueAccessor),
        expectedFacts = listOf(initialFact(AccessPathBase.This, FIELD_A_B, FIELD_B_C))
    )

    @Test
    fun `any accessor scenario 8 analyzed excludes d under b c added b c any mark`() = runScenario(
        "any-8 analyzed excludes d under b.c, added b.c.any.mark",
        listOf(initialFact(AccessPathBase.This, FIELD_A_B, FIELD_B_C).exclude(FIELD_C_D)),
        finalFact(AccessPathBase.This, FIELD_A_B, FIELD_B_C, AnyAccessor, MARK),
        expectedFacts = listOf(initialFact(AccessPathBase.This, FIELD_A_B))
    )

    @Test
    fun `any accessor scenario 9 analyzed excludes d under b c added b c any d mark`() = runScenario(
        "any-9 analyzed excludes d under b.c, added b.c.any.d.mark",
        listOf(initialFact(AccessPathBase.This, FIELD_A_B, FIELD_B_C).exclude(FIELD_C_D)),
        finalFact(AccessPathBase.This, FIELD_A_B, FIELD_B_C, AnyAccessor, FIELD_C_D, MARK),
        expectedFacts = listOf(initialFact(AccessPathBase.This, FIELD_A_B))
    )

    @Test
    fun `same conflicting fact added twice yields abstraction only once`() {
        val abstraction = newAbstraction()
        val analyzed = initialFact(AccessPathBase.This, FIELD_A_B).exclude(FIELD_B_C)
        abstraction.registerNewInitialFact(analyzed, FactTypeChecker.Dummy)

        val added = finalFact(AccessPathBase.This, FIELD_A_B, FIELD_B_C, FIELD_C_D)
        val firstProduced = abstraction.addAbstractedInitialFact(added, FactTypeChecker.Dummy)
        val secondProduced = abstraction.addAbstractedInitialFact(added, FactTypeChecker.Dummy)

        assertTrue(
            firstProduced.isNotEmpty(),
            "Expected first add to produce abstraction; analyzed=$analyzed; added=$added; produced=${
                producedFactsToString(
                    firstProduced
                )
            }",
        )
        assertTrue(
            abstractionIsEmpty(secondProduced),
            "Expected second add of same fact to produce nothing; analyzed=$analyzed; added=$added; produced=${
                producedFactsToString(
                    secondProduced
                )
            }",
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

    private fun abstractionIsEmpty(produced: List<Pair<InitialFactAp, FinalFactAp>>): Boolean =
        produced.isEmpty() || (produced.size == 1 && produced.single().first.size == 0)


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
