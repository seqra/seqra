package org.opentaint.jvm.sast.project.spring

import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.ap.ifds.ClassStaticAccessor
import org.opentaint.dataflow.ap.ifds.access.FactAp
import org.opentaint.dataflow.ap.ifds.access.InitialFactAp
import org.opentaint.dataflow.configuration.jvm.ActionPosition
import org.opentaint.dataflow.configuration.jvm.Argument
import org.opentaint.dataflow.configuration.jvm.ClassStatic
import org.opentaint.dataflow.configuration.jvm.CopyAllMarks
import org.opentaint.dataflow.configuration.jvm.Position
import org.opentaint.dataflow.configuration.jvm.PositionAccessor
import org.opentaint.dataflow.configuration.jvm.PositionWithAccess
import org.opentaint.dataflow.configuration.jvm.RemoveAllMarks
import org.opentaint.dataflow.configuration.jvm.Result
import org.opentaint.dataflow.configuration.jvm.TaintCleaner
import org.opentaint.dataflow.configuration.jvm.TaintEntryPointSource
import org.opentaint.dataflow.configuration.jvm.TaintMethodEntrySink
import org.opentaint.dataflow.configuration.jvm.TaintMethodExitSink
import org.opentaint.dataflow.configuration.jvm.TaintMethodExitSource
import org.opentaint.dataflow.configuration.jvm.TaintMethodSink
import org.opentaint.dataflow.configuration.jvm.TaintMethodSource
import org.opentaint.dataflow.configuration.jvm.TaintPassThrough
import org.opentaint.dataflow.configuration.jvm.TaintStaticFieldSource
import org.opentaint.dataflow.configuration.jvm.This
import org.opentaint.dataflow.configuration.mkTrue
import org.opentaint.dataflow.jvm.ap.ifds.taint.TaintRulesProvider
import org.opentaint.ir.api.common.CommonMethod
import org.opentaint.ir.api.common.cfg.CommonInst
import org.opentaint.ir.api.jvm.JIRField
import org.opentaint.ir.api.jvm.JIRMethod

class SpringRuleProvider(
    private val base: TaintRulesProvider,
    private val springCtx: SpringWebProjectContext,
) : TaintRulesProvider by base {
    override fun entryPointRulesForMethod(method: CommonMethod, statement: CommonInst, fact: FactAp?, allRelevant: Boolean): Iterable<TaintEntryPointSource> {
        if (method is SpringGeneratedMethod) return emptyList()
        return base.entryPointRulesForMethod(method, statement, fact, allRelevant)
    }

    override fun sourceRulesForMethod(method: CommonMethod, statement: CommonInst, fact: FactAp?, allRelevant: Boolean): Iterable<TaintMethodSource> {
        if (method is SpringGeneratedMethod) return emptyList()
        return base.sourceRulesForMethod(method, statement, fact, allRelevant)
    }

    override fun exitSourceRulesForMethod(
        method: CommonMethod,
        statement: CommonInst,
        fact: FactAp?,
        allRelevant: Boolean
    ): Iterable<TaintMethodExitSource> {
        if (method is SpringGeneratedMethod) return emptyList()
        return base.exitSourceRulesForMethod(method, statement, fact, allRelevant)
    }

    override fun sinkRulesForMethod(method: CommonMethod, statement: CommonInst, fact: FactAp?, allRelevant: Boolean): Iterable<TaintMethodSink> {
        if (method is SpringGeneratedMethod) return emptyList()
        return base.sinkRulesForMethod(method, statement, fact, allRelevant)
    }

    override fun sinkRulesForMethodEntry(method: CommonMethod, statement: CommonInst, fact: FactAp?, allRelevant: Boolean): Iterable<TaintMethodEntrySink> {
        if (method is SpringGeneratedMethod) return emptyList()
        return base.sinkRulesForMethodEntry(method, statement, fact, allRelevant)
    }

    override fun cleanerRulesForMethod(
        method: CommonMethod,
        statement: CommonInst,
        fact: FactAp?,
        allRelevant: Boolean
    ): Iterable<TaintCleaner> {
        if (method is SpringGeneratedMethod) {
            if (method.name != GeneratedSpringControllerDispatcherCleanupMethod) {
                return emptyList()
            }

            val currentFact = fact ?: return emptyList()
            val cleanupPositions = currentFact.cleanupPositions() ?: return emptyList()

            val cleaner = TaintCleaner(
                method, mkTrue(),
                cleanupPositions.map { RemoveAllMarks(ActionPosition.Exact(it)) },
                info = null
            )

            return listOf(cleaner)
        }

        return base.cleanerRulesForMethod(method, statement, fact, allRelevant)
    }

    private fun FactAp.cleanupPositions(): List<Position>? = when (val b = base) {
        is AccessPathBase.Argument -> listOf(Argument(b.idx))
        is AccessPathBase.ClassStatic -> getStartAccessors()
            .filterIsInstance<ClassStaticAccessor>()
            .filterNot { it.typeName == GeneratedSpringRegistry }
            .map { ClassStatic(it.typeName) }
            .takeIf { it.isNotEmpty() }

        is AccessPathBase.Constant -> null
        is AccessPathBase.Exception -> null
        is AccessPathBase.LocalVar -> null
        is AccessPathBase.Return -> listOf(Result)
        is AccessPathBase.This -> listOf(This)
    }

    override fun sourceRulesForStaticField(field: JIRField, statement: CommonInst, fact: FactAp?, allRelevant: Boolean): Iterable<TaintStaticFieldSource> {
        if (field is SpringGeneratedField) return emptyList()
        return base.sourceRulesForStaticField(field, statement, fact, allRelevant)
    }

    override fun passTroughRulesForMethod(
        method: CommonMethod,
        statement: CommonInst?,
        fact: FactAp?,
        allRelevant: Boolean
    ): Iterable<TaintPassThrough> {
        if (method is SpringGeneratedMethod) return emptyList()
        val rules = base.passTroughRulesForMethod(method, statement, fact, allRelevant).toList()

        val springRepoMethodInfo = springCtx.springRepositoryMethods[method]
            ?: return rules

        val repoActions = springRepoMethodInfo.actions()
            ?: return rules

        val repoRule = TaintPassThrough(method, mkTrue(), repoActions, info = null)
        return rules + repoRule
    }

    private fun RepositoryMethodInfo.actions(): List<CopyAllMarks>? {
        val actions = mutableListOf<CopyAllMarks>()
        val repoPos = PositionWithAccess(This, repositoryContent)
        actions += CopyAllMarks(ActionPosition.Exact(This), ActionPosition.Exact(This))

        when (kind) {
            SpringRepoQueryKind.SAVE -> {
                val entityPos = Argument(0)

                when (type) {
                    // todo: support reactive types
                    is SpringRepoQueryReturn.Reactive -> return null
                    is SpringRepoQueryReturn.Unknown -> return null
                    is SpringRepoQueryReturn.Primitive,
                    is SpringRepoQueryReturn.Single -> {
                        actions += CopyAllMarks(from = ActionPosition.Exact(entityPos), to = ActionPosition.Exact(repoPos))
                    }

                    is SpringRepoQueryReturn.Iterable -> {
                        actions += CopyAllMarks(
                            from = ActionPosition.Exact(PositionWithAccess(entityPos, iterableElement)),
                            to = ActionPosition.Exact(repoPos)
                        )
                    }
                }

                actions += CopyAllMarks(from = ActionPosition.Exact(entityPos), to = ActionPosition.Exact(Result))
            }

            SpringRepoQueryKind.FIND ->
                when (type) {
                    // todo: support reactive types
                    is SpringRepoQueryReturn.Reactive -> return null
                    is SpringRepoQueryReturn.Unknown -> return null

                    // do nothing
                    is SpringRepoQueryReturn.Primitive -> {}

                    is SpringRepoQueryReturn.Entity -> {
                        actions += CopyAllMarks(from = ActionPosition.Exact(repoPos), to = ActionPosition.Exact(Result))
                    }

                    is SpringRepoQueryReturn.Iterable -> {
                        actions += CopyAllMarks(
                            from = ActionPosition.Exact(repoPos),
                            to = ActionPosition.Exact(PositionWithAccess(Result, iterableElement))
                        )
                    }

                    is SpringRepoQueryReturn.Optional -> {
                        actions += CopyAllMarks(
                            from = ActionPosition.Exact(repoPos),
                            to = ActionPosition.Exact(PositionWithAccess(Result, optionalElement))
                        )
                    }
                }

            SpringRepoQueryKind.OTHER -> return null
        }

        return actions.takeIf { it.isNotEmpty() }
    }

    override fun sinkRulesForMethodExit(
        method: CommonMethod,
        statement: CommonInst,
        fact: FactAp?,
        initialFacts: Set<InitialFactAp>?,
        allRelevant: Boolean
    ): Iterable<TaintMethodExitSink> {
        if (method is SpringGeneratedMethod) return emptyList()
        if (method !is JIRMethod || !method.isSpringControllerMethod()) {
            return base.sinkRulesForMethodExit(method, statement, fact, initialFacts, allRelevant)
        }

        // Pass initialFacts = null for controller-return sinks to bypass the Z2F gate in
        // JIRMethodExitRuleProvider (which drops exit rules when initialFacts is non-empty).
        // Controller-return XSS sinks must still fire on F2F edges, i.e. STORED / second-order
        // flows where taint enters the GET handler as an initial fact (e.g. POST writes tainted
        // data into a repository, GET returns repo.findById(...)). This reproduces the load-bearing
        // null bypass of the removed unfoldSpringExitObject hack; the $VAR* stars in the rules now
        // handle the any-field widening that the deleted ContainsMarkRewriter used to do.
        return base.sinkRulesForMethodExit(method, statement, fact, initialFacts = null, allRelevant)
    }

    companion object {
        private const val javaObject = "java.lang.Object"

        private val iterableElement = PositionAccessor.FieldAccessor(
            className = "java.lang.Iterable",
            fieldName = "Element",
            fieldType = javaObject,
        )

        private val optionalElement = PositionAccessor.FieldAccessor(
            className = "java.util.Optional",
            fieldName = "Element",
            fieldType = javaObject,
        )

        private val repositoryContent = PositionAccessor.FieldAccessor(
            className = javaObject,
            fieldName = "__repo__",
            fieldType = javaObject,
        )
    }
}
