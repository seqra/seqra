package org.opentaint.dataflow.python.alias

import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import org.junit.jupiter.api.TestInstance
import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.python.alias.AliasDirective.Kind
import org.opentaint.dataflow.python.PIRCallResolver
import org.opentaint.dataflow.python.PIRLanguageManager
import org.opentaint.dataflow.python.graph.PIRApplicationGraph
import org.opentaint.ir.api.common.CommonMethod
import org.opentaint.ir.api.common.cfg.CommonInst
import org.opentaint.ir.api.python.PIRCall
import org.opentaint.ir.api.python.PIRClasspath
import org.opentaint.ir.api.python.PIRFunction
import org.opentaint.ir.api.python.PIRLocalVar
import org.opentaint.ir.api.python.PIRSettings
import org.opentaint.ir.impl.python.PIRClasspathLoader
import org.opentaint.util.analysis.ApplicationGraph
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.absolutePathString
import kotlin.io.path.extension
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.nameWithoutExtension
import kotlin.io.path.readText
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.fail
import kotlin.time.Duration

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PIRAliasSampleTest {
    private val samplesDir: Path by lazy {
        Path(System.getProperty("PY_ALIAS_SAMPLES_DIR") ?: error("PY_ALIAS_SAMPLES_DIR not set"))
    }

    private val sampleFiles: List<Path> by lazy {
        samplesDir.listDirectoryEntries().filter { it.extension == "py" }.sorted()
    }

    private val cp: PIRClasspath by lazy {
        val settings = PIRSettings(
            sources = sampleFiles.map { it.absolutePathString() },
            packageRoots = listOf(samplesDir.absolutePathString()),
            mypyFlags = listOf("--ignore-missing-imports"),
        )
        PIRClasspathLoader(settings).load()
    }

    private val languageManager by lazy { PIRLanguageManager(cp) }
    private val pirGraph by lazy { PIRApplicationGraph(cp) }
    private val callResolver by lazy { PIRCallResolver(cp, pirGraph) }

    @Suppress("UNCHECKED_CAST")
    private val graph by lazy { pirGraph as ApplicationGraph<CommonMethod, CommonInst> }

    private class CheckedSink(val sink: PIRCall, val line: Int?, val expected: List<Pair<Kind, AliasApInfo>>?)

    @TestFactory
    fun aliasSamples(): List<DynamicTest> = sampleFiles.flatMap { file ->
        val module = cp.findModuleOrNull(file.nameWithoutExtension)
            ?: return@flatMap listOf(DynamicTest.dynamicTest("${file.fileName}: module loaded") { fail("module ${file.fileName} not loaded") })
        val parsed = AliasDirective.parse(file.readText())
        val directives = parsed.byLine
        val samples = module.functions.associateWith { checkedSinks(it, directives) }.filterValues { it.isNotEmpty() }
        val consumed = samples.values.flatten().filter { it.expected != null }.mapNotNullTo(hashSetOf()) { it.line?.minus(1) }

        val sampleTests = samples.map { (fn, sinks) ->
            DynamicTest.dynamicTest("${file.fileName}:${fn.name}") { runFunction(fn, parsed.depth, sinks) }
        }
        val coverageTest = DynamicTest.dynamicTest("${file.fileName}: every directive is checked") {
            val unused = (directives.keys - consumed).sorted()
            assertEquals(emptyList(), unused, "${file.fileName}: directives on lines $unused are not directly above a sample sink")
        }
        sampleTests + coverageTest
    }

    private fun checkedSinks(fn: PIRFunction, directives: Map<Int, List<Pair<Kind, AliasApInfo>>>): List<CheckedSink> =
        fn.instList.filterIsInstance<PIRCall>()
            .filter { it.resolvedCallee?.endsWith(".$SINK_NAME") == true }
            .map { sink ->
                val line = sink.physicalLocation?.lineStart?.toInt()
                CheckedSink(sink, line, line?.let { directives[it - 1] })
            }

    private fun runFunction(fn: PIRFunction, depth: Int, sinks: List<CheckedSink>) {
        if (sinks.any { it.line == null }) fail("${fn.qualifiedName}: sink without physical location")
        val duplicateLines = sinks.groupBy { it.line }.filterValues { it.size > 1 }.keys
        if (duplicateLines.isNotEmpty()) fail("${fn.qualifiedName}: several sinks on lines $duplicateLines")
        val aa = analysis(fn, depth)
        for (checked in sinks) {
            val at = "${fn.qualifiedName}:${checked.line}"
            val expected = checked.expected ?: fail("$at: sink has no # alias: directive above it")
            val aliases = sinkArgAliases(aa, checked)
            for ((kind, ap) in expected) {
                val present = ap in aliases
                when (kind) {
                    Kind.PRESENT -> assertTrue(present, "$at: expected $ap not found in $aliases")
                    Kind.ABSENT -> assertFalse(present, "$at: expected ABSENT $ap but found it in $aliases")
                    Kind.GAP -> assertFalse(present, "$at: known gap $ap now found; mark it present")
                }
            }
        }
    }

    private fun analysis(fn: PIRFunction, depth: Int): PIRLocalAliasAnalysis {
        val entry = pirGraph.methodGraph(fn).entryPoints().single()
        val params = PIRLocalAliasAnalysis.Params(
            aliasAnalysisInterProcCallDepth = depth,
            aliasAnalysisTimeLimit = Duration.INFINITE,
        )
        return PIRLocalAliasAnalysis(entry, graph, callResolver, languageManager, null, params)
    }

    private fun sinkArgAliases(aa: PIRLocalAliasAnalysis, checked: CheckedSink): List<AliasApInfo> {
        val arg = checked.sink.args.firstOrNull()?.value as? PIRLocalVar
            ?: fail("$SINK_NAME arg at line ${checked.line} must be a local, got ${checked.sink.args}")
        return aa.findAlias(AccessPathBase.LocalVar(arg.index), checked.sink).orEmpty()
    }

    companion object {
        private const val SINK_NAME = "alias_sink"
    }
}
