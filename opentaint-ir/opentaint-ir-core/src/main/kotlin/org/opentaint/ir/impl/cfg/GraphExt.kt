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
import org.opentaint.ir.api.cfg.JIRAddExpr
import org.opentaint.ir.api.cfg.JIRAndExpr
import org.opentaint.ir.api.cfg.JIRArgument
import org.opentaint.ir.api.cfg.JIRArrayAccess
import org.opentaint.ir.api.cfg.JIRAssignInst
import org.opentaint.ir.api.cfg.JIRBasicBlock
import org.opentaint.ir.api.cfg.JIRBlockGraph
import org.opentaint.ir.api.cfg.JIRBool
import org.opentaint.ir.api.cfg.JIRByte
import org.opentaint.ir.api.cfg.JIRCallInst
import org.opentaint.ir.api.cfg.JIRCastExpr
import org.opentaint.ir.api.cfg.JIRCatchInst
import org.opentaint.ir.api.cfg.JIRChar
import org.opentaint.ir.api.cfg.JIRClassConstant
import org.opentaint.ir.api.cfg.JIRCmpExpr
import org.opentaint.ir.api.cfg.JIRCmpgExpr
import org.opentaint.ir.api.cfg.JIRCmplExpr
import org.opentaint.ir.api.cfg.JIRDivExpr
import org.opentaint.ir.api.cfg.JIRDouble
import org.opentaint.ir.api.cfg.JIRDynamicCallExpr
import org.opentaint.ir.api.cfg.JIREnterMonitorInst
import org.opentaint.ir.api.cfg.JIREqExpr
import org.opentaint.ir.api.cfg.JIRExitMonitorInst
import org.opentaint.ir.api.cfg.JIRExpr
import org.opentaint.ir.api.cfg.JIRExprVisitor
import org.opentaint.ir.api.cfg.JIRFieldRef
import org.opentaint.ir.api.cfg.JIRFloat
import org.opentaint.ir.api.cfg.JIRGeExpr
import org.opentaint.ir.api.cfg.JIRGotoInst
import org.opentaint.ir.api.cfg.JIRGraph
import org.opentaint.ir.api.cfg.JIRGtExpr
import org.opentaint.ir.api.cfg.JIRIfInst
import org.opentaint.ir.api.cfg.JIRInst
import org.opentaint.ir.api.cfg.JIRInstVisitor
import org.opentaint.ir.api.cfg.JIRInstanceOfExpr
import org.opentaint.ir.api.cfg.JIRInt
import org.opentaint.ir.api.cfg.JIRLambdaExpr
import org.opentaint.ir.api.cfg.JIRLeExpr
import org.opentaint.ir.api.cfg.JIRLengthExpr
import org.opentaint.ir.api.cfg.JIRLocalVar
import org.opentaint.ir.api.cfg.JIRLong
import org.opentaint.ir.api.cfg.JIRLtExpr
import org.opentaint.ir.api.cfg.JIRMethodConstant
import org.opentaint.ir.api.cfg.JIRMulExpr
import org.opentaint.ir.api.cfg.JIRNegExpr
import org.opentaint.ir.api.cfg.JIRNeqExpr
import org.opentaint.ir.api.cfg.JIRNewArrayExpr
import org.opentaint.ir.api.cfg.JIRNewExpr
import org.opentaint.ir.api.cfg.JIRNullConstant
import org.opentaint.ir.api.cfg.JIROrExpr
import org.opentaint.ir.api.cfg.JIRPhiExpr
import org.opentaint.ir.api.cfg.JIRRemExpr
import org.opentaint.ir.api.cfg.JIRReturnInst
import org.opentaint.ir.api.cfg.JIRShlExpr
import org.opentaint.ir.api.cfg.JIRShort
import org.opentaint.ir.api.cfg.JIRShrExpr
import org.opentaint.ir.api.cfg.JIRSpecialCallExpr
import org.opentaint.ir.api.cfg.JIRStaticCallExpr
import org.opentaint.ir.api.cfg.JIRStringConstant
import org.opentaint.ir.api.cfg.JIRSubExpr
import org.opentaint.ir.api.cfg.JIRSwitchInst
import org.opentaint.ir.api.cfg.JIRThis
import org.opentaint.ir.api.cfg.JIRThrowInst
import org.opentaint.ir.api.cfg.JIRUshrExpr
import org.opentaint.ir.api.cfg.JIRVirtualCallExpr
import org.opentaint.ir.api.cfg.JIRXorExpr
import org.opentaint.ir.api.ext.findTypeOrNull
import org.opentaint.ir.api.ext.toType
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
    for ((index, block) in basicBlocks.withIndex()) {
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
open class JIRExceptionResolver(val classpath: JIRClasspath) : JIRInstVisitor<List<JIRClassType>>, JIRExprVisitor<List<JIRClassType>> {
    private val throwableType = classpath.findTypeOrNull<Throwable>() as JIRClassType
    private val nullPointerExceptionType = classpath.findTypeOrNull<NullPointerException>() as JIRClassType
    private val arithmeticExceptionType = classpath.findTypeOrNull<ArithmeticException>() as JIRClassType

    override fun visitExternalJIRExpr(value: JIRExpr): List<JIRClassType> {
        return emptyList()
    }

    override fun visitExternalJIRInst(inst: JIRInst): List<JIRClassType> {
        return emptyList()
    }

    override fun visitJIRAssignInst(inst: JIRAssignInst): List<JIRClassType> {
        return inst.lhv.accept(this) + inst.rhv.accept(this)
    }

    override fun visitJIREnterMonitorInst(inst: JIREnterMonitorInst): List<JIRClassType> {
        return listOf(nullPointerExceptionType)
    }

    override fun visitJIRExitMonitorInst(inst: JIRExitMonitorInst): List<JIRClassType> {
        return listOf(nullPointerExceptionType)
    }

    override fun visitJIRCallInst(inst: JIRCallInst): List<JIRClassType> {
        return inst.callExpr.accept(this)
    }

    override fun visitJIRReturnInst(inst: JIRReturnInst): List<JIRClassType> {
        return emptyList()
    }

    override fun visitJIRThrowInst(inst: JIRThrowInst): List<JIRClassType> {
        return listOf(inst.throwable.type as JIRClassType, nullPointerExceptionType)
    }

    override fun visitJIRCatchInst(inst: JIRCatchInst): List<JIRClassType> {
        return emptyList()
    }

    override fun visitJIRGotoInst(inst: JIRGotoInst): List<JIRClassType> {
        return emptyList()
    }

    override fun visitJIRIfInst(inst: JIRIfInst): List<JIRClassType> {
        return emptyList()
    }

    override fun visitJIRSwitchInst(inst: JIRSwitchInst): List<JIRClassType> {
        return emptyList()
    }

    override fun visitJIRAddExpr(expr: JIRAddExpr): List<JIRClassType> {
        return emptyList()
    }

    override fun visitJIRAndExpr(expr: JIRAndExpr): List<JIRClassType> {
        return emptyList()
    }

    override fun visitJIRCmpExpr(expr: JIRCmpExpr): List<JIRClassType> {
        return emptyList()
    }

    override fun visitJIRCmpgExpr(expr: JIRCmpgExpr): List<JIRClassType> {
        return emptyList()
    }

    override fun visitJIRCmplExpr(expr: JIRCmplExpr): List<JIRClassType> {
        return emptyList()
    }

    override fun visitJIRDivExpr(expr: JIRDivExpr): List<JIRClassType> {
        return listOf(arithmeticExceptionType)
    }

    override fun visitJIRMulExpr(expr: JIRMulExpr): List<JIRClassType> {
        return emptyList()
    }

    override fun visitJIREqExpr(expr: JIREqExpr): List<JIRClassType> {
        return emptyList()
    }

    override fun visitJIRNeqExpr(expr: JIRNeqExpr): List<JIRClassType> {
        return emptyList()
    }

    override fun visitJIRGeExpr(expr: JIRGeExpr): List<JIRClassType> {
        return emptyList()
    }

    override fun visitJIRGtExpr(expr: JIRGtExpr): List<JIRClassType> {
        return emptyList()
    }

    override fun visitJIRLeExpr(expr: JIRLeExpr): List<JIRClassType> {
        return emptyList()
    }

    override fun visitJIRLtExpr(expr: JIRLtExpr): List<JIRClassType> {
        return emptyList()
    }

    override fun visitJIROrExpr(expr: JIROrExpr): List<JIRClassType> {
        return emptyList()
    }

    override fun visitJIRRemExpr(expr: JIRRemExpr): List<JIRClassType> {
        return listOf(arithmeticExceptionType)
    }

    override fun visitJIRShlExpr(expr: JIRShlExpr): List<JIRClassType> {
        return emptyList()
    }

    override fun visitJIRShrExpr(expr: JIRShrExpr): List<JIRClassType> {
        return emptyList()
    }

    override fun visitJIRSubExpr(expr: JIRSubExpr): List<JIRClassType> {
        return emptyList()
    }

    override fun visitJIRUshrExpr(expr: JIRUshrExpr): List<JIRClassType> {
        return emptyList()
    }

    override fun visitJIRXorExpr(expr: JIRXorExpr): List<JIRClassType> {
        return emptyList()
    }

    override fun visitJIRLengthExpr(expr: JIRLengthExpr): List<JIRClassType> {
        return listOf(nullPointerExceptionType)
    }

    override fun visitJIRNegExpr(expr: JIRNegExpr): List<JIRClassType> {
        return emptyList()
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

    override fun visitJIRInstanceOfExpr(expr: JIRInstanceOfExpr): List<JIRClassType> {
        return emptyList()
    }

    override fun visitJIRLambdaExpr(expr: JIRLambdaExpr): List<JIRClassType> {
        return buildList {
            add(throwableType)
            addAll(expr.method.exceptions.map { it.toType() })
        }
    }

    override fun visitJIRDynamicCallExpr(expr: JIRDynamicCallExpr): List<JIRClassType> {
        return listOf(throwableType)
    }

    override fun visitJIRVirtualCallExpr(expr: JIRVirtualCallExpr): List<JIRClassType> {
        return buildList {
            add(throwableType)
            add(nullPointerExceptionType)
            addAll(expr.method.exceptions.map { it.toType() })
        }
    }

    override fun visitJIRStaticCallExpr(expr: JIRStaticCallExpr): List<JIRClassType> {
        return buildList {
            add(throwableType)
            addAll(expr.method.exceptions.map { it.toType() })
        }
    }

    override fun visitJIRSpecialCallExpr(expr: JIRSpecialCallExpr): List<JIRClassType> {
        return buildList {
            add(throwableType)
            add(nullPointerExceptionType)
            addAll(expr.method.exceptions.map { it.toType() })
        }
    }

    override fun visitJIRThis(value: JIRThis): List<JIRClassType> {
        return emptyList()
    }

    override fun visitJIRArgument(value: JIRArgument): List<JIRClassType> {
        return emptyList()
    }

    override fun visitJIRLocalVar(value: JIRLocalVar): List<JIRClassType> {
        return emptyList()
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

    override fun visitJIRBool(value: JIRBool): List<JIRClassType> {
        return emptyList()
    }

    override fun visitJIRByte(value: JIRByte): List<JIRClassType> {
        return emptyList()
    }

    override fun visitJIRChar(value: JIRChar): List<JIRClassType> {
        return emptyList()
    }

    override fun visitJIRShort(value: JIRShort): List<JIRClassType> {
        return emptyList()
    }

    override fun visitJIRInt(value: JIRInt): List<JIRClassType> {
        return emptyList()
    }

    override fun visitJIRLong(value: JIRLong): List<JIRClassType> {
        return emptyList()
    }

    override fun visitJIRFloat(value: JIRFloat): List<JIRClassType> {
        return emptyList()
    }

    override fun visitJIRDouble(value: JIRDouble): List<JIRClassType> {
        return emptyList()
    }

    override fun visitJIRNullConstant(value: JIRNullConstant): List<JIRClassType> {
        return emptyList()
    }

    override fun visitJIRStringConstant(value: JIRStringConstant): List<JIRClassType> {
        return emptyList()
    }

    override fun visitJIRClassConstant(value: JIRClassConstant): List<JIRClassType> {
        return emptyList()
    }

    override fun visitJIRMethodConstant(value: JIRMethodConstant): List<JIRClassType> {
        return emptyList()
    }

    override fun visitJIRPhiExpr(value: JIRPhiExpr): List<JIRClassType> {
        return emptyList()
    }
}
