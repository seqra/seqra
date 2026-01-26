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
import org.opentaint.semgrep.pattern.SemgrepErrorEntry
import org.opentaint.semgrep.pattern.SemgrepJavaPattern
import org.opentaint.semgrep.pattern.SemgrepJavaPatternParser
import org.opentaint.semgrep.pattern.SemgrepJavaPatternParsingResult
import org.opentaint.semgrep.pattern.SemgrepLoadTrace
import org.opentaint.semgrep.pattern.SemgrepRuleLoadStepTrace
import org.opentaint.semgrep.pattern.SemgrepRuleLoadTrace
import org.opentaint.semgrep.pattern.SemgrepRuleLoader
import org.opentaint.semgrep.pattern.TaintRuleFromSemgrep
import org.opentaint.semgrep.pattern.conversion.PatternToActionListConverter
import org.opentaint.semgrep.pattern.conversion.SemgrepPatternParser
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.deleteExisting
import kotlin.io.path.extension
import kotlin.io.path.pathString
import kotlin.io.path.readText
import kotlin.io.path.relativeTo
import kotlin.io.path.walk
import kotlin.io.path.writeText
import kotlin.random.Random
import kotlin.system.measureTimeMillis
import kotlin.time.measureTime

fun main() {
    val path = Path(System.getProperty("user.home")).resolve("data/opentaint-rules")

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

    val tm = measureTimeMillis {
        collectParsingStats(path)
//        testStability(path) // May need to increase automataBuildTimeout to pass
    }
//    println("Time is ${tm * 0.001}s")

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

private fun normalizeRules(path: Path) {
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
        path.resolve(rulePath[i]).writeText(rule)
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

private fun minimizeConfig(path: Path) {
    val rulePath = mutableListOf<String>()
    val rulesSets = mutableListOf<String>()
    val parsedRules = mutableListOf<PartialRule>()

    for (rule in collectAllRules(path)) {
        val parsed = runCatching { yaml.decodeFromString<PartialRule>(rule.rule) }.getOrNull() ?: continue
        rulePath.add(rule.path)
        rulesSets.add(rule.rule)
        parsedRules.add(parsed)
    }

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

        val ruleSetPath = path.resolve(rulePath[i])
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

private fun collectAllRules(path: Path): List<SemgrepRuleFile> {
    val result = mutableListOf<SemgrepRuleFile>()
    val rootDir = path.toFile()
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

private fun collectParsingStats(path: Path): List<Pair<SemgrepJavaPattern, String>> {
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

    val semgrepTrace = SemgrepLoadTrace()

    val parser = SemgrepJavaPatternParser()
    val converter = PatternToActionListConverter()

    val patternParser = object : SemgrepPatternParser {
        override fun parseOrNull(
            pattern: String,
            semgrepTrace: SemgrepRuleLoadStepTrace,
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

                    semgrepTrace.error(
                        "Pattern parse failure: ${reason.message ?: ""}",
                        SemgrepErrorEntry.Reason.ERROR
                    )
                    return null
                }

                is SemgrepJavaPatternParsingResult.OtherFailure -> {
                    failures += 1
                    parserOtherFailures += result.exception to pattern
                    semgrepTrace.error(
                        "Other parse failure: ${result.exception.message ?: ""}",
                        SemgrepErrorEntry.Reason.ERROR
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

    val loader = SemgrepRuleLoader(patternParser, converter)

    val rootDir = path.toFile()
    rootDir.walk().filter { it.isFile }.forEach { file ->
        if (file.extension !in setOf("yml", "yaml")) {
            return@forEach
        }

        if (file.name in ignoreFiles) {
            return@forEach
        }

        println("Reading $file")
        val content = file.readText()
        loader.registerRuleSet(content, file.toPath().relativeTo(path), path, semgrepTrace)
    }

    val time = measureTime {
        loader.loadRules()
    }

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
    println("Build time: $time")

    analyzeErrors(semgrepTrace)

    return allPatterns
}

private fun analyzeErrors(trace: SemgrepLoadTrace) {
    val allErrors = hashMapOf<SemgrepErrorEntry.Reason, MutableList<SemgrepErrorEntry>>()
    val ruleErrors = mutableListOf<SemgrepRuleLoadTrace>()

    for (fileError in trace.fileTraces) {
        allErrors.addErrors(fileError.entries.filterIsInstance<SemgrepErrorEntry>())
        ruleErrors.addAll(fileError.ruleTraces)
    }

    for (ruleError in ruleErrors) {
        allErrors.addErrors(ruleError.entries.filterIsInstance<SemgrepErrorEntry>())
        ruleError.steps.forEach {
            allErrors.addErrors(it.entries.filterIsInstance<SemgrepErrorEntry>())
        }
    }

    for ((groupKind, groupErrors) in allErrors.entries.sortedBy { it.key.toString() }) {
        println("-".repeat(20))
        println("Trace $groupKind")

        val errorKinds = groupErrors.map { it.ruleKind() }
        val sortedErrors = errorKinds.groupingBy { it }.eachCount().entries.sortedByDescending { it.value }
        sortedErrors.forEach { (key, value) ->
            println("$key: $value")
        }
    }
}

private fun MutableMap<SemgrepErrorEntry.Reason, MutableList<SemgrepErrorEntry>>.addErrors(
    errors: Iterable<SemgrepErrorEntry>
) = errors.forEach {
    this.getOrPut(it.reason, ::mutableListOf).add(it)
}

private fun SemgrepErrorEntry.ruleKind(): String {
    if (message.startsWith("Pattern parse failure:")) {
        return "Pattern parse failure"
    }

    if (message.startsWith("Failed transformation to ActionList:")) {
        return "Failed transformation to ActionList"
    }

    val notImplemented = message.indexOf("An operation is not implemented:")
    if (notImplemented != -1) {
        return message.substring(notImplemented)
    }

    var normalizedMessage = message
    normalizedMessage = normalizedMessage.replace(Regex("""\d+ times"""), "XX times")
    normalizedMessage = normalizedMessage.replace(Regex("""^Rule.*?:"""), "Rule XXX:")

    return normalizedMessage
}

private fun testStability(path: Path, nIterations: Int = 100) {
    val rng = Random(943)
    val ruleExtensions = arrayOf("yaml", "yml")
    val allRules = path.walk()
        .filter { it.extension in ruleExtensions }
        .toList()
        .sortedBy { it.pathString }

    repeat(nIterations) {
        val seed = rng.nextInt()
        println("Iteration #$it, seed = $seed")
        testStabilityOnePass(path, allRules, seed)
    }
}

private fun testStabilityOnePass(path: Path, allRules: List<Path>, seed: Int) {
    val rng = Random(seed)
    val forwardStats = collectRuleSetStat(path, allRules.shuffled(rng))
    val reversedStats = collectRuleSetStat(path, allRules.shuffled(rng))

    val diff = mutableListOf<Triple<String, List<Map<String, Int>>?, List<Map<String, Int>>?>>()
    val allKeys = forwardStats.keys + reversedStats.keys
    for (key in allKeys) {
        val fs = forwardStats[key]
        val rs = reversedStats[key]
        if (fs != rs) {
            println("DIFF: $key")
            println("$fs")
            println("$rs")
            diff += Triple(key, fs, rs)
        }
    }

    check(diff.isEmpty()) {
        "Rule generator is unstable: ${diff.size}"
    }
}

private fun collectRuleSetStat(semgrepRulesPath: Path, allRules: List<Path>): HashMap<String, List<Map<String, Int>>> {
    val stats = hashMapOf<String, List<Map<String, Int>>>()
    val loader = SemgrepRuleLoader()
    val trace = SemgrepLoadTrace()
    for (rulePath in allRules) {
        loader.registerRuleSet(rulePath.readText(), rulePath, semgrepRulesPath, trace)
        val (loadedRules, _) = loader.loadRules().unzip()
        loadedRules.forEach {
            stats[it.ruleId] = it.stats()
        }
    }
    return stats
}

private fun TaintRuleFromSemgrep.stats(): List<Map<String, Int>> =
    taintRules.map { it.stats() }

private fun TaintRuleFromSemgrep.TaintRuleGroup.stats(): Map<String, Int> =
    rules.groupingBy { it::class.java.simpleName }.eachCount()
