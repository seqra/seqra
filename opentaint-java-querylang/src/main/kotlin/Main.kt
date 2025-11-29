package org.opentaint

import org.opentaint.dataflow.configuration.jvm.serialized.SinkMetaData
import org.opentaint.semgrep.pattern.SemgrepJavaPattern
import org.opentaint.semgrep.pattern.SemgrepJavaPatternParser
import org.opentaint.semgrep.pattern.SemgrepJavaPatternParsingResult
import org.opentaint.semgrep.pattern.conversion.PatternToActionListConverter
import org.opentaint.semgrep.pattern.conversion.SemgrepPatternParser
import org.opentaint.semgrep.pattern.conversion.SemgrepRuleAutomataBuilder
import org.opentaint.semgrep.pattern.conversion.taint.convertToTaintRules
import org.opentaint.semgrep.pattern.yamlToSemgrepRule
import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration
import kotlin.time.measureTimedValue

fun main() {
//    val pattern = "return (int ${"\$"}A);"
//    val pattern = "(org.springframework.web.client.RestTemplate \$RESTTEMP).\$FUNC"
//    val pattern = "\$X.SSLSocketFactory.ALLOW_ALL_HOSTNAME_VERIFIER"
//    val pattern = "io.micronaut.http.cookie.Cookie.of(...). ... .sameSite(\$SAME)"
//    val pattern = """
//        @Path(value = ${"$"}PATH2, ${"$"}KEY = ...)
//        ${"$"}RETURN ${"$"}FUNC(...) {
//          ...
//        }
//    """.trimIndent()
//    val pattern = """
//          @Path(${"\$"}PATH1)
//          class ${"\$"}CLASS
//    """.trimIndent()
//
//    // ${"$"}
//    val pattern = """
//        setSslSocketFactory(new NonValidatingSSLSocketFactory());
//    """.trimIndent()
//
//    val parser = SempregJavaPatternParser()
//    val result = parser.parseSemgrepJavaPattern(pattern)
//    println(result)

//    val parsedPattern = (result as? SemgrepJavaPatternParsingResult.Ok)?.pattern
//        ?: error("Couldn't parse pattern: $result")
//    val rule = transformSemgrepPatternToTaintRule(parsedPattern)
//    println(rule)

    collectParsingStats()

//    val s = "int1|12char|4567"
//    println(checkIfRegexIsSimpleEnumeration(s))

    /*
    // ${"$"}
    val pattern1 = """
        f(${"$"}X);
    """.trimIndent()

    val pattern2 = """
        ...
        clean(${"$"}X);
    """.trimIndent()

    val rule = NormalizedSemgrepRule(
        patterns = listOf(pattern1),
        patternNots = listOf(),
        patternInsides = listOf(),
        patternNotInsides = listOf(pattern2),
    )
    val automata = transformSemgrepRuleToAutomata(rule)

    automata!!.view()
    */
}

fun collectParsingStats(): List<Pair<SemgrepJavaPattern, String>> {
    val path = "data/sast-semgrep-rules/semgrep/"

    // TODO
    val ignoreFiles = setOf(
        "rule-XMLStreamRdr.yml",
        "rule-X509TrustManager.yml",
        "rule-HostnameVerifier.yml"
    )

    val allPatterns = mutableListOf<Pair<SemgrepJavaPattern, String>>()

    var successful = 0
    var failures = 0

    val astParseFailures = mutableListOf<String>()
    val parserOtherFailures = mutableListOf<Pair<Throwable, String>>()
    val parserFailures = hashMapOf<Pair<String, String>, MutableList<String>>()

    val parser = SemgrepJavaPatternParser()
    val converter = PatternToActionListConverter()

    val patternParser = object : SemgrepPatternParser {
        override fun parseOrNull(pattern: String): SemgrepJavaPattern? {
            val result = parser.parseSemgrepJavaPattern(pattern)

            when (result) {
                is SemgrepJavaPatternParsingResult.FailedASTParsing -> {
                    failures += 1
                    astParseFailures.add(pattern)
                    return null
                }

                is SemgrepJavaPatternParsingResult.ParserFailure -> {
                    failures += 1

                    val reason = result.exception
                    val reasonKind = reason::class.java.simpleName
                    val reasonElementKind = reason.element::class.java.simpleName
                    parserFailures.getOrPut(reasonKind to reasonElementKind, ::mutableListOf).add(pattern)

                    return null
                }

                is SemgrepJavaPatternParsingResult.OtherFailure -> {
                    failures += 1
                    parserOtherFailures += result.exception to pattern
                    return null
                }

                is SemgrepJavaPatternParsingResult.Ok -> {
                    successful += 1
                    return result.pattern
                }
            }
        }
    }

    var converted = 0
    var all = 0
    var exceptionWhileBuildingAutomata = 0
    var taintRuleGenerationException = 0
    var successTaintRules = 0
    val taintRuleGenerationExceptions = hashMapOf<String, AtomicInteger>()
    val automataBuildExceptions = hashMapOf<String, AtomicInteger>()
    val ruleBuildTime = hashMapOf<String, Duration>()

    val ruleBuilderStats = SemgrepRuleAutomataBuilder.Stats()

    val rootDir = File(path)
    rootDir.walk()
        .filter { it.isFile }.forEach { file ->
        if (file.extension !in setOf("yml", "yaml")) {
            return@forEach
        }

        if (file.name in ignoreFiles) {
            return@forEach
        }
        println("Reading $file")
        val content = file.readText()

        val rules = try {
            yamlToSemgrepRule(content)
        } catch (e: Throwable) {
            System.err.println("Error parsing $file")
            e.printStackTrace()
            return@forEach
        }

        if (rules.isEmpty()) {  // not java rules
            return@forEach
        }
        all++

        for ((i, rule) in rules.withIndex()) {
            val ruleBuilder = SemgrepRuleAutomataBuilder(patternParser.cached(), converter.cached())
            val automata = measureTimedValue {
                runCatching {
                    ruleBuilder.build(rule)
                }.getOrElse { e ->
                    automataBuildExceptions.getOrPut(e.toString(), ::AtomicInteger).incrementAndGet()
                    exceptionWhileBuildingAutomata += 1
                    null
                }
            }.also {
                val rulePath = file.relativeTo(rootDir).toString()
                val ruleFqn = "$rulePath#$i"
                ruleBuildTime[ruleFqn] = it.duration
            }.value

            ruleBuilderStats.add(ruleBuilder.stats)

            if (automata == null) continue

            converted++
            println("converted")

            runCatching {
                convertToTaintRules(automata, "test", SinkMetaData())
            }.onFailure { e ->
                taintRuleGenerationExceptions.getOrPut(e.toString(), ::AtomicInteger).incrementAndGet()
                taintRuleGenerationException++
            }.onSuccess { successTaintRules++ }
        }
    }

    println("Converted into automata $converted/$all")
    println("Exceptions while building automata: $exceptionWhileBuildingAutomata")
    automataBuildExceptions.entries.sortedByDescending { it.value.get() }.forEach { (key, value) ->
        println("$key: $value")
    }
    println()
    println(ruleBuilderStats)
    println()

    println("Pattern statistics:")
    println("Success: $successful")
    println("Failures: $failures")
    println("AST failures: ${astParseFailures.size}")
    println("Unknown failures: ${parserOtherFailures.size}")
    parserFailures.entries.sortedByDescending { it.value.size }.forEach { (key, value) ->
        println("$key: ${value.size}")
    }

    println()
    println("PatternToActionListConverter errors:")
    converter.failedTransformations.entries.sortedByDescending { it.value }.forEach { (key, value) ->
        println("$key: $value")
    }

    println()
    println("Taint rules")
    println("Success: $successTaintRules")
    println("Failures: $taintRuleGenerationException")
    taintRuleGenerationExceptions.entries.sortedByDescending { it.value.get() }.forEach { (key, value) ->
        println("$key: $value")
    }

    println()
    println("Build time")
    ruleBuildTime.entries.sortedByDescending { it.value }.take(10).forEach { (key, value) ->
        println("$key: $value")
    }

    return allPatterns
}

private fun SemgrepRuleAutomataBuilder.Stats.add(other: SemgrepRuleAutomataBuilder.Stats) {
    this.ruleParsingFailure += other.ruleParsingFailure
    this.ruleWithoutPattern += other.ruleWithoutPattern
    this.actionListConversionFailure += other.actionListConversionFailure
    this.metaVarResolvingFailure += other.metaVarResolvingFailure
    this.emptyAutomata += other.emptyAutomata
}
