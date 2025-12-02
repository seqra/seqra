package org.opentaint

import com.charleskorn.kaml.AmbiguousQuoteStyle
import com.charleskorn.kaml.AnchorsAndAliases
import com.charleskorn.kaml.MultiLineStringStyle
import com.charleskorn.kaml.SingleLineStringStyle
import com.charleskorn.kaml.Yaml
import com.charleskorn.kaml.YamlConfiguration
import com.charleskorn.kaml.YamlList
import com.charleskorn.kaml.YamlMap
import com.charleskorn.kaml.YamlNode
import com.charleskorn.kaml.YamlNull
import com.charleskorn.kaml.YamlScalar
import com.charleskorn.kaml.YamlTaggedNode
import kotlinx.collections.immutable.persistentListOf
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.slf4j.event.Level
import org.opentaint.dataflow.configuration.jvm.serialized.SinkMetaData
import org.opentaint.semgrep.pattern.AbstractSemgrepError
import org.opentaint.semgrep.pattern.SemgrepError
import org.opentaint.semgrep.pattern.SemgrepFileErrors
import org.opentaint.semgrep.pattern.SemgrepJavaPattern
import org.opentaint.semgrep.pattern.SemgrepJavaPatternParser
import org.opentaint.semgrep.pattern.SemgrepJavaPatternParsingResult
import org.opentaint.semgrep.pattern.SemgrepRuleErrors
import org.opentaint.semgrep.pattern.conversion.PatternToActionListConverter
import org.opentaint.semgrep.pattern.conversion.SemgrepPatternParser
import org.opentaint.semgrep.pattern.conversion.SemgrepRuleAutomataBuilder
import org.opentaint.semgrep.pattern.conversion.taint.convertToTaintRules
import org.opentaint.semgrep.pattern.yamlToSemgrepRule
import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import kotlin.io.path.Path
import kotlin.io.path.deleteExisting
import kotlin.io.path.writeText
import kotlin.time.Duration
import kotlin.time.measureTimedValue

fun main() {
    val path = "data/opentaint-rules"

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

//    normalizeRules(path)
//    minimizeConfig(path)

    collectParsingStats(path)

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

private val yaml = Yaml(
    configuration = YamlConfiguration(
        strictMode = false,
        ambiguousQuoteStyle = AmbiguousQuoteStyle.DoubleQuoted,
        singleLineStringStyle = SingleLineStringStyle.PlainExceptAmbiguous,
        multiLineStringStyle = MultiLineStringStyle.Literal,
        anchorsAndAliases = AnchorsAndAliases.Permitted()
    )
)

private fun normalizeRules(path: String) {
    val allRules = collectAllRules(path)

    val rulePath = allRules.map { it.path }
    val rulesSets = allRules.map { it.rule }

    val parsedRules = rulesSets.map { ruleText ->
        val original = yaml.decodeFromString<PartialRule>(ruleText)
        if (!original.rules.any { it.containsBadMultiLine(persistentListOf()) }) return@map null

        yaml.encodeToString<PartialRule>(original)
    }

    for ((i, rule) in parsedRules.withIndex()) {
        if (rule == null) continue
        Path(path).resolve(rulePath[i]).writeText(rule)
    }
}

private fun rewriteYamlNodeScalars(node: YamlNode): YamlNode {
    return when (node) {
        is YamlList -> YamlList(node.items.map { rewriteYamlNodeScalars(it) }, node.path)
        is YamlMap -> YamlMap(node.entries.mapValues { rewriteYamlNodeScalars(it.value) }, node.path)
        is YamlNull -> node
        is YamlTaggedNode -> YamlTaggedNode(node.tag, rewriteYamlNodeScalars(node.innerNode))
        is YamlScalar -> {
            val contentLines = node.content.lines()
            if (contentLines.size < 2) return node

            val nonEmpty = contentLines.dropWhile { it.isBlank() }.dropLastWhile { it.isBlank() }
            if (nonEmpty.size != 1) return node

            val nonEmptyContent = nonEmpty.joinToString("\n")
            return YamlScalar(nonEmptyContent, node.path)
        }
    }
}

private fun YamlNode.containsBadMultiLine(siblings: List<YamlNode?>): Boolean {
    return when (this) {
        is YamlList -> {
            for ((i, item) in items.withIndex()) {
                if (item.containsBadMultiLine(siblings + items.getOrNull(i + 1))) return true
            }
            return false
        }
        is YamlMap -> {
            val entryList = entries.toList()
            for ((i, entry) in entryList.withIndex()) {
                if (entry.second.containsBadMultiLine(siblings + entryList.getOrNull(i + 1)?.first)) return true
            }
            return false
        }
        is YamlNull -> false
        is YamlTaggedNode -> innerNode.containsBadMultiLine(siblings + null)
        is YamlScalar -> {
            val lines = content.lines()
            if (lines.size < 2) return false

            val expectedSiblingLine = location.line + lines.size
            val siblingLocation = siblings.asReversed().firstNotNullOfOrNull { it } ?: return false
            return siblingLocation.location.line < expectedSiblingLine
        }
    }
}

private fun minimizeConfig(path: String) {
    val allRules = collectAllRules(path)

    val rulePath = allRules.map { it.path }
    val rulesSets = allRules.map { it.rule }

    val parsedRules = rulesSets.map { yaml.decodeFromString<PartialRule>(it) }

    val ruleIndex = parsedRules.map { ruleSet ->
        val index = hashMapOf<NormalizedRuleWrapper, MutableList<Int>>()
        for ((i, rule) in ruleSet.rules.withIndex()) {
            index.getOrPut(NormalizedRuleWrapper(rule), ::mutableListOf).add(i)
        }
        index
    }

    val ruleClusters = hashMapOf<NormalizedRuleWrapper, MutableList<Int>>()
    for ((ruleSetIdx, rules) in ruleIndex.withIndex()) {
        for ((rule, _) in rules) {
            ruleClusters.getOrPut(rule, ::mutableListOf).add(ruleSetIdx)
        }
    }

    for ((_, cluster) in ruleClusters) {
        cluster.sortWith(compareBy<Int> { parsedRules[it].rules.size }.thenBy { rulePath[it].length })
    }

    val removedRules = hashMapOf<Int, MutableSet<Int>>()
    for ((representative, cluster) in ruleClusters) {
        if (cluster.size <= 1) continue

        val selectedRule = cluster.first()
        val selectedMatchingRules = ruleIndex[selectedRule][representative]
            ?: error("impossible")

        val selectedRuleVariant = selectedMatchingRules.min()
        removedRules.getOrPut(selectedRule, ::hashSetOf).addAll(selectedMatchingRules - selectedRuleVariant)

        check(removedRules[selectedRule]?.contains(selectedRuleVariant) != true) { "already removed" }

        for (ruleSetIdx in cluster) {
            if (ruleSetIdx == selectedRule) continue

            val matchingRules = ruleIndex[ruleSetIdx][representative]
                ?: error("impossible")

            removedRules.getOrPut(ruleSetIdx, ::hashSetOf).addAll(matchingRules)
        }
    }

    for ((i, ruleSet) in parsedRules.withIndex()) {
        val removedRuleIndices = removedRules[i].orEmpty()
        if (removedRuleIndices.isEmpty()) continue

        val resultRules = ruleSet.rules.filterIndexed { index, _ -> index !in removedRuleIndices }

        val ruleSetPath = Path(path).resolve(rulePath[i])
        if (resultRules.isEmpty()) {
            ruleSetPath.deleteExisting()
            continue
        }

        val serialized = yaml.encodeToString(PartialRule(resultRules))
        ruleSetPath.writeText(serialized)
    }
}

@Serializable
private data class PartialRule(val rules: List<YamlMap>) {
    override fun equals(other: Any?): Boolean {
        if (other !is PartialRule) return false
        if (rules.size != other.rules.size) return false

        for (i in rules.indices) {
            if (!rules[i].equivalentContentTo(other.rules[i])) return false
        }

        return true
    }

    override fun hashCode(): Int = error("unsupported")
}

private class NormalizedRuleWrapper(val rule: YamlMap) {
    val entriesToCompare = rule.entries.entries.filter { it.key.content !in ignoreFields }
    val entriesToCompareText = entriesToCompare.mapTo(hashSetOf()) {
        it.key.content to it.value.contentToString()
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is NormalizedRuleWrapper) return false

        return entriesToCompareText == other.entriesToCompareText
    }

    override fun hashCode(): Int = entriesToCompareText.hashCode()

    companion object {
        val ignoreFields = setOf("id", "message", "metadata")
    }
}

private data class SemgrepRuleFile(val path: String, val rule: String)

private fun collectAllRules(path: String): List<SemgrepRuleFile> {
    val result = mutableListOf<SemgrepRuleFile>()
    val rootDir = File(path)
    rootDir.walk()
        .filter { it.isFile }.forEach { file ->
            if (file.extension !in setOf("yml", "yaml")) {
                return@forEach
            }

            val rulePath = file.relativeTo(rootDir).path
            val ruleText = file.readText()
            result.add(SemgrepRuleFile(rulePath, ruleText))
        }
    return result
}

private fun collectParsingStats(path: String): List<Pair<SemgrepJavaPattern, String>> {
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

    val semgrepFilesErrors: MutableList<AbstractSemgrepError> = arrayListOf()

    val parser = SemgrepJavaPatternParser()
    val converter = PatternToActionListConverter()

    val patternParser = object : SemgrepPatternParser {
        override fun parseOrNull(
            pattern: String,
            semgrepError: AbstractSemgrepError,
            semgrepStep: SemgrepError.Step,
        ): SemgrepJavaPattern? {
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

                    semgrepError += SemgrepError(
                        semgrepStep,
                        "Pattern parse failure: ${reason.message ?: ""}",
                        Level.TRACE,
                        SemgrepError.Reason.ERROR
                    )
                    return null
                }

                is SemgrepJavaPatternParsingResult.OtherFailure -> {
                    failures += 1
                    parserOtherFailures += result.exception to pattern
                    semgrepError += SemgrepError(
                        semgrepStep,
                        "Other parse failure: ${result.exception.message ?: ""}",
                        Level.TRACE,
                        SemgrepError.Reason.ERROR
                    )
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

            val semgrepFileErrors = SemgrepFileErrors(
                file.path.toString()
            )
            semgrepFilesErrors.add(semgrepFileErrors)
            val rules = try {
                yamlToSemgrepRule(content)
            } catch (e: Throwable) {
                semgrepFileErrors += SemgrepError(
                    SemgrepError.Step.LOAD_RULESET,
                    "Failed parsing yaml: ${file.path}",
                            Level.ERROR,
                    SemgrepError.Reason.ERROR
                )

                e.printStackTrace()
                return@forEach
            }

            if (rules.isEmpty()) {  // not java rules
                return@forEach
            }
            all++

            for ((i, rule) in rules.withIndex()) {
            val semgrepRuleErrors = SemgrepRuleErrors(
                rule.id,
                ruleSetName = file.toString()
            )
            semgrepFileErrors += semgrepRuleErrors
            val ruleBuilder = SemgrepRuleAutomataBuilder(patternParser.cached(), converter.cached())
            val automata = measureTimedValue {
                runCatching {
                    ruleBuilder.build(rule, semgrepRuleErrors)
                }.getOrElse { e ->
                    semgrepFileErrors += SemgrepError(
                        SemgrepError.Step.LOAD_RULESET,
                        "Exception build rule: $e",
                        Level.TRACE,
                        SemgrepError.Reason.ERROR,
                    )
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
                convertToTaintRules(automata, "test", SinkMetaData(), semgrepRuleErrors)
            }.onFailure { e ->
                semgrepRuleErrors += SemgrepError(
                    SemgrepError.Step.AUTOMATA_TO_TAINT_RULE,
                    "Exception convert ruleAutomata to TaintRules: $e",
                    Level.TRACE,
                    SemgrepError.Reason.ERROR,
                )


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

    val prettyJson = Json {
        prettyPrint = true
    }
    println(prettyJson.encodeToString(semgrepFilesErrors))

    return allPatterns
}

private fun SemgrepRuleAutomataBuilder.Stats.add(other: SemgrepRuleAutomataBuilder.Stats) {
    this.ruleParsingFailure += other.ruleParsingFailure
    this.ruleWithoutPattern += other.ruleWithoutPattern
    this.actionListConversionFailure += other.actionListConversionFailure
    this.metaVarResolvingFailure += other.metaVarResolvingFailure
    this.emptyAutomata += other.emptyAutomata
}
