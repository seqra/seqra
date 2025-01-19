package org.opentaint.ir.impl.cfg

import info.leadinglight.jdot.Edge
import info.leadinglight.jdot.Graph
import info.leadinglight.jdot.Node
import info.leadinglight.jdot.enums.Color
import info.leadinglight.jdot.enums.Shape
import info.leadinglight.jdot.impl.Util
import org.opentaint.ir.api.JIRClassType
import org.opentaint.ir.api.JIRClasspath
import org.opentaint.ir.api.PredefinedPrimitives
import org.opentaint.ir.api.cfg.DefaultJIRExprVisitor
import org.opentaint.ir.api.cfg.DefaultJIRInstVisitor
import org.opentaint.ir.api.cfg.JIRArrayAccess
import org.opentaint.ir.api.cfg.JIRAssignInst
import org.opentaint.ir.api.cfg.JIRBasicBlock
import org.opentaint.ir.api.cfg.JIRBlockGraph
import org.opentaint.ir.api.cfg.JIRCallInst
import org.opentaint.ir.api.cfg.JIRCastExpr
import org.opentaint.ir.api.cfg.JIRDivExpr
import org.opentaint.ir.api.cfg.JIRDynamicCallExpr
import org.opentaint.ir.api.cfg.JIRExitMonitorInst
import org.opentaint.ir.api.cfg.JIRExpr
import org.opentaint.ir.api.cfg.JIRFieldRef
import org.opentaint.ir.api.cfg.JIRGotoInst
import org.opentaint.ir.api.cfg.JIRGraph
import org.opentaint.ir.api.cfg.JIRIfInst
import org.opentaint.ir.api.cfg.JIRInst
import org.opentaint.ir.api.cfg.JIRLambdaExpr
import org.opentaint.ir.api.cfg.JIRLengthExpr
import org.opentaint.ir.api.cfg.JIRNewArrayExpr
import org.opentaint.ir.api.cfg.JIRNewExpr
import org.opentaint.ir.api.cfg.JIRRemExpr
import org.opentaint.ir.api.cfg.JIRSpecialCallExpr
import org.opentaint.ir.api.cfg.JIRStaticCallExpr
import org.opentaint.ir.api.cfg.JIRSwitchInst
import org.opentaint.ir.api.cfg.JIRThrowInst
import org.opentaint.ir.api.cfg.JIRVirtualCallExpr
import org.opentaint.ir.api.ext.findTypeOrNull
import java.io.File
import java.nio.file.Files
import java.nio.file.Path

fun JIRGraph.view(dotCmd: String, viewerCmd: String, viewCatchConnections: Boolean = false) {
    Util.sh(arrayOf(viewerCmd, "file://${toFile(dotCmd, viewCatchConnections)}"))
}

fun JIRGraph.toFile(dotCmd: String, viewCatchConnections: Boolean = false, file: File? = null): Path {
    Graph.setDefaultCmd(dotCmd)

    val graph = Graph("jIRGraph")

    val nodes = mutableMapOf<JIRInst, Node>()
    for ((index, inst) in instructions.withIndex()) {
        val node = Node("$index")
            .setShape(Shape.box)
            .setLabel(inst.toString().replace("\"", "\\\""))
            .setFontSize(12.0)
        nodes[inst] = node
        graph.addNode(node)
    }

    graph.setBgColor(Color.X11.transparent)
    graph.setFontSize(12.0)
    graph.setFontName("Fira Mono")

    for ((inst, node) in nodes) {
        when (inst) {
            is JIRGotoInst -> for (successor in successors(inst)) {
                graph.addEdge(Edge(node.name, nodes[successor]!!.name))
            }

            is JIRIfInst -> {
                graph.addEdge(
                    Edge(node.name, nodes[inst(inst.trueBranch)]!!.name)
                        .also {
                            it.setLabel("true")
                        }
                )
                graph.addEdge(
                    Edge(node.name, nodes[inst(inst.falseBranch)]!!.name)
                        .also {
                            it.setLabel("false")
                        }
                )
            }

            is JIRSwitchInst -> {
                for ((key, branch) in inst.branches) {
                    graph.addEdge(
                        Edge(node.name, nodes[inst(branch)]!!.name)
                            .also {
                                it.setLabel("$key")
                            }
                    )
                }
                graph.addEdge(
                    Edge(node.name, nodes[inst(inst.default)]!!.name)
                        .also {
                            it.setLabel("else")
                        }
                )
            }

            else -> for (successor in successors(inst)) {
                graph.addEdge(Edge(node.name, nodes[successor]!!.name))
            }
        }
        if (viewCatchConnections) {
            for (catcher in catchers(inst)) {
                graph.addEdge(Edge(node.name, nodes[catcher]!!.name).also {
                    it.setLabel("catch ${catcher.throwable.type}")
                })
            }
        }
    }

    val outFile = graph.dot2file("svg")
    val newFile = "${outFile.removeSuffix("out")}svg"
    val resultingFile = file?.toPath() ?: File(newFile).toPath()
    Files.move(File(outFile).toPath(), resultingFile)
    return resultingFile
}

fun JIRBlockGraph.view(dotCmd: String, viewerCmd: String) {
    Util.sh(arrayOf(viewerCmd, "file://${toFile(dotCmd)}"))
}

fun JIRBlockGraph.toFile(dotCmd: String, file: File? = null): Path {
    Graph.setDefaultCmd(dotCmd)

    val graph = Graph("jIRGraph")

    val nodes = mutableMapOf<JIRBasicBlock, Node>()
    for ((index, block) in withIndex()) {
        val node = Node("$index")
            .setShape(Shape.box)
            .setLabel(instructions(block).joinToString("") { "$it\\l" }.replace("\"", "\\\"").replace("\n", "\\n"))
            .setFontSize(12.0)
        nodes[block] = node
        graph.addNode(node)
    }

    graph.setBgColor(Color.X11.transparent)
    graph.setFontSize(12.0)
    graph.setFontName("Fira Mono")

    for ((block, node) in nodes) {
        val terminatingInst = instructions(block).last()
        val successors = successors(block)
        when (terminatingInst) {
            is JIRGotoInst -> for (successor in successors) {
                graph.addEdge(Edge(node.name, nodes[successor]!!.name))
            }

            is JIRIfInst -> {
                graph.addEdge(
                    Edge(node.name, nodes[successors.first { it.start == terminatingInst.trueBranch }]!!.name)
                        .also {
                            it.setLabel("true")
                        }
                )
                graph.addEdge(
                    Edge(node.name, nodes[successors.first { it.start == terminatingInst.falseBranch }]!!.name)
                        .also {
                            it.setLabel("false")
                        }
                )
            }

            is JIRSwitchInst -> {
                for ((key, branch) in terminatingInst.branches) {
                    graph.addEdge(
                        Edge(node.name, nodes[successors.first { it.start == branch }]!!.name)
                            .also {
                                it.setLabel("$key")
                            }
                    )
                }
                graph.addEdge(
                    Edge(node.name, nodes[successors.first { it.start == terminatingInst.default }]!!.name)
                        .also {
                            it.setLabel("else")
                        }
                )
            }

            else -> for (successor in successors(block)) {
                graph.addEdge(Edge(node.name, nodes[successor]!!.name))
            }
        }
    }

    val outFile = graph.dot2file("svg")
    val newFile = "${outFile.removeSuffix("out")}svg"
    val resultingFile = file?.toPath() ?: File(newFile).toPath()
    Files.move(File(outFile).toPath(), resultingFile)
    return resultingFile
}

/**
 * Returns a list of possible thrown exceptions for any given instruction or expression (types of exceptions
 * are determined from JVM bytecode specification). For method calls it returns:
 * - all the declared checked exception types
 * - 'java.lang.Throwable' for any potential unchecked types
 */
open class JIRExceptionResolver(val classpath: JIRClasspath) : DefaultJIRExprVisitor<List<JIRClassType>>,
    DefaultJIRInstVisitor<List<JIRClassType>> {
    private val throwableType = classpath.findTypeOrNull<Throwable>() as JIRClassType
    private val errorType = classpath.findTypeOrNull<Error>() as JIRClassType
    private val runtimeExceptionType = classpath.findTypeOrNull<RuntimeException>() as JIRClassType
    private val nullPointerExceptionType = classpath.findTypeOrNull<NullPointerException>() as JIRClassType
    private val arithmeticExceptionType = classpath.findTypeOrNull<ArithmeticException>() as JIRClassType

    override val defaultExprHandler: (JIRExpr) -> List<JIRClassType>
        get() = { emptyList() }

    override val defaultInstHandler: (JIRInst) -> List<JIRClassType>
        get() = { emptyList() }

    override fun visitJIRAssignInst(inst: JIRAssignInst): List<JIRClassType> {
        return inst.lhv.accept(this) + inst.rhv.accept(this)
    }

    override fun visitJIRExitMonitorInst(inst: JIRExitMonitorInst): List<JIRClassType> {
        return listOf(nullPointerExceptionType)
    }

    override fun visitJIRCallInst(inst: JIRCallInst): List<JIRClassType> {
        return inst.callExpr.accept(this)
    }

    override fun visitJIRThrowInst(inst: JIRThrowInst): List<JIRClassType> {
        return listOf(inst.throwable.type as JIRClassType, nullPointerExceptionType)
    }

    override fun visitJIRDivExpr(expr: JIRDivExpr): List<JIRClassType> {
        return listOf(arithmeticExceptionType)
    }

    override fun visitJIRRemExpr(expr: JIRRemExpr): List<JIRClassType> {
        return listOf(arithmeticExceptionType)
    }

    override fun visitJIRLengthExpr(expr: JIRLengthExpr): List<JIRClassType> {
        return listOf(nullPointerExceptionType)
    }

    override fun visitJIRCastExpr(expr: JIRCastExpr): List<JIRClassType> {
        return when {
            PredefinedPrimitives.matches(expr.type.typeName) -> emptyList()
            else -> listOf(classpath.findTypeOrNull<ClassCastException>() as JIRClassType)
        }
    }

    override fun visitJIRNewExpr(expr: JIRNewExpr): List<JIRClassType> {
        return listOf(classpath.findTypeOrNull<Error>() as JIRClassType)
    }

    override fun visitJIRNewArrayExpr(expr: JIRNewArrayExpr): List<JIRClassType> {
        return listOf(classpath.findTypeOrNull<NegativeArraySizeException>() as JIRClassType)
    }

    override fun visitJIRLambdaExpr(expr: JIRLambdaExpr): List<JIRClassType> {
        return buildList {
            add(runtimeExceptionType)
            add(errorType)
            addAll(expr.method.exceptions.thisOrThrowable())
        }
    }

    override fun visitJIRDynamicCallExpr(expr: JIRDynamicCallExpr): List<JIRClassType> {
        return listOf(throwableType)
    }

    override fun visitJIRVirtualCallExpr(expr: JIRVirtualCallExpr): List<JIRClassType> {
        return buildList {
            add(runtimeExceptionType)
            add(errorType)
            addAll(expr.method.exceptions.thisOrThrowable())
        }
    }

    override fun visitJIRStaticCallExpr(expr: JIRStaticCallExpr): List<JIRClassType> {
        return buildList {
            add(runtimeExceptionType)
            add(errorType)
            addAll(expr.method.exceptions.thisOrThrowable())
        }
    }

    override fun visitJIRSpecialCallExpr(expr: JIRSpecialCallExpr): List<JIRClassType> {
        return buildList {
            add(runtimeExceptionType)
            add(errorType)
            addAll(expr.method.exceptions.thisOrThrowable())
        }
    }

    override fun visitJIRFieldRef(value: JIRFieldRef): List<JIRClassType> {
        return listOf(nullPointerExceptionType)
    }

    override fun visitJIRArrayAccess(value: JIRArrayAccess): List<JIRClassType> {
        return listOf(
            nullPointerExceptionType,
            classpath.findTypeOrNull<IndexOutOfBoundsException>() as JIRClassType
        )
    }

    private fun <E> List<E>.thisOrThrowable(): Collection<JIRClassType> {
        return map {
            when (it) {
                is JIRClassType -> it
                else -> throwableType
            }
        }
    }

}

