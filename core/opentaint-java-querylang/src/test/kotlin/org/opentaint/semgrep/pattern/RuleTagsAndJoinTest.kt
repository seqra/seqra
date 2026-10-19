package org.opentaint.semgrep.pattern

import org.opentaint.dataflow.configuration.jvm.serialized.SerializedItem
import org.opentaint.semgrep.pattern.conversion.JavaLanguageStrategy
import org.opentaint.semgrep.pattern.conversion.LanguageStrategy
import kotlin.io.path.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class RuleTagsAndJoinTest {

    private fun parse(yml: String): SemgrepYamlRuleSet {
        val trace = SemgrepFileLoadTrace("test.yaml")
        return parseSemgrepYaml(yml, trace) ?: error("parse returned null; errors=${trace.errorMessages()}")
    }

    @Test
    fun `rule tags parse into a list`() {
        val rs = parse(
            """
            rules:
              - id: tagged
                severity: NOTE
                message: m
                languages: [java]
                tags:
                  - untrusted-data-source
                  - ssrf-source
                pattern: foo()
            """.trimIndent()
        )
        assertEquals(listOf("untrusted-data-source", "ssrf-source"), rs.rules.single().tags)
    }

    @Test
    fun `rule without tags defaults to empty`() {
        val rs = parse(
            """
            rules:
              - id: untagged
                severity: NOTE
                message: m
                languages: [java]
                pattern: foo()
            """.trimIndent()
        )
        assertTrue(rs.rules.single().tags.isEmpty())
    }

    @Test
    fun `join ref accepts either rule or tag`() {
        val rs = parse(
            """
            rules:
              - id: j
                severity: ERROR
                message: m
                languages: [java]
                mode: join
                join:
                  refs:
                    - tag: untrusted-data-source
                      as: untrusted-data
                      exclude:
                        - lib.yaml#legacy-source
                        - custom.yaml#noisy-source
                    - rule: lib.yaml#sink
                      as: sink
                  on:
                    - 'untrusted-data.${'$'}X -> sink.${'$'}X'
            """.trimIndent()
        )
        val refs = rs.rules.single().join!!.refs
        assertEquals("untrusted-data-source", refs[0].tag)
        assertEquals(null, refs[0].rule)
        assertEquals(listOf("lib.yaml#legacy-source", "custom.yaml#noisy-source"), refs[0].exclude)
        assertEquals("lib.yaml#sink", refs[1].rule)
        assertEquals(null, refs[1].tag)
        assertTrue(refs[1].exclude.isEmpty())
    }

    private fun load(
        vararg files: Pair<String, String>,
        strategies: List<LanguageStrategy<*, *>> = listOf(JavaLanguageStrategy()),
        ruleIdExclude: List<String> = emptyList(),
    ): Pair<SemgrepRuleLoader.RuleLoadResult, SemgrepLoadTrace> {
        val trace = SemgrepLoadTrace()
        val loader = SemgrepRuleLoader(strategies)
        for ((path, text) in files) {
            loader.registerRuleSet(text, Path(path), Path("."), trace)
        }
        return loader.loadRules(ruleIdExclude = ruleIdExclude) to trace
    }

    private fun loadedRuleIds(r: SemgrepRuleLoader.RuleLoadResult): List<String> =
        r.rulesWithMeta.map { it.second.shortRuleId }

    private val sinkLib = "lib/sink.yaml" to """
        rules:
          - id: ssrf-sink
            options: { lib: true }
            severity: NOTE
            message: sink
            languages: [java]
            patterns:
              - pattern: sink(${'$'}X)
    """.trimIndent()

    private val servletSource = "lib/servlet.yaml" to """
        rules:
          - id: servlet-source
            options: { lib: true }
            tags: [untrusted-data-source]
            severity: NOTE
            message: src
            languages: [java]
            patterns:
              - pattern: ${'$'}X = servletSource()
    """.trimIndent()

    private val untrustedJoin = "ssrf.yaml" to """
        rules:
          - id: ssrf
            severity: ERROR
            message: m
            languages: [java]
            mode: join
            join:
              refs:
                - tag: untrusted-data-source
                  as: untrusted-data
                - rule: lib/sink.yaml#ssrf-sink
                  as: sink
              on:
                - 'untrusted-data.${'$'}X -> sink.${'$'}X'
    """.trimIndent()

    @Test
    fun `all generated join operand marks retain the outer rule owner`() {
        val (result, trace) = load(sinkLib, servletSource, untrustedJoin)
        assertTrue(trace.errorMessages().isEmpty(), trace.errorMessages().toString())
        val joined = result.rulesWithMeta.single().first
        assertEquals(mapOf(joined.ruleId to false), joined.primitiveByOwner)
        assertTrue(joined.markOwners.isNotEmpty())
        assertEquals(setOf(joined.ruleId), joined.markOwners.values.toSet())
        assertTrue(joined.markOwners.keys.any { Mark.parseMark(it).ruleId != joined.ruleId },
            "The join has marks from child conversion contexts")
        // Ownership does not rewrite the full generated name or decode it to infer the owner.
        joined.markOwners.keys.forEach { assertEquals(it, Mark.parseMark(it).taintMarkStr()) }
    }

    @Test
    fun `normal loader carries the actual outer primitive option`() {
        for (primitive in listOf(false, true)) {
            val (result, trace) = load("normal.yaml" to """
                rules:
                  - id: normal
                    severity: ERROR
                    message: m
                    languages: [java]
                    options: { primitive-tracking: $primitive }
                    mode: taint
                    pattern-sources:
                      - pattern: source()
                    pattern-sinks:
                      - pattern: sink(${'$'}X)
            """.trimIndent())
            assertTrue(trace.errorMessages().isEmpty(), trace.errorMessages().toString())
            val rule = result.rulesWithMeta.single().first
            assertEquals(mapOf(rule.ruleId to primitive), rule.primitiveByOwner)
            assertTrue(rule.markOwners.isNotEmpty())
            assertTrue(rule.markOwners.keys.all {
                (Mark.parseMark(it).modeModifier != null) == primitive
            })
        }
    }

    @Test
    fun `join loader retains its own primitive flag across child contexts`() {
        val primitiveJoin = untrustedJoin.first to untrustedJoin.second.replace(
            "id: ssrf", "id: ssrf\n    options: { primitive-tracking: true }")
        val (result, trace) = load(sinkLib, servletSource, primitiveJoin)
        assertTrue(trace.errorMessages().isEmpty(), trace.errorMessages().toString())
        val joined = result.rulesWithMeta.single().first
        assertEquals(mapOf(joined.ruleId to true), joined.primitiveByOwner)
        assertEquals(setOf(joined.ruleId), joined.markOwners.values.toSet())
        assertTrue(joined.markOwners.keys.any { Mark.parseMark(it).ruleId != joined.ruleId })
        assertTrue(joined.markOwners.keys.all { Mark.parseMark(it).modeModifier != null })
    }

    @Test
    fun `tag ref expanding to no rules is a hard error`() {
        val (_, trace) = load(sinkLib, untrustedJoin)
        assertTrue(trace.errorMessages().any { it.contains("declares that tag") }, trace.errorMessages().toString())
    }

    @Test
    fun `tag expansion skips disabled rules`() {
        val disabledSource = "lib/disabled.yaml" to """
            rules:
              - id: disabled-source
                options: { lib: true, disabled: true }
                tags: [untrusted-data-source]
                severity: NOTE
                message: src
                languages: [java]
                patterns:
                  - pattern: disabledSource()
        """.trimIndent()
        val (result, trace) = load(sinkLib, servletSource, disabledSource, untrustedJoin)
        assertTrue(trace.errorMessages().isEmpty(), trace.errorMessages().toString())
        assertTrue("ssrf" in loadedRuleIds(result), "join rule should load; loaded=${loadedRuleIds(result)}")
    }

    @Test
    fun `tag ref excludes a set of rules by id`() {
        val sources = "lib/sources.yaml" to """
            rules:
              - id: source-a
                options: { lib: true }
                tags: [untrusted-data-source]
                severity: NOTE
                message: src
                languages: [java]
                patterns: [ { pattern: ${'$'}X = sourceA() } ]
              - id: source-b
                options: { lib: true }
                tags: [untrusted-data-source]
                severity: NOTE
                message: src
                languages: [java]
                patterns: [ { pattern: ${'$'}X = sourceB() } ]
              - id: source-c
                options: { lib: true }
                tags: [untrusted-data-source]
                severity: NOTE
                message: src
                languages: [java]
                patterns: [ { pattern: ${'$'}X = sourceC() } ]
        """.trimIndent()
        val join = "j.yaml" to """
            rules:
              - id: j
                severity: ERROR
                message: m
                languages: [java]
                mode: join
                join:
                  refs:
                    - tag: untrusted-data-source
                      as: src
                      exclude:
                        - lib/sources.yaml#source-a
                        - lib/sources.yaml#source-b
                    - rule: lib/sink.yaml#ssrf-sink
                      as: sink
                  on:
                    - 'src.${'$'}X -> sink.${'$'}X'
        """.trimIndent()

        val (result, trace) = load(sources, sinkLib, join)
        assertTrue(trace.errorMessages().isEmpty(), trace.errorMessages().toString())
        val root = assertIs<TaintRuleFromSemgrep.Structure.Join<*>>(result.rulesWithMeta.single().first.root)
        assertEquals(
            setOf("lib/sources.yaml:source-c"),
            root.branches.flatMap { it.leftOperands }.map { it.ruleId }.toSet(),
        )

        val (emptyResult, emptyTrace) = load(
            sources, sinkLib, join,
            ruleIdExclude = listOf("lib/sources.yaml:source-c"),
        )
        assertTrue(emptyResult.rulesWithMeta.isEmpty())
        assertTrue(emptyTrace.errorMessages().isEmpty(), emptyTrace.errorMessages().toString())
    }

    @Test
    fun `tag expanding to only disabled rules is an error`() {
        val disabledSource = "lib/disabled.yaml" to """
            rules:
              - id: disabled-source
                options: { lib: true, disabled: true }
                tags: [untrusted-data-source]
                severity: NOTE
                message: src
                languages: [java]
                patterns:
                  - pattern: disabledSource()
        """.trimIndent()
        val (_, trace) = load(sinkLib, disabledSource, untrustedJoin)
        assertTrue(trace.errorMessages().any { it.contains("declares that tag") }, trace.errorMessages().toString())
    }

    @Test
    fun `tag expansion is scoped to the join rule's language`() {
        val kotlinFacadeStrategy = object : LanguageStrategy<SemgrepJavaPattern, SerializedItem> by JavaLanguageStrategy() {
            override val language: String = "kotlin"
        }
        val kotlinSource = "lib/ktsrc.yaml" to """
            rules:
              - id: kt-source
                options: { lib: true }
                tags: [untrusted-data-source]
                severity: NOTE
                message: src
                languages: [kotlin]
                patterns:
                  - pattern: ktSource()
        """.trimIndent()
        val (_, trace) = load(
            sinkLib, kotlinSource, untrustedJoin,
            strategies = listOf(JavaLanguageStrategy(), kotlinFacadeStrategy)
        )
        assertTrue(trace.errorMessages().any { it.contains("declares that tag") }, trace.errorMessages().toString())
    }

    @Test
    fun `ref with neither rule nor tag errors`() {
        val (_, trace) = load(
            sinkLib,
            "j.yaml" to """
                rules:
                  - id: j
                    severity: ERROR
                    message: m
                    languages: [java]
                    mode: join
                    join:
                      refs:
                        - as: x
                        - rule: lib/sink.yaml#ssrf-sink
                          as: sink
                      on:
                        - 'x.${'$'}X -> sink.${'$'}X'
            """.trimIndent()
        )
        assertTrue(trace.errorMessages().any { it.contains("neither was given") }, trace.errorMessages().toString())
    }

    @Test
    fun `ref with both rule and tag errors`() {
        val (_, trace) = load(
            sinkLib, servletSource,
            "j.yaml" to """
                rules:
                  - id: j
                    severity: ERROR
                    message: m
                    languages: [java]
                    mode: join
                    join:
                      refs:
                        - rule: lib/servlet.yaml#servlet-source
                          tag: untrusted-data-source
                          as: x
                        - rule: lib/sink.yaml#ssrf-sink
                          as: sink
                      on:
                        - 'x.${'$'}X -> sink.${'$'}X'
            """.trimIndent()
        )
        assertTrue(trace.errorMessages().any { it.contains("both were given") }, trace.errorMessages().toString())
    }

    @Test
    fun `exclude on a rule ref errors`() {
        val (_, trace) = load(
            sinkLib, servletSource,
            "j.yaml" to """
                rules:
                  - id: j
                    severity: ERROR
                    message: m
                    languages: [java]
                    mode: join
                    join:
                      refs:
                        - rule: lib/servlet.yaml#servlet-source
                          as: src
                          exclude: [lib/servlet.yaml#servlet-source]
                        - rule: lib/sink.yaml#ssrf-sink
                          as: sink
                      on:
                        - 'src.${'$'}X -> sink.${'$'}X'
            """.trimIndent()
        )
        assertTrue(
            trace.errorMessages().any { it.contains("only valid with a 'tag' target") },
            trace.errorMessages().toString(),
        )
    }

    @Test
    fun `two refs sharing one alias is rejected`() {
        val (_, trace) = load(
            sinkLib, servletSource,
            "j.yaml" to """
                rules:
                  - id: j
                    severity: ERROR
                    message: m
                    languages: [java]
                    mode: join
                    join:
                      refs:
                        - rule: lib/servlet.yaml#servlet-source
                          as: dup
                        - rule: lib/sink.yaml#ssrf-sink
                          as: dup
                      on:
                        - 'dup.${'$'}X -> dup.${'$'}X'
            """.trimIndent()
        )
        assertTrue(trace.errorMessages().any { it.contains("declared by more than one ref") }, trace.errorMessages().toString())
    }

    @Test
    fun `ref to a join rule errors`() {
        val (_, trace) = load(
            sinkLib,
            "inner.yaml" to """
                rules:
                  - id: inner-join
                    options: { lib: true }
                    severity: ERROR
                    message: m
                    languages: [java]
                    mode: join
                    join:
                      refs:
                        - rule: lib/sink.yaml#ssrf-sink
                          as: s
                      on:
                        - 's.${'$'}X -> s.${'$'}X'
            """.trimIndent(),
            "j.yaml" to """
                rules:
                  - id: j
                    severity: ERROR
                    message: m
                    languages: [java]
                    mode: join
                    join:
                      refs:
                        - rule: lib/sink.yaml#ssrf-sink
                          as: src
                        - rule: inner.yaml#inner-join
                          as: sink
                      on:
                        - 'src.${'$'}X -> sink.${'$'}X'
            """.trimIndent()
        )
        assertTrue(trace.errorMessages().any { it.contains("is a join rule") }, trace.errorMessages().toString())
    }

    @Test
    fun `sink joined on conflicting metavars errors`() {
        val (_, trace) = load(
            "lib/srcs.yaml" to """
                rules:
                  - id: a
                    options: { lib: true }
                    severity: NOTE
                    message: m
                    languages: [java]
                    patterns: [ { pattern: a(${'$'}X) } ]
                  - id: b
                    options: { lib: true }
                    severity: NOTE
                    message: m
                    languages: [java]
                    patterns: [ { pattern: b(${'$'}Y) } ]
            """.trimIndent(),
            sinkLib,
            "j.yaml" to """
                rules:
                  - id: j
                    severity: ERROR
                    message: m
                    languages: [java]
                    mode: join
                    join:
                      refs:
                        - rule: lib/srcs.yaml#a
                          as: a
                        - rule: lib/srcs.yaml#b
                          as: b
                        - rule: lib/sink.yaml#ssrf-sink
                          as: sink
                      on:
                        - 'a.${'$'}X -> sink.${'$'}T'
                        - 'b.${'$'}Y -> sink.${'$'}U'
            """.trimIndent()
        )
        assertTrue(trace.errorMessages().any { it.contains("conflicting metavariables") }, trace.errorMessages().toString())
    }

    @Test
    fun `multi-sink join fans a tagged source union into several sinks`() {
        val (result, trace) = load(
            "lib/cmd.yaml" to """
                rules:
                  - id: src-a
                    options: { lib: true }
                    tags: [demo-src]
                    severity: NOTE
                    message: m
                    languages: [java]
                    patterns: [ { pattern: ${'$'}V = srcA() } ]
                  - id: src-b
                    options: { lib: true }
                    tags: [demo-src]
                    severity: NOTE
                    message: m
                    languages: [java]
                    patterns: [ { pattern: ${'$'}V = srcB() } ]
                  - id: sink-x
                    options: { lib: true }
                    severity: NOTE
                    message: m
                    languages: [java]
                    patterns: [ { pattern: sinkX(${'$'}V) } ]
                  - id: sink-y
                    options: { lib: true }
                    severity: NOTE
                    message: m
                    languages: [java]
                    patterns: [ { pattern: sinkY(${'$'}V) } ]
            """.trimIndent(),
            "j.yaml" to """
                rules:
                  - id: multi-sink
                    severity: ERROR
                    message: m
                    languages: [java]
                    mode: join
                    join:
                      refs:
                        - tag: demo-src
                          as: src
                        - rule: lib/cmd.yaml#sink-x
                          as: sinkx
                        - rule: lib/cmd.yaml#sink-y
                          as: sinky
                      on:
                        - 'src.${'$'}V -> sinkx.${'$'}V'
                        - 'src.${'$'}V -> sinky.${'$'}V'
            """.trimIndent()
        )
        assertTrue(trace.errorMessages().isEmpty(), trace.errorMessages().toString())
        assertTrue("multi-sink" in loadedRuleIds(result), "join rule should load; loaded=${loadedRuleIds(result)}")
    }

    @Test
    fun `join rejects a missing left mark`() {
        val (_, trace) = load(
            "lib/marks.yaml" to """
                rules:
                  - id: src
                    options: { lib: true }
                    severity: NOTE
                    message: m
                    languages: [java]
                    patterns: [ { pattern: src(${'$'}ACTUAL) } ]
                  - id: sink
                    options: { lib: true }
                    severity: NOTE
                    message: m
                    languages: [java]
                    patterns: [ { pattern: sink(${'$'}VALUE) } ]
            """.trimIndent(),
            "j.yaml" to """
                rules:
                  - id: j
                    severity: ERROR
                    message: m
                    languages: [java]
                    mode: join
                    join:
                      refs:
                        - rule: lib/marks.yaml#src
                          as: src
                        - rule: lib/marks.yaml#sink
                          as: sink
                      on: [ 'src.${'$'}MISSING -> sink.${'$'}VALUE' ]
            """.trimIndent()
        )
        assertTrue(trace.errorMessages().any { it.contains("metavariable '${'$'}MISSING'") }, trace.errorMessages().toString())
    }

    @Test
    fun `join rejects a missing right mark`() {
        val (_, trace) = load(
            "lib/marks.yaml" to """
                rules:
                  - id: src
                    options: { lib: true }
                    severity: NOTE
                    message: m
                    languages: [java]
                    patterns: [ { pattern: src(${'$'}VALUE) } ]
                  - id: sink
                    options: { lib: true }
                    severity: NOTE
                    message: m
                    languages: [java]
                    patterns: [ { pattern: sink(${'$'}ACTUAL) } ]
            """.trimIndent(),
            "j.yaml" to """
                rules:
                  - id: j
                    severity: ERROR
                    message: m
                    languages: [java]
                    mode: join
                    join:
                      refs:
                        - rule: lib/marks.yaml#src
                          as: src
                        - rule: lib/marks.yaml#sink
                          as: sink
                      on: [ 'src.${'$'}VALUE -> sink.${'$'}MISSING' ]
            """.trimIndent()
        )
        assertTrue(trace.errorMessages().any { it.contains("metavariable '${'$'}MISSING'") }, trace.errorMessages().toString())
    }

    private fun loadRightSearchJoin(sinkPatterns: String) = load(
        "lib/marks.yaml" to """
            rules:
              - id: src
                options: { lib: true }
                severity: NOTE
                message: m
                languages: [java]
                pattern: src(${'$'}VALUE)
              - id: sink
                options: { lib: true }
                severity: NOTE
                message: m
                languages: [java]
${sinkPatterns.prependIndent("                ")}
        """.trimIndent(),
        "j.yaml" to """
            rules:
              - id: j
                severity: ERROR
                message: m
                languages: [java]
                mode: join
                join:
                  refs:
                    - rule: lib/marks.yaml#src
                      as: src
                    - rule: lib/marks.yaml#sink
                      as: sink
                  on: [ 'src.${'$'}VALUE -> sink.${'$'}VALUE' ]
        """.trimIndent()
    )

    @Test
    fun `search join accepts when every pattern-either alternative uses the mark`() {
        val (result, trace) = loadRightSearchJoin(
            """
                pattern-either:
                  - pattern: sink(${'$'}VALUE)
                  - pattern: otherSink(${'$'}VALUE)
            """.trimIndent()
        )

        assertTrue(trace.errorMessages().isEmpty(), trace.errorMessages().toString())
        assertTrue("j" in loadedRuleIds(result))
    }

    @Test
    fun `search join rejects when one pattern-either alternative omits the mark`() {
        val (_, trace) = loadRightSearchJoin(
            """
                pattern-either:
                  - pattern: sink(${'$'}VALUE)
                  - pattern: otherSink(${'$'}OTHER)
            """.trimIndent()
        )

        assertTrue(trace.errorMessages().any { it.contains("metavariable '${'$'}VALUE'") }, trace.errorMessages().toString())
    }

    @Test
    fun `search join accepts when one conjunctive pattern uses the mark`() {
        val (result, trace) = loadRightSearchJoin(
            """
                patterns:
                  - pattern: sink(${'$'}VALUE)
                  - pattern: sink(${'$'}OTHER)
            """.trimIndent()
        )

        assertTrue(trace.errorMessages().isEmpty(), trace.errorMessages().toString())
        assertTrue("j" in loadedRuleIds(result))
    }

    @Test
    fun `search focus-metavariable does not rescue a pattern missing the mark`() {
        val (_, trace) = loadRightSearchJoin(
            """
                patterns:
                  - pattern: sink(${'$'}OTHER)
                  - focus-metavariable: ${'$'}VALUE
            """.trimIndent()
        )

        assertTrue(trace.errorMessages().any { it.contains("metavariable '${'$'}VALUE'") }, trace.errorMessages().toString())
    }

    @Test
    fun `taint join rejects a sink branch focused on another mark`() {
        val (_, trace) = load(
            "lib/marks.yaml" to """
                rules:
                  - id: src
                    options: { lib: true }
                    severity: NOTE
                    message: m
                    languages: [java]
                    patterns: [ { pattern: src(${'$'}UNTRUSTED) } ]
                  - id: sink
                    options: { lib: true }
                    severity: NOTE
                    message: m
                    languages: [java]
                    mode: taint
                    pattern-sources: []
                    pattern-sinks:
                      - patterns:
                          - pattern: sink(${'$'}NAME)
                          - focus-metavariable: ${'$'}NAME
            """.trimIndent(),
            "j.yaml" to """
                rules:
                  - id: j
                    severity: ERROR
                    message: m
                    languages: [java]
                    mode: join
                    join:
                      refs:
                        - rule: lib/marks.yaml#src
                          as: src
                        - rule: lib/marks.yaml#sink
                          as: sink
                      on: [ 'src.${'$'}UNTRUSTED -> sink.${'$'}UNTRUSTED' ]
            """.trimIndent()
        )
        assertTrue(trace.errorMessages().any { it.contains("metavariable '${'$'}UNTRUSTED'") }, trace.errorMessages().toString())
    }

    @Test
    fun `taint join rejects a sink branch containing only another mark`() {
        val (_, trace) = load(
            "lib/marks.yaml" to """
                rules:
                  - id: src
                    options: { lib: true }
                    severity: NOTE
                    message: m
                    languages: [java]
                    patterns: [ { pattern: src(${'$'}UNTRUSTED) } ]
                  - id: sink
                    options: { lib: true }
                    severity: NOTE
                    message: m
                    languages: [java]
                    mode: taint
                    pattern-sources: []
                    pattern-sinks:
                      - pattern: ${'$'}C.run()
            """.trimIndent(),
            "j.yaml" to """
                rules:
                  - id: j
                    severity: ERROR
                    message: m
                    languages: [java]
                    mode: join
                    join:
                      refs:
                        - rule: lib/marks.yaml#src
                          as: src
                        - rule: lib/marks.yaml#sink
                          as: sink
                      on: [ 'src.${'$'}UNTRUSTED -> sink.${'$'}UNTRUSTED' ]
            """.trimIndent()
        )
        assertTrue(trace.errorMessages().any { it.contains("metavariable '${'$'}UNTRUSTED'") }, trace.errorMessages().toString())
    }

    @Test
    fun `taint join accepts when every sink branch focuses the joined mark`() {
        val (result, trace) = load(
            "lib/marks.yaml" to """
                rules:
                  - id: src
                    options: { lib: true }
                    severity: NOTE
                    message: m
                    languages: [java]
                    patterns: [ { pattern: src(${'$'}UNTRUSTED) } ]
                  - id: sink
                    options: { lib: true }
                    severity: NOTE
                    message: m
                    languages: [java]
                    mode: taint
                    pattern-sources: []
                    pattern-sinks:
                      - patterns:
                          - pattern: sink(${'$'}UNTRUSTED)
                          - focus-metavariable: ${'$'}UNTRUSTED
                      - patterns:
                          - pattern: otherSink(${'$'}UNTRUSTED)
                          - focus-metavariable: ${'$'}UNTRUSTED
            """.trimIndent(),
            "j.yaml" to """
                rules:
                  - id: j
                    severity: ERROR
                    message: m
                    languages: [java]
                    mode: join
                    join:
                      refs:
                        - rule: lib/marks.yaml#src
                          as: src
                        - rule: lib/marks.yaml#sink
                          as: sink
                      on: [ 'src.${'$'}UNTRUSTED -> sink.${'$'}UNTRUSTED' ]
            """.trimIndent()
        )
        assertTrue(trace.errorMessages().isEmpty(), trace.errorMessages().toString())
        assertTrue("j" in loadedRuleIds(result))
    }

    @Test
    fun `taint join accepts a sink requiring the joined label`() {
        val (result, trace) = load(
            "lib/marks.yaml" to """
                rules:
                  - id: src
                    options: { lib: true }
                    severity: NOTE
                    message: m
                    languages: [java]
                    patterns: [ { pattern: src(${'$'}UNTRUSTED) } ]
                  - id: sink
                    options: { lib: true }
                    severity: NOTE
                    message: m
                    languages: [java]
                    mode: taint
                    pattern-sources: []
                    pattern-sinks:
                      - pattern: sink()
                        requires: ${'$'}UNTRUSTED
            """.trimIndent(),
            "j.yaml" to """
                rules:
                  - id: j
                    severity: ERROR
                    message: m
                    languages: [java]
                    mode: join
                    join:
                      refs:
                        - rule: lib/marks.yaml#src
                          as: src
                        - rule: lib/marks.yaml#sink
                          as: sink
                      on: [ 'src.${'$'}UNTRUSTED -> sink.${'$'}UNTRUSTED' ]
            """.trimIndent()
        )
        assertTrue(trace.errorMessages().isEmpty(), trace.errorMessages().toString())
        assertTrue("j" in loadedRuleIds(result))
    }

    @Test
    fun `taint join rejects pattern occurrence without focus or requires`() {
        val (result, trace) = load(
            "lib/marks.yaml" to """
                rules:
                  - id: src
                    options: { lib: true }
                    severity: NOTE
                    message: m
                    languages: [java]
                    patterns: [ { pattern: src(${'$'}UNTRUSTED) } ]
                  - id: sink
                    options: { lib: true }
                    severity: NOTE
                    message: m
                    languages: [java]
                    mode: taint
                    pattern-sources: []
                    pattern-sinks:
                      - pattern: sink(${'$'}UNTRUSTED)
            """.trimIndent(),
            "j.yaml" to """
                rules:
                  - id: j
                    severity: ERROR
                    message: m
                    languages: [java]
                    mode: join
                    join:
                      refs:
                        - rule: lib/marks.yaml#src
                          as: src
                        - rule: lib/marks.yaml#sink
                          as: sink
                      on: [ 'src.${'$'}UNTRUSTED -> sink.${'$'}UNTRUSTED' ]
            """.trimIndent()
        )
        assertTrue(trace.errorMessages().any { it.contains("metavariable '${'$'}UNTRUSTED'") }, trace.errorMessages().toString())
    }

    @Test
    fun `taint source label is a valid left join mark`() {
        val (result, trace) = load(
            "lib/marks.yaml" to """
                rules:
                  - id: src
                    options: { lib: true }
                    severity: NOTE
                    message: m
                    languages: [java]
                    mode: taint
                    pattern-sources:
                      - label: ${'$'}UNTRUSTED
                        pattern: source()
                    pattern-sinks: []
                  - id: sink
                    options: { lib: true }
                    severity: NOTE
                    message: m
                    languages: [java]
                    patterns: [ { pattern: sink(${'$'}VALUE) } ]
            """.trimIndent(),
            "j.yaml" to """
                rules:
                  - id: j
                    severity: ERROR
                    message: m
                    languages: [java]
                    mode: join
                    join:
                      refs:
                        - rule: lib/marks.yaml#src
                          as: src
                        - rule: lib/marks.yaml#sink
                          as: sink
                      on: [ 'src.${'$'}UNTRUSTED -> sink.${'$'}VALUE' ]
            """.trimIndent()
        )
        assertTrue(trace.errorMessages().isEmpty(), trace.errorMessages().toString())
        assertTrue("j" in loadedRuleIds(result))
    }

    @Test
    fun `override replaces the original rule's tags`() {
        val (result, trace) = load(
            sinkLib, servletSource,
            "override/servlet.yaml" to """
                rules:
                  - id: better-source
                    options:
                      lib: true
                      overrides: "lib/servlet.yaml#servlet-source"
                    tags: [custom-source]
                    severity: NOTE
                    message: src
                    languages: [java]
                    patterns:
                      - pattern: ${'$'}X = betterSource()
            """.trimIndent(),
            "ssrf-custom.yaml" to """
                rules:
                  - id: ssrf-custom
                    severity: ERROR
                    message: m
                    languages: [java]
                    mode: join
                    join:
                      refs:
                        - tag: custom-source
                          as: src
                        - rule: lib/sink.yaml#ssrf-sink
                          as: sink
                      on:
                        - 'src.${'$'}X -> sink.${'$'}X'
            """.trimIndent()
        )
        assertTrue(trace.errorMessages().isEmpty(), trace.errorMessages().toString())
        assertTrue("ssrf-custom" in loadedRuleIds(result), "join on overriding rule's tag should load; loaded=${loadedRuleIds(result)}")
    }

    @Test
    fun `override drops original tags from the tag index`() {
        val (_, trace) = load(
            sinkLib, servletSource,
            "override/servlet.yaml" to """
                rules:
                  - id: better-source
                    options:
                      lib: true
                      overrides: "lib/servlet.yaml#servlet-source"
                    tags: [custom-source]
                    severity: NOTE
                    message: src
                    languages: [java]
                    patterns:
                      - pattern: ${'$'}X = betterSource()
            """.trimIndent(),
            untrustedJoin
        )
        assertTrue(
            trace.errorMessages().any { it.contains("declares that tag") },
            "join on overridden rule's old tag should fail; errors=${trace.errorMessages()}"
        )
    }

    @Test
    fun `chaining an alias as both source and sink is rejected`() {
        val (_, trace) = load(
            "lib/three.yaml" to """
                rules:
                  - id: a
                    options: { lib: true }
                    severity: NOTE
                    message: m
                    languages: [java]
                    patterns: [ { pattern: a(${'$'}X) } ]
                  - id: b
                    options: { lib: true }
                    severity: NOTE
                    message: m
                    languages: [java]
                    patterns: [ { pattern: b(${'$'}X) } ]
                  - id: c
                    options: { lib: true }
                    severity: NOTE
                    message: m
                    languages: [java]
                    patterns: [ { pattern: c(${'$'}X) } ]
            """.trimIndent(),
            "j.yaml" to """
                rules:
                  - id: j
                    severity: ERROR
                    message: m
                    languages: [java]
                    mode: join
                    join:
                      refs:
                        - rule: lib/three.yaml#a
                          as: a
                        - rule: lib/three.yaml#b
                          as: b
                        - rule: lib/three.yaml#c
                          as: c
                      on:
                        - 'a.${'$'}X -> b.${'$'}X'
                        - 'b.${'$'}X -> c.${'$'}X'
            """.trimIndent()
        )
        assertTrue(trace.errorMessages().any { it.contains("chains an alias") }, trace.errorMessages().toString())
    }
}
