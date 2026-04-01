package org.opentaint.dataflow.jvm.ap.ifds.analysis

import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.ap.ifds.Accessor
import org.opentaint.dataflow.ap.ifds.AnyAccessor
import org.opentaint.dataflow.ap.ifds.access.FinalFactAp
import org.opentaint.dataflow.jvm.ap.ifds.JIRLocalAliasAnalysis
import org.opentaint.dataflow.jvm.ap.ifds.JIRLocalAliasAnalysis.AliasApInfo
import org.opentaint.ir.api.jvm.cfg.JIRInst

data class AliasedFact(val fact: FinalFactAp, val alias: AliasApInfo?)

object FactUtils {
    /**
     * @return Pair of lists, where left one has everything relevant to `prefix`, and right one has everything else.
     */
    fun splitByPrefix(prefix: List<Accessor>, fact: FinalFactAp): Pair<List<FinalFactAp>, List<FinalFactAp>> {
        if (prefix.isEmpty()) return listOf(fact) to emptyList()
        
        val head = prefix.first()
        val tail = prefix.drop(1)
        if (tail.isEmpty()) {
            if (fact.startsWithAccessor(AnyAccessor)) {
                val factAfterAny = fact.readAccessor(AnyAccessor)
                    ?: error("Impossible")

                val clearHeadAfterAny = factAfterAny.clearAccessor(head)
                val remainingAfterAny = clearHeadAfterAny?.prependAccessor(AnyAccessor)

                val withHeadAfterAny = factAfterAny.readAccessor(head)
                val prefixAfterAny = withHeadAfterAny?.prependAccessor(head)?.prependAccessor(AnyAccessor)

                val factWithoutAny = fact.clearAccessor(AnyAccessor)
                val remainingWithoutAny = factWithoutAny?.clearAccessor(head)
                val prefixWithoutAny = factWithoutAny?.readAccessor(head)?.prependAccessor(head)

                val prefixFact = listOfNotNull(prefixAfterAny, prefixWithoutAny)
                val remainingFact = listOfNotNull(remainingAfterAny, remainingWithoutAny)

                return prefixFact to remainingFact
            }

            if (!fact.startsWithAccessor(head)) {
                return emptyList<FinalFactAp>() to listOf(fact)
            }

            val remainingFact = listOfNotNull(fact.clearAccessor(head))
            val prefixFact = fact.readAccessor(head)?.prependAccessor(head)

            return listOfNotNull(prefixFact) to remainingFact
        }

        val child = fact.readAccessor(head)
            ?: return emptyList<FinalFactAp>() to listOf(fact)

        val headRemaining = listOfNotNull(fact.clearAccessor(head))
        val (tailprefix, tailRemaining) = splitByPrefix(tail, child)
        val prefixRelated = tailprefix.map { it.prependAccessor(head) }
        val remaining = headRemaining + tailRemaining.map { it.prependAccessor(head) }

        return prefixRelated to remaining
    }

    private fun rewriteForBase(fact: FinalFactAp, alias: AliasApInfo, newBase: AccessPathBase): AliasedFact {
        val newFact = alias.accessors.fold(fact.rebase(newBase) as FinalFactAp?) { f, accessor ->
            f?.readAccessor(accessor.apAccessor())
        }
        check(newFact != null) { "Aliased fact did not contain all alias accessors!" }
        return AliasedFact(newFact, alias)
    }

    fun rewriteForAlias(fact: FinalFactAp, alias: AliasApInfo?): FinalFactAp {
        if (alias == null) return fact
        val newFact = alias.accessors.foldRight(fact.rebase(alias.base)) { accessor, f ->
            f.prependAccessor(accessor.apAccessor())
        }
        return newFact
    }

    private fun splitByMustAlias(
        fact: FinalFactAp,
        mustAlias: AliasApInfo,
    ): Pair<List<FinalFactAp>, List<FinalFactAp>> {
        if (fact.base != mustAlias.base) {
            return emptyList<FinalFactAp>() to listOf(fact)
        }
        val aliasAccessors = mustAlias.accessors.map { it.apAccessor() }
        return splitByPrefix(aliasAccessors, fact)
    }

    fun splitFactByBaseMustAlias(
        aliasAnalysis: JIRLocalAliasAnalysis?,
        statement: JIRInst,
        relevantBase: AccessPathBase,
        fact: FinalFactAp,
        includeOriginal: Boolean
    ): Pair<List<AliasedFact>, List<FinalFactAp>> {
        val aliases = aliasAnalysis?.getMustAliases(statement, relevantBase).orEmpty()
        var irrelevantFacts = listOf(fact)
        val aliasedFacts = mutableListOf<AliasedFact>()
        // hack for uniform calls in fieldWrite
        if (includeOriginal) aliasedFacts.add(AliasedFact(fact, null))

        aliases.forEach { alias ->
            val left = mutableListOf<FinalFactAp>()
            irrelevantFacts.forEach { fact ->
                val (aliased, irrelevant) = splitByMustAlias(fact, alias)
                left.addAll(irrelevant)
                aliased.forEach { aliasedFact ->
                    val rebasedFact = rewriteForBase(aliasedFact, alias, relevantBase)
                    aliasedFacts.add(rebasedFact)
                }
            }
            irrelevantFacts = left
        }

        // no splits happened, nothing was aliased, no irrelevant expected
        if (irrelevantFacts.isNotEmpty() && irrelevantFacts.first() === fact) {
            irrelevantFacts = emptyList()
        }

        return aliasedFacts to irrelevantFacts
    }

    fun splitFactMultipleBases(
        aliasAnalysis: JIRLocalAliasAnalysis?,
        statement: JIRInst,
        relevantBases: List<AccessPathBase>,
        fact: FinalFactAp,
        includeOriginal: Boolean
    ): Pair<List<AliasedFact>, List<FinalFactAp>> {
        var irrelevantFacts = listOf(fact)
        val aliasedFacts = mutableListOf<AliasedFact>()
        if (includeOriginal) aliasedFacts.add(AliasedFact(fact, null))

        relevantBases.forEach { base ->
            val newIrrelevant = mutableListOf<FinalFactAp>()
            irrelevantFacts.forEach { fact ->
                val (aliased, irrelevant) =
                    splitFactByBaseMustAlias(aliasAnalysis, statement, base, fact, false)
                aliasedFacts.addAll(aliased)
                newIrrelevant.addAll(irrelevant)
            }
            irrelevantFacts = newIrrelevant
        }
        if (irrelevantFacts.size == 1 && irrelevantFacts.first() === fact) {
            irrelevantFacts = emptyList()
        }

        return aliasedFacts to irrelevantFacts
    }
}
