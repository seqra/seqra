package org.opentaint.semgrep

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test

class GoSampleBasedTest: GoSampleBasedTestBase("GO_SAMPLES_DIR") {
    @Test fun matchingRule() = runSample("MatchingRule")

    @Test fun matchingRuleWithSink() = runSample("MatchingRuleWithSink")

    @Test fun simpleSourceSink() = runSample("SimpleSourceSink")

    @Test fun passThrough() = runSample("PassThrough")

    @Test fun methodInterfaceSourceAliasReboxing() = runSample("MethodInterfaceSourceAliasReboxing")

    @Test fun sanitizer() = runSample("Sanitizer")

    @Test fun multiArgSink() = runSample("MultiArgSink")

    @Test fun interprocSink() = runSample("InterprocSink")

    @Test fun ellipsisSourceSink() = runSample("EllipsisSourceSink")

    @Test fun ssrfClientGet() = runSample("SsrfClientGet")

    @Test fun sstiTemplateParse() = runSample("SstiTemplateParse")

    @Test fun joinSourceSink() = runSample("JoinSourceSink")

    @Test fun typedPatterns() = runSample("TypedPatterns")

    @Test fun constantNotTainted() = runSample("ConstantNotTainted")

    @Test fun nilCheck() = runSample("NilCheck")

    @Test fun multipleSourcesOneSink() = runSample("MultipleSourcesOneSink")

    @Disabled // todo: Fix IsType matching: consider type hierarchy
    @Test fun typeBasedSink() = runSample("TypeBasedSink")

    // ─── Cluster A: source-shape false-negative reproductions ────────────────

    @Test fun sourceUrlRawQuery() = runSample("SourceUrlRawQuery")

    @Test fun sourceRequestUri() = runSample("SourceRequestUri")

    @Test fun sourceHeaderMapIndex() = runSample("SourceHeaderMapIndex")

    @Test fun sourceFormParse() = runSample("SourceFormParse")

    @Test fun xssHeaderQueryUnescape() = runSample("XssHeaderQueryUnescape", useDefaultConfig = true)

    @Test fun xssBeegoOutputBody() = runSample("XssBeegoOutputBody", useDefaultConfig = true)

    @Test fun sqlBeegoHeaderQueryUnescape() = runSample("SqlBeegoHeaderQueryUnescape", useDefaultConfig = true)

    @Test fun cmdInjEnvSink() = runSample("CmdInjEnvSink", useDefaultConfig = true)

    @Test fun cmdTypedReceiverSink() = runSample("CmdTypedReceiverSink", useDefaultConfig = true)

    @Test fun importInsideSink() = runSample("ImportInsideSink")

    @Test fun cmdStringShellSink() = runSample("CmdStringShellSink")

    @Test fun qualifiedFieldSources() = runSample("QualifiedFieldSources")

    @Test fun xssTemplateSanitizer() = runSample("XssTemplateSanitizer")

    @Disabled // todo: two pattern-inside imports leave rand.Read bare on the v2 branch (math/rand/v2 selector = "v2")
    @Test fun weakRandomTwoImportRepro() = runSample("WeakRandomTwoImportRepro")

    @Test fun sourceBeegoCtxInput() = runSample("SourceBeegoCtxInput")

    @Test fun sourceBeegoCtxInputHeader() = runSample("SourceBeegoCtxInputHeader")

    @Test fun sourceBeegoInputParamsRange() = runSample("SourceBeegoInputParamsRange")

    @Test fun typedFieldReadSource() = runSample("TypedFieldReadSource")

    @Test fun interfaceImplSink() = runSample("InterfaceImplSink")

    @Test fun typedArgSink() = runSample("TypedArgSink")

    @Disabled // todo: support field-read sinks (issues.md #4)
    @Test fun fieldReadSink() = runSample("FieldReadSink")

    @Test fun mapValueToReceiver() = runSample("MapValueToReceiver")

    @Test fun cookieValueFieldRead() = runSample("CookieValueFieldRead")

    // Phase 3 config coverage: taint must survive the changed builtin/fmt passthroughs.
    @Test fun builtinSliceCoverage() = runSample("BuiltinSliceCoverage", useDefaultConfig = true)

    @Test fun fmtCoverage() = runSample("FmtCoverage", useDefaultConfig = true)

    @Disabled // slices.* are generic (Clone[S ~[]E, E any]); the config key does not match the
    // generic-instantiated call, so these entries were already inert before the fold (element
    // flow undetected even with the pre-fold stars). Un-disable if generic config matching lands.
    @Test fun slicesCoverage() = runSample("SlicesCoverage", useDefaultConfig = true)

    @Disabled // todo: support struct-literal field matching (issues.md #8)
    @Test fun insecureCookieLiteral() = runSample("InsecureCookieLiteral")

    @Test fun shellExecArgConstraint() = runSample("ShellExecArgConstraint")

    @Test fun sqlSinkReceiverArg() = runSample("SqlSinkReceiverArg", useDefaultConfig = true)

    @Test fun weakRandV2() = runSample("WeakRandV2")

    @Test fun importQualifiedRewrite() = runSample("ImportQualifiedRewrite")

    // ─── Ports of example.* Java sample tests ───────────────────────────────

    @Test fun javaRule() = runSample("JavaRule")

    @Test fun javaRuleWithEllipsisMethodInvocation() = runSample("JavaRuleWithEllipsisMethodInvocation")

    @Test fun javaRuleWithPatternInside() = runSample("JavaRuleWithPatternInside")

    @Test fun javaRuleWithNotInsidePrefix() = runSample("JavaRuleWithNotInsidePrefix")

    @Test fun javaRuleWithNotInsideSuffix() = runSample("JavaRuleWithNotInsideSuffix")

    @Test fun javaRuleWithIntersection() = runSample("JavaRuleWithIntersection")

    @Test fun javaRuleWithSimplePass() = runSample("JavaRuleWithSimplePass")

    @Test fun javaRuleWithSeveralSuffixCleaners() = runSample("JavaRuleWithSeveralSuffixCleaners")

    @Test fun javaRuleReturnSimple() = runSample("JavaRuleReturnSimple")

    @Test fun javaRuleReturnConditional() = runSample("JavaRuleReturnConditional")

    @Test fun javaRuleReturnNotInside() = runSample("JavaRuleReturnNotInside")

    @Test fun javaRuleWithAllowedConstant() = runSample("JavaRuleWithAllowedConstant")

    @Test fun javaRuleWithMultiplePatterns() = runSample("JavaRuleWithMultiplePatterns")

    @Test fun javaCleanerAfterSink1() = runSample("JavaCleanerAfterSink1")

    @Test fun javaRuleWithoutPattern() = runSample("JavaRuleWithoutPattern")

    @Test fun javaR1() = runSample("JavaR1")

    @Test fun javaR2() = runSample("JavaR2")

    @Test fun javaR3() = runSample("JavaR3")

    @Test fun javaNdRule() = runSample("JavaNDRule")

    @Test fun javaRuleReturn1() = runSample("JavaRuleReturn1")

    @Test fun javaRuleReturn2() = runSample("JavaRuleReturn2")

    @Test fun javaRuleReturn4() = runSample("JavaRuleReturn4")

    @Test fun javaRuleReturn5() = runSample("JavaRuleReturn5")

    @Test fun javaRuleReturn6() = runSample("JavaRuleReturn6")

    @Test fun javaTrickyPatternNot() = runSample("JavaTrickyPatternNot")

    @Test fun javaCleanerAfterSink0() = runSample("JavaCleanerAfterSink0")

    @Test fun javaCleanerAfterSink2() = runSample("JavaCleanerAfterSink2")

    @Test fun javaRuleReturnChained() = runSample("JavaRuleReturnChained")

    @Test fun javaRuleReturnNotInsidePrefix() = runSample("JavaRuleReturnNotInsidePrefix")

    @Test fun javaRuleReturnMultiInsideNotInsideA() = runSample("JavaRuleReturnMultiInsideNotInsideA")

    @Test fun javaRuleReturnMultiInsideNotInsideC() = runSample("JavaRuleReturnMultiInsideNotInsideC")

    @Test fun javaRuleReturnInsideSignature() = runSample("JavaRuleReturnInsideSignature")

    @Test fun javaRuleReturnInsideSignature2() = runSample("JavaRuleReturnInsideSignature2")

    @Test fun javaRulePatternNotInsideWithSignature() = runSample("JavaRulePatternNotInsideWithSignature")

    @Test fun javaRulePatternNotWithSignature() = runSample("JavaRulePatternNotWithSignature")

    @Test fun javaRuleReturnWithNotInsideSignature() = runSample("JavaRuleReturnWithNotInsideSignature")

    @Test fun javaRuleWithSignature() = runSample("JavaRuleWithSignature")

    @Test fun javaRuleWithPatternsSignature() = runSample("JavaRuleWithPatternsSignature")

    @Test fun javaRuleWithPatternsSimple() = runSample("JavaRuleWithPatternsSimple")

    @Test fun javaRuleWithRealInsideSequence() = runSample("JavaRuleWithRealInsideSequence")

    @Test fun javaRuleWithArtificialInsideSequence() = runSample("JavaRuleWithArtificialInsideSequence")

    @Test fun javaRuleWithArtificialInsideSequenceReverse() = runSample("JavaRuleWithArtificialInsideSequenceReverse")

    @Test fun javaRuleWithMultiplePatternsUnification() = runSample("JavaRuleWithMultiplePatternsUnification")

    @Test fun javaRuleWithMultiplePatternsEllipsisUnification() = runSample("JavaRuleWithMultiplePatternsEllipsisUnification")

    @Test fun javaRuleWithType() = runSample("JavaRuleWithType")

    @Test fun javaRuleWithEllipsisInvocationAndPatternNot() = runSample("JavaRuleWithEllipsisInvocationAndPatternNot")

    @Test fun javaRuleRequiringCarefulCleaners() = runSample("JavaRuleRequiringCarefulCleaners")

    @Test fun javaRuleWithAnyPattern() = runSample("JavaRuleWithAnyPattern")

    @Test fun javaRuleWithState() = runSample("JavaRuleWithState")

    @Test fun javaRuleRequiringCarefulCleanersInTaint() = runSample("JavaRuleRequiringCarefulCleanersInTaint")

    @Test fun javaRuleWithNotInsideDistinctReturnType() = runSample("JavaRuleWithNotInsideDistinctReturnType")

    @Test fun javaJoinWithTaintAndMatchingLeft() = runSample("JavaJoinWithTaintAndMatchingLeft")

    @Test fun javaArrayExample() = runSample("JavaArrayExample")

    @Test fun javaRuleWithConcreteReturnType() = runSample("JavaRuleWithConcreteReturnType")

    @Test fun javaRuleWithConcreteReturnDiscrim() = runSample("JavaRuleWithConcreteReturnDiscrim")

    @Test fun javaRuleWithMixedMetavarConcrete() = runSample("JavaRuleWithMixedMetavarConcrete")

    @Test fun javaRuleWithDeepNesting() = runSample("JavaRuleWithDeepNesting")
}
