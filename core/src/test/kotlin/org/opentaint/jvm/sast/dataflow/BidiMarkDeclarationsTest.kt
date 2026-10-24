package org.opentaint.jvm.sast.dataflow

import org.opentaint.dataflow.ap.ifds.access.FactAp
import org.opentaint.dataflow.ap.ifds.access.InitialFactAp
import org.opentaint.dataflow.bidi.ap.MarkGroupKey
import org.opentaint.dataflow.bidi.ap.RawMarkDeclarations
import org.opentaint.dataflow.bidi.ap.encodeGroup
import org.opentaint.dataflow.configuration.jvm.*
import org.opentaint.dataflow.configuration.jvm.serialized.*
import org.opentaint.dataflow.jvm.ap.ifds.taint.PrimitiveTaintExt
import org.opentaint.dataflow.jvm.ap.ifds.taint.TaintRulesProvider
import org.opentaint.dataflow.jvm.ap.ifds.taint.forBidirectionalCore
import org.opentaint.dataflow.jvm.bidi.interp.finalizeMarkDeclarations
import org.opentaint.dataflow.jvm.util.typeName
import org.opentaint.dataflow.util.Cancellation
import org.opentaint.ir.api.common.CommonMethod
import org.opentaint.ir.api.common.cfg.CommonInst
import org.opentaint.ir.api.jvm.JIRField
import org.opentaint.ir.impl.features.classpaths.virtual.JIRVirtualMethodImpl
import org.opentaint.ir.impl.features.classpaths.virtual.JIRVirtualParameter
import org.opentaint.jvm.sast.ast.BasicTestUtils
import org.opentaint.jvm.sast.dataflow.rules.TaintConfiguration
import org.opentaint.jvm.sast.dataflow.rules.RuleResolutionStatistics
import org.opentaint.jvm.sast.project.spring.SpringRuleProvider
import org.opentaint.jvm.sast.project.spring.SpringWebProjectContext
import org.opentaint.jvm.sast.rules.JIRSemgrepRuleProvider
import org.opentaint.semgrep.pattern.TaintRuleFromSemgrep
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

class BidiMarkDeclarationsTest : BasicTestUtils() {
    override val sourceFileExtension = "java"
    private val position = PositionBaseWithModifiers.BaseOnly(PositionBase.Argument(999))
    private val function = SerializedFunctionNameMatcher.Simple(
        SerializedSimpleNameMatcher.Simple("not.loaded"),
        SerializedSimpleNameMatcher.Simple("NoClass"),
        SerializedSimpleNameMatcher.Simple("noMethod"),
    )
    private fun contains(name: String) = SerializedCondition.ContainsMark(name, position)
    private fun assign(name: String) = SerializedTaintAssignAction(name, pos = position)

    @Test fun `all nine raw roles include inactive negative metadata and disabled action names without binding`() {
        val info = object : UserDefinedRuleInfo {
            override val relevantTaintMarks = setOf("sink metadata")
        }
        val rawCondition = SerializedCondition.Or(listOf(
            SerializedCondition.And(listOf(SerializedCondition.Not(SerializedCondition.True), contains("false branch"))),
            SerializedCondition.Not(contains("negative")),
        ))
        val config = TaintConfiguration(cp, RuleResolutionStatistics())
        config.loadConfig(SerializedTaintConfig(
            entryPoint = listOf(SerializedRule.EntryPoint(function, condition = contains("entry condition"), taint = listOf(assign("entry output")))),
            source = listOf(SerializedRule.Source(function, condition = rawCondition, taint = listOf(assign("source output")))),
            methodExitSource = listOf(SerializedRule.MethodExitSource(function, condition = contains("exit source condition"), taint = listOf(assign("exit source output")))),
            sink = listOf(SerializedRule.Sink(function, condition = contains("sink condition"),
                trackFactsReachAnalysisEnd = listOf(assign("ignored end")), info = info)),
            passThrough = listOf(SerializedRule.PassThrough(function, condition = contains("pass condition"), copy = listOf(
                SerializedTaintPassAction("copied", position, position), SerializedTaintPassAction(null, position, position)))),
            cleaner = listOf(SerializedRule.Cleaner(function, condition = contains("clean condition"), cleans = listOf(
                SerializedTaintCleanAction("cleaned", position), SerializedTaintCleanAction(null, position)))),
            methodExitSink = listOf(SerializedRule.MethodExitSink(function, condition = contains("exit sink condition"),
                trackFactsReachAnalysisEnd = listOf(assign("ignored exit end")))),
            methodEntrySink = listOf(SerializedRule.MethodEntrySink(function, condition = contains("entry sink condition"),
                trackFactsReachAnalysisEnd = listOf(assign("ignored entry end")))),
            staticFieldSource = listOf(SerializedFieldRule.SerializedStaticFieldSource(
                SerializedTypeNameMatcher.ClassPattern(SerializedSimpleNameMatcher.Simple("not.loaded"), SerializedSimpleNameMatcher.Simple("NoClass")),
                SerializedSimpleNameMatcher.Simple("field"), contains("static condition"), listOf(assign("static output")))),
        ))
        val expected = setOf("entry condition", "entry output", "false branch", "negative", "source output",
            "exit source condition", "exit source output", "sink condition", "ignored end", "sink metadata",
            "pass condition", "copied", "clean condition", "cleaned", "exit sink condition", "ignored exit end",
            "entry sink condition", "ignored entry end", "static condition", "static output")
        val declarations = config.rawMarkDeclarations()
        assertEquals(expected, declarations.names)
        assertTrue(declarations.explicitOwners.isEmpty())
        assertTrue(declarations.primitiveByOwner.isEmpty())
        assertBoundCachesEmpty(config)
        assertFailsWith<UnsupportedOperationException> { (declarations.names as MutableSet).add("not metadata") }
    }

    @Test fun `provider reads current detached metadata after configuration loading`() {
        val config = TaintConfiguration(cp, RuleResolutionStatistics())
        val provider = JIRTaintRulesProvider(config)
        val before = provider.markDeclarations
        config.loadConfig(SerializedTaintConfig(source = listOf(SerializedRule.Source(function,
            taint = listOf(assign("loaded after provider construction"))))))
        assertTrue(before.names.isEmpty())
        assertEquals(setOf("loaded after provider construction"), provider.markDeclarations.names)
        assertEquals(provider.markDeclarations, provider.forBidirectionalCore().markDeclarations)
        assertBoundCachesEmpty(config)
    }

    @Test fun `full wrapper stack retains declarations from selected ignored and sidecar-only branches`() {
        val full = "generated${PrimitiveTaintExt.PRIMITIVE_TRACKING_ENABLED_MODE}"
        val outer = "outer#join"
        val rule = TaintRuleFromSemgrep<SerializedItem>(outer,
            TaintRuleFromSemgrep.Structure.Matching(emptyList()),
            markOwners = mapOf(full to outer), primitiveByOwner = mapOf(outer to true))
        val config = TaintConfiguration(cp, RuleResolutionStatistics())
        config.loadConfig(SerializedTaintConfig(sink = listOf(SerializedRule.Sink(function,
            condition = contains("inactive"), trackFactsReachAnalysisEnd = listOf(assign("disabled end declaration"))))))
        val semgrep = JIRSemgrepRuleProvider(listOf(rule), config)
        semgrep.selectRules(emptySet())
        // This child references a generated name before any fallback ownership exists.
        val other = object : TaintRulesProvider by MissingDeclarations {
            override val markDeclarations = RawMarkDeclarations(setOf(full, "ignored branch"), emptyMap(), emptyMap())
        }
        val method = findMethod("test.samples.bidi.BidiAcceptanceSample", "mayPass")
        val spring = SpringWebProjectContext(setOf(method), cp)
        for (mode in JIRCombinedTaintRulesProvider.CombinationMode.entries) {
            val combined = JIRCombinedTaintRulesProvider(semgrep.forBidirectionalCore(), other,
                JIRCombinedTaintRulesProvider.CombinationOptions(mode, mode, mode, mode, mode))
            val stack = StringConcatRuleProvider(SpringRuleProvider(JIRMethodExitRuleProvider(combined), spring))
            val raw = stack.forBidirectionalCore().markDeclarations
            assertEquals(setOf("inactive", "disabled end declaration", full, "ignored branch"), raw.names)
            assertEquals(mapOf<String, MarkGroupKey>(full to MarkGroupKey.Rule(outer)), raw.explicitOwners)
            assertEquals(mapOf<MarkGroupKey, Boolean>(MarkGroupKey.Rule(outer) to true), raw.primitiveByOwner)
            val finalized = finalizeMarkDeclarations(raw, Cancellation())
            assertEquals(encodeGroup(MarkGroupKey.Rule(outer), true), finalized.fullKey(full).group)
            assertEquals(encodeGroup(MarkGroupKey.SharedModel, false), finalized.fullKey("ignored branch").group)
        }
        assertBoundCachesEmpty(config)
    }

    @Test fun `missing provider metadata and missing loaded rule flag fail explicitly`() {
        assertFailsWith<IllegalStateException> { MissingDeclarations.markDeclarations }
        val rule = TaintRuleFromSemgrep<SerializedItem>("missing flag", TaintRuleFromSemgrep.Structure.Matching(emptyList()))
        assertFailsWith<IllegalArgumentException> { JIRSemgrepRuleProvider(listOf(rule), TaintConfiguration(cp)) }
    }

    @Test fun `StringConcat warm and cold producers retain the same condition actions and ordinals`() {
        val factory = findClass("java.lang.invoke.StringConcatFactory")
        fun signature(types: List<String>, descriptor: String) = JIRVirtualMethodImpl(
            name = "makeConcatWithConstants",
            returnType = "java.lang.String".typeName(),
            parameters = types.mapIndexed { index, type -> JIRVirtualParameter(index, type.typeName()) },
            description = descriptor,
        ).also { it.bind(factory) }
        val earlier = signature(listOf("java.lang.String"), "(Ljava/lang/String;)Ljava/lang/String;")
        val actual = signature(listOf("int", "java.lang.String"), "(ILjava/lang/String;)Ljava/lang/String;")
        val noPassRules = object : TaintRulesProvider by MissingDeclarations {
            override val markDeclarations = RawMarkDeclarations.EMPTY
            override fun passTroughRulesForMethod(method: CommonMethod, statement: CommonInst?, fact: FactAp?, allRelevant: Boolean) =
                emptyList<TaintPassThrough>()
        }
        val warm = StringConcatRuleProvider(noPassRules)
        warm.passTroughRulesForMethod(earlier, null, null, false).toList()
        val cached = warm.passTroughRulesForMethod(actual, null, null, false).single()
        val cold = StringConcatRuleProvider(noPassRules).passTroughRulesForMethod(actual, null, null, false).single()
        assertNotEquals(earlier.description, actual.description)
        assertSame(earlier, cached.method)
        assertSame(actual, cold.method)
        assertEquals(cold.condition, cached.condition)
        val expected = (0..20).map { CopyAllMarks(Argument(it), Result) }
        assertEquals(expected, cached.actionsAfter)
        assertEquals(expected, cold.actionsAfter)
        assertEquals(cold.actionsAfter.withIndex().toList(), cached.actionsAfter.withIndex().toList())
        assertEquals(RawMarkDeclarations.EMPTY, warm.markDeclarations)
    }

    private fun assertBoundCachesEmpty(config: TaintConfiguration) {
        val statistics = checkNotNull(config.resolutionStatistics)
        assertEquals(0L, statistics.cacheQueries, "Declaration collection must not query any bound-role cache/view")
        assertEquals(0L, statistics.binderCalls, "Declaration collection must not enter a method binder")
        assertEquals(0L, statistics.contextVisits, "Declaration collection must not enumerate binding contexts")
    }

    /** Any accidental bound-role lookup fails this metadata-only test immediately. */
    private object MissingDeclarations : TaintRulesProvider {
        override fun entryPointRulesForMethod(method: CommonMethod, statement: CommonInst, fact: FactAp?, allRelevant: Boolean): Iterable<TaintEntryPointSource> = error("Unexpected binding")
        override fun sourceRulesForMethod(method: CommonMethod, statement: CommonInst, fact: FactAp?, allRelevant: Boolean): Iterable<TaintMethodSource> = error("Unexpected binding")
        override fun exitSourceRulesForMethod(method: CommonMethod, statement: CommonInst, fact: FactAp?, allRelevant: Boolean): Iterable<TaintMethodExitSource> = error("Unexpected binding")
        override fun sinkRulesForMethod(method: CommonMethod, statement: CommonInst, fact: FactAp?, allRelevant: Boolean): Iterable<TaintMethodSink> = error("Unexpected binding")
        override fun sinkRulesForMethodEntry(method: CommonMethod, statement: CommonInst, fact: FactAp?, allRelevant: Boolean): Iterable<TaintMethodEntrySink> = error("Unexpected binding")
        override fun sinkRulesForMethodExit(method: CommonMethod, statement: CommonInst, fact: FactAp?, initialFacts: Set<InitialFactAp>?, allRelevant: Boolean): Iterable<TaintMethodExitSink> = error("Unexpected binding")
        override fun sourceRulesForStaticField(field: JIRField, statement: CommonInst, fact: FactAp?, allRelevant: Boolean): Iterable<TaintStaticFieldSource> = error("Unexpected binding")
        override fun passTroughRulesForMethod(method: CommonMethod, statement: CommonInst?, fact: FactAp?, allRelevant: Boolean): Iterable<TaintPassThrough> = error("Unexpected binding")
        override fun cleanerRulesForMethod(method: CommonMethod, statement: CommonInst, fact: FactAp?, allRelevant: Boolean): Iterable<TaintCleaner> = error("Unexpected binding")
        override fun selectRules(ruleIds: Set<String>) = Unit
    }
}
