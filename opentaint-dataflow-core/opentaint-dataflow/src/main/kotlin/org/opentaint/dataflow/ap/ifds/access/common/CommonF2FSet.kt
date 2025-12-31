package org.opentaint.dataflow.ap.ifds.access.common

import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.ap.ifds.ExclusionSet
import org.opentaint.dataflow.ap.ifds.MethodAnalyzerEdges.EdgeStorage
import org.opentaint.dataflow.ap.ifds.access.FinalFactAp
import org.opentaint.dataflow.ap.ifds.access.InitialFactAp
import org.opentaint.dataflow.ap.ifds.access.MethodEdgesInitialToFinalApSet
import org.opentaint.dataflow.util.collectToListWithPostProcess
import org.opentaint.ir.api.common.cfg.CommonInst

abstract class CommonF2FSet<IAP, FAP>(
    private val initialStatement: CommonInst
): MethodEdgesInitialToFinalApSet, InitialApAccess<IAP>, FinalApAccess<FAP> {

    data class AccessWithExclusion<FAP>(val access: FAP, val exclusion: ExclusionSet)

    interface ApStorage<IAP, FAP> {
        fun add(statement: CommonInst, initial: IAP, final: AccessWithExclusion<FAP>): AccessWithExclusion<FAP>?
        fun filter(dst: MutableList<Pair<IAP, AccessWithExclusion<FAP>>>, statement: CommonInst, finalPattern: IAP)
        fun filter(dst: MutableList<AccessWithExclusion<FAP>>, statement: CommonInst, initial: IAP, finalPattern: IAP)
    }

    abstract fun createApStorage(): ApStorage<IAP, FAP>

    private val storage = ExitFactBaseStorage()

    override fun add(
        statement: CommonInst,
        initialAp: InitialFactAp,
        finalAp: FinalFactAp,
    ): Pair<InitialFactAp, FinalFactAp>? {
        check(initialAp.exclusions == finalAp.exclusions) { "Edge exclusion mismatch" }

        val edgeStorage = storage.getOrCreate(finalAp.base).getOrCreate(initialAp.base)

        val final = AccessWithExclusion(getFinalAccess(finalAp), finalAp.exclusions)
        val addedAccessWithExclusion = edgeStorage.add(statement, getInitialAccess(initialAp), final)
            ?: return null

        if (addedAccessWithExclusion === final) return initialAp to finalAp

        val newInitialAp = createInitial(initialAp.base, getInitialAccess(initialAp), addedAccessWithExclusion.exclusion)

        val newExitAp = createFinal(
            finalAp.base, addedAccessWithExclusion.access, addedAccessWithExclusion.exclusion
        )

        return newInitialAp to newExitAp
    }

    abstract fun mostAbstractPattern(base: AccessPathBase): IAP

    override fun collectApAtStatement(
        collection: MutableList<Pair<InitialFactAp, FinalFactAp>>,
        statement: CommonInst
    ) {
        storage.forEachValue { finalFactBase, finalStorage ->
            finalStorage.collect(collection, statement, finalFactPattern = null, finalFactBase)
        }
    }

    override fun collectApAtStatement(
        collection: MutableList<Pair<InitialFactAp, FinalFactAp>>,
        statement: CommonInst,
        finalFactPattern: InitialFactAp,
    ) {
        val finalFactBase = finalFactPattern.base
        val finalStorage = storage.find(finalFactBase) ?: return

        finalStorage.collect(collection, statement, finalFactPattern, finalFactBase)
    }

    private fun InitialFactBaseStorage.collect(
        collection: MutableList<Pair<InitialFactAp, FinalFactAp>>,
        statement: CommonInst,
        finalFactPattern: InitialFactAp?,
        finalFactBase: AccessPathBase,
    ) {
        val pattern = finalFactPattern?.let { getInitialAccess(it) }
            ?: mostAbstractPattern(finalFactBase)

        forEachValue { initialBase, storage ->
            collectToListWithPostProcess(
                collection,
                { storage.filter(it, statement, pattern) },
                {
                    val initialAp = createInitial(initialBase, it.first, it.second.exclusion)
                    val finalAp = createFinal(finalFactBase, it.second.access, it.second.exclusion)
                    initialAp to finalAp
                }
            )
        }
    }

    override fun collectApAtStatement(
        collection: MutableList<FinalFactAp>,
        statement: CommonInst,
        initialAp: InitialFactAp,
        finalFactPattern: InitialFactAp,
    ) {
        val finalFactBase = finalFactPattern.base
        val finalStorage = storage.find(finalFactBase) ?: return

        val initialFactBase = initialAp.base
        val factStorage = finalStorage.find(initialFactBase) ?: return

        collectToListWithPostProcess(
            collection,
            { factStorage.filter(it, statement, getInitialAccess(initialAp), getInitialAccess(finalFactPattern)) },
            { createFinal(finalFactBase, it.access, it.exclusion) }
        )
    }

    private inner class InitialFactBaseStorage : EdgeStorage<ApStorage<IAP, FAP>>(initialStatement) {
        override fun createStorage(): ApStorage<IAP, FAP> = createApStorage()
    }

    private inner class ExitFactBaseStorage : EdgeStorage<InitialFactBaseStorage>(initialStatement) {
        override fun createStorage() = InitialFactBaseStorage()
    }
}
