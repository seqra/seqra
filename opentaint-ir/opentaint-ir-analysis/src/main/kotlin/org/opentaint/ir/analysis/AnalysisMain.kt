package org.opentaint.ir.analysis
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import org.opentaint.ir.analysis.analyzers.AliasAnalyzer
import org.opentaint.ir.analysis.analyzers.NpeAnalyzer
import org.opentaint.ir.analysis.analyzers.TaintAnalysisNode
import org.opentaint.ir.analysis.analyzers.UnusedVariableAnalyzer
import org.opentaint.ir.analysis.engine.Analyzer
import org.opentaint.ir.analysis.engine.BidiIFDSForTaintAnalysis
import org.opentaint.ir.analysis.engine.DomainFact
import org.opentaint.ir.analysis.engine.IFDSUnitInstance
import org.opentaint.ir.analysis.engine.IFDSUnitTraverser
import org.opentaint.ir.analysis.engine.SingletonUnitResolver
import org.opentaint.ir.analysis.engine.TaintRealisationsGraph
import org.opentaint.ir.analysis.graph.JIRApplicationGraphImpl
import org.opentaint.ir.analysis.graph.SimplifiedJIRApplicationGraph
import org.opentaint.ir.analysis.points2.AllOverridesDevirtualizer
import org.opentaint.ir.analysis.points2.Devirtualizer
import org.opentaint.ir.api.JIRClasspath
import org.opentaint.ir.api.JIRMethod
import org.opentaint.ir.api.analysis.JIRApplicationGraph
import org.opentaint.ir.api.cfg.JIRInst
import org.opentaint.ir.impl.features.usagesExt
import java.util.*

@Serializable
data class DumpableVulnerabilityInstance(
    val vulnerabilityType: String,
    val sources: List<String>,
    val sink: String,
    val realisationPaths: List<List<String>>
)

@Serializable
data class DumpableAnalysisResult(val foundVulnerabilities: List<DumpableVulnerabilityInstance>)

data class VulnerabilityInstance(
    val vulnerabilityType: String,
    val realisationsGraph: TaintRealisationsGraph
) {
    fun toDumpable(maxPathsCount: Int): DumpableVulnerabilityInstance {
        return DumpableVulnerabilityInstance(
            vulnerabilityType,
            realisationsGraph.sources.map { it.statement.toString() },
            realisationsGraph.sink.statement.toString(),
            realisationsGraph.getAllPaths().take(maxPathsCount).map { intermediatePoints ->
                intermediatePoints.map { it.statement.toString() }
            }.toList()
        )
    }
}

data class AnalysisResult(val vulnerabilities: List<VulnerabilityInstance>) {
    fun toDumpable(maxPathsCount: Int = 10): DumpableAnalysisResult {
        return DumpableAnalysisResult(vulnerabilities.map { it.toDumpable(maxPathsCount) })
    }
}

typealias AnalysesOptions = Map<String, String>

@Serializable
data class AnalysisConfig(val analyses: Map<String, AnalysesOptions>)

interface AnalysisEngine {
    fun analyze(): AnalysisResult
    fun addStart(method: JIRMethod)
}

interface Points2Engine {
    fun obtainDevirtualizer(): Devirtualizer
}

interface Factory {
    val name: String
}

interface AnalysisEngineFactory : Factory {
    fun createAnalysisEngine(
        graph: JIRApplicationGraph,
        points2Engine: Points2Engine,
    ): AnalysisEngine
}

class UnusedVariableAnalysisFactory : AnalysisEngineFactory {
    override fun createAnalysisEngine(graph: JIRApplicationGraph, points2Engine: Points2Engine): AnalysisEngine {
        return IFDSUnitTraverser(
            graph,
            UnusedVariableAnalyzer(graph),
            SingletonUnitResolver,
            points2Engine.obtainDevirtualizer(),
            IFDSUnitInstance
        )
    }

    override val name: String
        get() = "unused-variable"
}

abstract class FlowDroidFactory : AnalysisEngineFactory {

    protected abstract val JIRApplicationGraph.analyzer: Analyzer

    override fun createAnalysisEngine(
        graph: JIRApplicationGraph,
        points2Engine: Points2Engine,
    ): AnalysisEngine {
        val analyzer = graph.analyzer
        return IFDSUnitTraverser(graph, analyzer, SingletonUnitResolver, points2Engine.obtainDevirtualizer(), BidiIFDSForTaintAnalysis)
    }

    override val name: String
        get() = "flow-droid"
}

class NPEAnalysisFactory : FlowDroidFactory() {
    override val JIRApplicationGraph.analyzer: Analyzer
        get() {
            return NpeAnalyzer(this)
        }
}

class AliasAnalysisFactory(
    private val generates: (JIRInst) -> List<TaintAnalysisNode>,
    private val isSink: (JIRInst, DomainFact) -> Boolean,
) : FlowDroidFactory() {
    override val JIRApplicationGraph.analyzer: Analyzer
        get() {
            return AliasAnalyzer(this, generates, isSink)
        }
}

interface Points2EngineFactory : Factory {
    fun createPoints2Engine(graph: JIRApplicationGraph): Points2Engine
}

interface GraphFactory : Factory {
    fun createGraph(classpath: JIRClasspath): JIRApplicationGraph
}

class JIRSimplifiedGraphFactory(
    private val bannedPackagePrefixes: List<String>? = null
) : GraphFactory {
    override val name: String = "ifds-simplification"

    override fun createGraph(
        classpath: JIRClasspath
    ): JIRApplicationGraph = runBlocking {
        val mainGraph = JIRApplicationGraphImpl(classpath, classpath.usagesExt())
        if (bannedPackagePrefixes != null) {
            SimplifiedJIRApplicationGraph(mainGraph, bannedPackagePrefixes)
        } else {
            SimplifiedJIRApplicationGraph(mainGraph)
        }
    }
}

object JIRNaivePoints2EngineFactory : Points2EngineFactory {
    override fun createPoints2Engine(
        graph: JIRApplicationGraph,
    ): Points2Engine {
        val cp = graph.classpath
        return AllOverridesDevirtualizer(graph, cp)
    }

    override val name: String
        get() = "naive-p2"
}

inline fun <reified T : Factory> loadFactories(): List<T> {
    assert(T::class.java != Factory::class.java)
    return ServiceLoader.load(T::class.java).toList()
}
