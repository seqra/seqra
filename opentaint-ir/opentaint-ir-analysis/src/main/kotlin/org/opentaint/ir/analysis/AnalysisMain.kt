package org.opentaint.ir.analysis
import kotlinx.cli.ArgParser
import kotlinx.cli.ArgType
import kotlinx.cli.required
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToStream
import mu.KLogging
import org.opentaint.ir.analysis.analyzers.AliasAnalyzer
import org.opentaint.ir.analysis.analyzers.NpeAnalyzer
import org.opentaint.ir.analysis.analyzers.TaintAnalysisNode
import org.opentaint.ir.analysis.analyzers.UnusedVariableAnalyzer
import org.opentaint.ir.analysis.engine.Analyzer
import org.opentaint.ir.analysis.engine.DomainFact
import org.opentaint.ir.analysis.engine.IFDSInstance
import org.opentaint.ir.analysis.engine.TaintAnalysisWithPointsTo
import org.opentaint.ir.analysis.graph.JIRApplicationGraphImpl
import org.opentaint.ir.analysis.graph.SimplifiedJIRApplicationGraph
import org.opentaint.ir.analysis.points2.AllOverridesDevirtualizer
import org.opentaint.ir.analysis.points2.Devirtualizer
import org.opentaint.ir.api.JIRClasspath
import org.opentaint.ir.api.JIRMethod
import org.opentaint.ir.api.analysis.JIRApplicationGraph
import org.opentaint.ir.api.cfg.JIRInst
import org.opentaint.ir.impl.features.InMemoryHierarchy
import org.opentaint.ir.impl.features.Usages
import org.opentaint.ir.impl.features.usagesExt
import org.opentaint.ir.impl.opentaint-ir
import java.io.File
import java.util.*

@Serializable
data class VulnerabilityInstance(
    val vulnerabilityType: String,
    val sources: List<String>,
    val sink: String,
    val realisationPaths: List<List<String>>
)

@Serializable
data class DumpableAnalysisResult(val foundVulnerabilities: List<VulnerabilityInstance>)

interface AnalysisEngine {
    fun analyze(): DumpableAnalysisResult
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
        return IFDSInstance(
            graph,
            UnusedVariableAnalyzer(graph),
            points2Engine.obtainDevirtualizer()
        )
    }

    override val name: String
        get() = "Opentaint-IR-Unused-Variable"
}

abstract class FlowDroidFactory : AnalysisEngineFactory {

    protected abstract fun getAnalyzer(graph: JIRApplicationGraph): Analyzer

    override fun createAnalysisEngine(
        graph: JIRApplicationGraph,
        points2Engine: Points2Engine,
    ): AnalysisEngine {
        val analyzer = getAnalyzer(graph)
        return TaintAnalysisWithPointsTo(graph, analyzer, points2Engine)
    }

    override val name: String
        get() = "Opentaint-IR-FlowDroid"
}

class NPEAnalysisFactory : FlowDroidFactory() {
    override fun getAnalyzer(graph: JIRApplicationGraph): Analyzer {
        return NpeAnalyzer(graph)
    }
}

class AliasAnalysisFactory(
    private val generates: (JIRInst) -> List<TaintAnalysisNode>,
    private val isSink: (JIRInst, DomainFact) -> Boolean,
) : FlowDroidFactory() {
    override fun getAnalyzer(graph: JIRApplicationGraph): Analyzer {
        return AliasAnalyzer(graph, generates, isSink)
    }
}

interface Points2EngineFactory : Factory {
    fun createPoints2Engine(graph: JIRApplicationGraph): Points2Engine
}

interface GraphFactory : Factory {
    fun createGraph(classpath: JIRClasspath): JIRApplicationGraph

    fun createGraph(classpath: List<File>, cacheDir: File): JIRApplicationGraph = runBlocking {
        val classpathHash = classpath.toString().hashCode()
        val persistentPath = cacheDir.resolve("opentaint-ir-for-$classpathHash")

        val jIRdb = opentaint-ir {
            loadByteCode(classpath)
            persistent(persistentPath.absolutePath)
            installFeatures(InMemoryHierarchy, Usages)
        }
        val cp = jIRdb.classpath(classpath)
        createGraph(cp)
    }
}

class JIRSimplifiedGraphFactory(
    private val bannedPackagePrefixes: List<String>? = null
) : GraphFactory {
    override val name: String = "Opentaint-IR-graph simplified for IFDS"

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

class JIRNaivePoints2EngineFactory : Points2EngineFactory {
    override fun createPoints2Engine(
        graph: JIRApplicationGraph,
    ): Points2Engine {
        val cp = graph.classpath
        return AllOverridesDevirtualizer(graph, cp)
    }

    override val name: String
        get() = "Opentaint-IR-P2-Naive"
}

inline fun <reified T : Factory> loadFactories(): List<T> {
    assert(T::class.java != Factory::class.java)
    return ServiceLoader.load(T::class.java).toList()
}

private inline fun <reified T : Factory> factoryChoice(): ArgType.Choice<T> {
    val factories = loadFactories<T>()
    val nameToFactory = { requiredFactoryName: String -> factories.single { it.name == requiredFactoryName } }
    val factoryToName = { factory: T -> factory.name }

    return ArgType.Choice(factories, nameToFactory, factoryToName)
}

private val logger = object : KLogging() {}.logger

class AnalysisMain {
    fun run(args: List<String>) = main(args.toTypedArray())
}

fun main(args: Array<String>) {
    val parser = ArgParser("taint-analysis")
    val classpath by parser.option(
        ArgType.String,
        fullName = "classpath",
        shortName = "cp",
        description = "Classpath for analysis. Used by Opentaint-IR."
    ).required()
    val graphFactory by parser.option(
        factoryChoice<GraphFactory>(),
        fullName = "graph-type",
        shortName = "g",
        description = "Type of code graph to be used by analysis."
    ).required()
    val engineFactory by parser.option(
        factoryChoice<AnalysisEngineFactory>(),
        fullName = "engine",
        shortName = "e",
        description = "Type of IFDS engine."
    ).required()
    val points2Factory by parser.option(
        factoryChoice<Points2EngineFactory>(),
        fullName = "points2",
        shortName = "p2",
        description = "Type of points-to engine."
    ).required()
    val cacheDirPath by parser.option(
        ArgType.String,
        fullName = "cache-directory",
        shortName = "c",
        description = "Directory with caches for analysis. All parent directories will be created if not exists. Directory will be created if not exists. Directory must be empty."
    ).required()
    val outputPath by parser.option(
        ArgType.String,
        fullName = "output",
        shortName = "o",
        description = "File where analysis report will be written. All parent directories will be created if not exists. File will be created if not exists. Existing file will be overwritten."
    ).required()

    parser.parse(args)

    val outputFile = File(outputPath)

    if (outputFile.exists() && outputFile.isDirectory) {
        throw IllegalArgumentException("Provided path for output file is directory, please provide correct path")
    } else if (outputFile.exists()) {
        logger.info { "Output file $outputFile already exists, results will be overwritten" }
    } else {
        outputFile.parentFile.mkdirs()
    }

    val cacheDir = File(cacheDirPath)

    if (!cacheDir.exists()) {
        cacheDir.mkdirs()
    }

    if (!cacheDir.isDirectory) {
        throw IllegalArgumentException("Provided path to cache directory is not directory")
    }

    val classpathAsFiles = classpath.split(File.pathSeparatorChar).sorted().map { File(it) }
    val graph = graphFactory.createGraph(classpathAsFiles, cacheDir)
    val points2Engine = points2Factory.createPoints2Engine(graph)
    val analysisEngine = engineFactory.createAnalysisEngine(graph, points2Engine)
    val analysisResult = analysisEngine.analyze()
    val json = Json { prettyPrint = true }

    outputFile.outputStream().use { fileOutputStream ->
        json.encodeToStream(analysisResult, fileOutputStream)
    }
}