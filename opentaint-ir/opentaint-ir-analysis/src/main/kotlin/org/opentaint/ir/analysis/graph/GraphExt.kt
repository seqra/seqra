package org.opentaint.ir.analysis.graph

import info.leadinglight.jdot.Edge
import info.leadinglight.jdot.Graph
import info.leadinglight.jdot.Node
import info.leadinglight.jdot.enums.Color
import info.leadinglight.jdot.enums.Shape
import info.leadinglight.jdot.impl.Util
import org.opentaint.ir.api.JIRMethod
import org.opentaint.ir.api.analysis.JIRApplicationGraph
import org.opentaint.ir.api.cfg.JIRInst
import java.io.File
import java.nio.file.Files
import java.nio.file.Path

fun JIRApplicationGraph.dfs(
    node: JIRInst,
    method: JIRMethod,
    visited: MutableSet<JIRInst>,
) {
    if (visited.add(node)) {
        for (next in successors(node)) {
            if (next.location.method == method) {
                dfs(next, method, visited)
            }
        }
    }
}

fun JIRApplicationGraph.view(method: JIRMethod, dotCmd: String, viewerCmd: String) {
    Util.sh(arrayOf(viewerCmd, "file://${toFile(method, dotCmd)}"))
}

fun JIRApplicationGraph.toFile(
    method: JIRMethod,
    dotCmd: String,
    file: File? = null,
): Path {
    Graph.setDefaultCmd(dotCmd)

    val graph = Graph("jIRApplicationGraph")
    graph.setBgColor(Color.X11.transparent)
    graph.setFontSize(12.0)
    graph.setFontName("monospace")

    val allInstructions: MutableSet<JIRInst> = hashSetOf()
    for (start in entryPoints(method)) {
        dfs(start, method, allInstructions)
    }

    val nodes = mutableMapOf<JIRInst, Node>()
    for ((index, inst) in allInstructions.withIndex()) {
        val node = Node("$index")
            .setShape(Shape.box)
            .setLabel(inst.toString().replace("\"", "\\\""))
            .setFontSize(12.0)
        nodes[inst] = node
        graph.addNode(node)
    }

    for ((inst, node) in nodes) {
        for (next in successors(inst)) {
            if (next in nodes) {
                val edge = Edge(node.name, nodes[next]!!.name)
                graph.addEdge(edge)
            }
        }
    }

    val outFile = graph.dot2file("svg")
    val newFile = "${outFile.removeSuffix("out")}svg"
    val resultingFile = file?.toPath() ?: File(newFile).toPath()
    Files.move(File(outFile).toPath(), resultingFile)
    return resultingFile
}
