package org.opentaint.common.sast.rules

import org.opentaint.dataflow.configuration.selection.*
import org.opentaint.semgrep.pattern.TaintRuleFromSemgrep
import org.opentaint.semgrep.pattern.TaintRuleFromSemgrep.Structure
import org.opentaint.semgrep.pattern.TaintRuleFromSemgrep.TaintRuleGroup
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class SemgrepRuleProviderSelectionTest {
    private data class Item(
        val id: String?,
        val references: Set<String>? = emptySet(),
        val role: RuleRole = RuleRole.SOURCE,
        val condition: MarkExpression = MarkExpression.Truth(true),
        val assignments: List<String> = emptyList(),
    ) {
        fun row() = MarkCatalogRow(id, role, condition, assignments, 0, references.orEmpty())
    }

    private class Provider(graphs: List<TaintRuleFromSemgrep<Item>>) : SemgrepRuleProvider<Item, Item>(graphs) {
        var ordinary = MarkCatalog(graphs.flatMap { it.taintRules.flatMap { group -> group.rules.map(Item::row) } })
        var bidi: MarkCatalog? = null
        val snapshot get() = selectionSnapshot
        val bidiSnapshot get() = bidiSelectionSnapshot
        var appliedSnapshot: RuleSelectionSnapshot? = null
        var installedGenerations = 0
        var publicationChecks = 0
        override fun selectionPublished() {
            publicationChecks++
            val snapshot = selectionSnapshot ?: return
            if (snapshot !== appliedSnapshot) {
                appliedSnapshot = snapshot
                installedGenerations++
            }
        }
        fun ownedSelection(items: List<Item>) = appliedSnapshot?.let { snapshot ->
            items.filter { snapshot.keepsRaw(it.id, it.role, false) }
        } ?: items
        override fun Item.ruleItemId() = id
        override fun Item.resolvedRuleId() = id
        override fun executableMarkCatalog(ignoreEndFacts: Boolean): MarkCatalog = if (ignoreEndFacts) bidi ?: ordinary else ordinary
        override fun ruleItemMarkReferences(item: Item) = item.references
        fun selected(items: List<Item>, metadata: Boolean) = items.select(metadata).toList()
    }

    private fun graph(id: String, vararg items: Item, owner: String? = null,
                      finalIds: Set<String> = setOfNotNull(items.lastOrNull()?.id)) = TaintRuleFromSemgrep(
        id, Structure.Matching(listOf(TaintRuleGroup(items.toList(), finalRuleIds = finalIds))),
        markOwners = if (owner == null) emptyMap() else items.flatMap { it.references.orEmpty() }.associateWith { owner },
        primitiveByOwner = if (owner == null) emptyMap() else mapOf(owner to false),
    )

    @Test fun `initial null selection differs from a selected empty scope`() {
        val item = Item("source")
        val provider = Provider(listOf(graph("g", item)))
        assertNull(provider.snapshot)
        assertEquals(listOf(item), provider.selected(listOf(item), false))
        provider.selectRelevantSemgrepRules(emptySet())
        assertEquals(emptyList(), provider.selected(listOf(item), false))
        assertEquals(emptySet(), provider.snapshot!!.executionIds)
    }

    @Test fun `metadata keeps every internal item of an active graph`() {
        val source = Item("source", setOf("T"), assignments = listOf("T"))
        val transition = Item("transition", setOf("T", "U"), assignments = listOf("U"))
        val sink = Item("sink", setOf("T"), RuleRole.SINK, MarkExpression.ContainsMark("T"))
        val inactive = Item("inactive", setOf("V"), assignments = listOf("V"))
        val provider = Provider(listOf(graph("active", source, transition, sink, owner = "active"),
            graph("inactive", inactive, owner = "inactive")))
        provider.selectRelevantSemgrepRules(setOf("source", "sink"))
        assertEquals(setOf("source", "sink"), provider.snapshot!!.executionIds)
        assertEquals(setOf("source", "transition", "sink"), provider.snapshot!!.metadataIds)
        assertEquals(setOf("T"), provider.snapshot!!.markPlan.liveMarkNames)
        assertFalse(provider.snapshot!!.markPlan.isLive("U"))
        assertEquals(listOf(source, transition, sink), provider.selected(listOf(source, transition, sink, inactive), true))
    }

    @Test fun `metadata support follows shared owner without shared mark names`() {
        val a = Item("a", setOf("T"))
        val b = Item("b", setOf("U"))
        val provider = Provider(listOf(graph("a", a, owner = "shared"), graph("b", b, owner = "shared")))
        provider.selectRelevantSemgrepRules(setOf("a"))
        assertEquals(setOf("a", "b"), provider.snapshot!!.metadataIds)
        assertEquals(setOf("a"), provider.snapshot!!.executionIds)
    }

    @Test fun `metadata support follows full reference and item ID closure`() {
        val first = Item("first", setOf("T"))
        val bridge = Item("bridge", setOf("T", "U"))
        val last = Item("last", setOf("U"))
        val provider = Provider(listOf(graph("first", first), graph("bridge", bridge), graph("last", last)))
        provider.selectRelevantSemgrepRules(setOf("first"))
        assertEquals(setOf("first", "bridge", "last"), provider.snapshot!!.metadataIds)
        assertEquals(setOf("first"), provider.snapshot!!.executionIds)
    }

    @Test fun `normal and metadata null and foreign ID policies remain distinct`() {
        val chosen = Item("chosen")
        val unchosen = Item("unchosen")
        val provider = Provider(listOf(graph("a", chosen), graph("b", unchosen)))
        provider.selectRelevantSemgrepRules(setOf("chosen"))
        val unknown = Item("foreign")
        val nullId = Item(null)
        assertEquals(listOf(nullId, chosen), provider.selected(listOf(unknown, nullId, chosen, unchosen), false))
        assertEquals(listOf(unknown, nullId, chosen), provider.selected(listOf(unknown, nullId, chosen, unchosen), true))
        assertTrue(provider.snapshot!!.keepsRaw("unchosen", RuleRole.CLEANER, false))
        assertTrue(provider.snapshot!!.keepsRaw("unchosen", RuleRole.PASS, true))
        assertFalse(provider.snapshot!!.keepsRaw("unchosen", RuleRole.CLEANER, true))
    }

    @Test fun `selection keeps duplicate variant order`() {
        val a = Item("a")
        val b = Item("b")
        val provider = Provider(listOf(graph("a", a), graph("b", b)))
        provider.selectRelevantSemgrepRules(setOf("a"))
        assertEquals(listOf(a, a, a), provider.selected(listOf(a, b, a, b, a), false))
    }

    @Test fun `identical complete selection reuses snapshots but owner body changes create an epoch`() {
        val a = Item("a")
        val provider = Provider(listOf(graph("a", a)))
        provider.selectRelevantSemgrepRules(setOf("a"))
        val ordinary = provider.snapshot!!
        val bidi = provider.bidiSnapshot!!
        provider.selectRelevantSemgrepRules(setOf("a"))
        assertSame(ordinary, provider.snapshot)
        assertSame(bidi, provider.bidiSnapshot)
        provider.ordinary = provider.ordinary.copy(sourceGeneration = 1)
        provider.selectRelevantSemgrepRules(setOf("a"))
        assertNotSame(ordinary, provider.snapshot)
        assertEquals(ordinary.generation + 1, provider.snapshot!!.generation)
    }

    @Test fun `direct public selection also installs owned view and identical selection only validates`() {
        val chosen = Item("chosen")
        val dropped = Item("dropped")
        val provider = Provider(listOf(graph("a", chosen), graph("b", dropped)))
        assertEquals(listOf(chosen,dropped), provider.ownedSelection(listOf(chosen,dropped)))
        provider.selectRelevantSemgrepRules(setOf("chosen"))
        assertSame(provider.snapshot, provider.appliedSnapshot)
        assertEquals(listOf(chosen), provider.ownedSelection(listOf(chosen,dropped)))
        provider.selectRelevantSemgrepRules(setOf("chosen"))
        assertEquals(2, provider.publicationChecks)
        assertEquals(1, provider.installedGenerations)
    }

    @Test fun `unknown delegated boundary forces TOP and all metadata without changing S`() {
        val source = Item("source", setOf("T"), assignments = listOf("T"))
        val sink = Item("sink", setOf("T"), RuleRole.SINK, MarkExpression.ContainsMark("T"))
        val inactive = Item("inactive", setOf("U"), assignments = listOf("U"))
        val provider = Provider(listOf(graph("active", source, sink, owner = "active"),
            graph("inactive", inactive, owner = "inactive")))
        RuleSelectionBoundary.withScope(false) {
            RuleSelectionBoundary.withScope(true) { provider.selectRelevantSemgrepRules(setOf("source", "sink")) }
        }
        assertTrue(provider.snapshot!!.markPlan.protectAll)
        assertEquals(setOf("source", "sink"), provider.snapshot!!.executionIds)
        assertEquals(setOf("source", "sink", "inactive"), provider.snapshot!!.metadataIds)
        assertTrue(RuleSelectionBoundary.isComplete)
        provider.selectRelevantSemgrepRules(setOf("source", "sink"))
        assertFalse(provider.snapshot!!.markPlan.protectAll)
        assertEquals(setOf("source", "sink"), provider.snapshot!!.metadataIds)
    }

    @Test fun `selection boundary nesting restores exact scope after failure`() {
        assertTrue(RuleSelectionBoundary.isComplete)
        RuleSelectionBoundary.withScope(false) {
            assertFalse(RuleSelectionBoundary.isComplete)
            kotlin.test.assertFailsWith<IllegalStateException> {
                RuleSelectionBoundary.withScope(true) {
                    assertFalse(RuleSelectionBoundary.isComplete)
                    error("nested failure")
                }
            }
            assertFalse(RuleSelectionBoundary.isComplete)
        }
        assertTrue(RuleSelectionBoundary.isComplete)
        kotlin.test.assertFailsWith<IllegalStateException> {
            RuleSelectionBoundary.withScope(false) { error("outer failure") }
        }
        assertTrue(RuleSelectionBoundary.isComplete)
    }

    @Test fun `ordinary END support does not leak into no END view`() {
        val source = Item("source", setOf("T"), assignments = listOf("T"))
        val sink = Item("sink", setOf("T", "END"), RuleRole.SINK, MarkExpression.ContainsMark("T"))
        val provider = Provider(listOf(graph("g", source, sink)))
        provider.bidi = provider.ordinary
        provider.ordinary = provider.ordinary.copy(rows = provider.ordinary.rows.map { row ->
            if (row.itemId != "sink") row else row.copy(assignments = listOf("END"),
                protectedProducers = setOf("END"), protectedConsumers = setOf("END"))
        })
        provider.selectRelevantSemgrepRules(setOf("source", "sink"))
        assertEquals(setOf("T", "END"), provider.snapshot!!.markPlan.liveMarkNames)
        assertEquals(setOf("T"), provider.bidiSnapshot!!.markPlan.liveMarkNames)
        assertEquals(provider.snapshot!!.generation, provider.bidiSnapshot!!.generation)
    }

    @Test fun `external reference retains affected inactive graph metadata`() {
        val active = Item("active", setOf("T"))
        val inactive = Item("inactive", setOf("U"))
        val provider = Provider(listOf(graph("a", active), graph("b", inactive)))
        provider.ordinary = provider.ordinary.copy(rows = provider.ordinary.rows +
            MarkCatalogRow(null, RuleRole.PASS, MarkExpression.Truth(true), emptyList(), 1, setOf("U")))
        provider.selectRelevantSemgrepRules(setOf("active"))
        assertEquals(setOf("active", "inactive"), provider.snapshot!!.metadataIds)
    }

    @Test fun `unknown executable scope retains metadata and all marks`() {
        val active = Item("active")
        val inactive = Item("inactive")
        val provider = Provider(listOf(graph("a", active), graph("b", inactive)))
        provider.ordinary = provider.ordinary.copy(protectAll = true)
        provider.selectRelevantSemgrepRules(setOf("active"))
        assertEquals(setOf("active", "inactive"), provider.snapshot!!.metadataIds)
        assertTrue(provider.snapshot!!.markPlan.protectAll)
    }

    @Test fun `existing dependency alternatives remain alternatives`() {
        val a = Item("a")
        val b = Item("b")
        val finalItem = Item("final")
        val group = TaintRuleGroup(listOf(a, b, finalItem),
            ruleDependencies = mapOf("final" to setOf("a", "b")), finalRuleIds = setOf("final"))
        val provider = Provider(listOf(TaintRuleFromSemgrep("g", Structure.Matching(listOf(group)))))
        provider.selectRelevantSemgrepRules(setOf("a", "final"))
        assertEquals(setOf("a", "final"), provider.snapshot!!.executionIds)
    }
}
