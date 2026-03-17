package org.opentaint.dataflow.util.printer

import info.leadinglight.jdot.Edge
import info.leadinglight.jdot.Graph
import info.leadinglight.jdot.Node
import info.leadinglight.jdot.enums.Color
import info.leadinglight.jdot.enums.Shape
import info.leadinglight.jdot.enums.Style
import info.leadinglight.jdot.impl.Util
import java.io.File
import java.nio.file.Files
import java.nio.file.Path

interface PrintableGraph<GNode, GLabel> {
    fun allNodes(): List<GNode>
    fun nodeLabel(node: GNode): String
    fun successors(node: GNode): List<Pair<GLabel, GNode>>
    fun edgeLabel(edge: GLabel): String

    fun view(name: String) {
        val path = toFile(name, "dot")
        Util.sh(arrayOf(viewer.value, "file://$path"))
    }

    fun toFile(fileName: String, dotCmd: String): Path {
        Graph.setDefaultCmd(dotCmd)

        val graph = Graph("automata")

        graph.setBgColor(Color.X11.transparent)
        graph.setFontSize(12.0)
        graph.setFontName("Fira Mono")

        val nodes = mutableMapOf<GNode, Node>()

        fun mkNode(node: GNode): Node = nodes.getOrPut(node) {
            val index = nodes.size
            val label = nodeLabel(node).split("\n").joinToString("\\\n") { line -> "${line.replace("\"", "\\\"")}\\l" }
            val nd = Node("$index")
                .setShape(Shape.box)
                .setLabel(label)
                .setFontSize(12.0)
            graph.addNode(nd)
            nd
        }

        for (state in allNodes()) {
            val stateNode = mkNode(state)

            for ((edgeT, dstState) in successors(state)) {
                val dstNode = mkNode(dstState)

                val edgeLabel = edgeLabel(edgeT)

                graph.addEdge(Edge(stateNode.name, dstNode.name).also {
                    val label =
                        edgeLabel.split("\n").joinToString("\\\n") { line -> "${line.replace("\"", "\\\"")}\\l" }
                    it.setLabel(label)
                    it.setStyle(Style.Edge.solid)
                })
            }
        }

        val outFile = graph.dot2file("svg")
        val newFile = "${outFile.removeSuffix(".out")}$fileName.svg"
        val resultingFile = File(newFile).toPath()
        Files.move(File(outFile).toPath(), resultingFile)
        return resultingFile
    }

    companion object {
        private val viewer = lazy {
            val os = System.getProperty("os.name")
            if (os.startsWith("Mac")) "open"
            else "xdg-open"
        }
    }
}