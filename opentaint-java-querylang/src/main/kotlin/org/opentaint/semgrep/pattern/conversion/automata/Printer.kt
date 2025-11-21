package org.opentaint.org.opentaint.semgrep.pattern.conversion.automata

import info.leadinglight.jdot.Edge
import info.leadinglight.jdot.Graph
import info.leadinglight.jdot.Node
import info.leadinglight.jdot.enums.Color
import info.leadinglight.jdot.enums.Shape
import info.leadinglight.jdot.enums.Style
import info.leadinglight.jdot.impl.Util
import org.opentaint.dataflow.util.forEach
import org.opentaint.org.opentaint.semgrep.pattern.conversion.automata.MethodFormula.And
import org.opentaint.org.opentaint.semgrep.pattern.conversion.automata.MethodFormula.Cube
import org.opentaint.org.opentaint.semgrep.pattern.conversion.automata.MethodFormula.True
import org.opentaint.org.opentaint.semgrep.pattern.conversion.taint.TaintRegisterStateAutomata
import org.opentaint.org.opentaint.semgrep.pattern.conversion.taint.TaintRuleEdge
import org.opentaint.org.opentaint.semgrep.pattern.conversion.taint.TaintRuleGenerationCtx
import java.io.File
import java.nio.file.Files
import java.nio.file.Path

fun SemgrepRuleAutomata.view() {
    PrintableSemgrepRuleAutomata(this).view()
}

class PrintableSemgrepRuleAutomata(val automata: SemgrepRuleAutomata) : PrintableGraph<AutomataNode, AutomataEdgeType> {
    private var nodeIndex = 0

    override fun allNodes(): List<AutomataNode> {
        val allNodes = hashSetOf<AutomataNode>()
        val unprocessedNodes = mutableListOf(automata.initialNode)
        while (unprocessedNodes.isNotEmpty()) {
            val node = unprocessedNodes.removeLast()
            if (!allNodes.add(node)) continue
            node.outEdges.forEach { unprocessedNodes.add(it.second) }
        }
        return allNodes.toList()
    }

    override fun successors(node: AutomataNode): List<Pair<AutomataEdgeType, AutomataNode>> = node.outEdges

    override fun nodeLabel(node: AutomataNode): String =
        "${nodeIndex++}${if (node.accept) " ACCEPT " else if (node == automata.initialNode) " ROOT " else ""}"

    override fun edgeLabel(edge: AutomataEdgeType): String = when (edge) {
        AutomataEdgeType.End -> "END"
        is AutomataEdgeType.AutomataEdgeTypeWithFormula -> {
            val formula = edge.formula.prettyPrint(automata.formulaManager, lineLengthLimit = 40)
            when (edge) {
                is AutomataEdgeType.MethodCall -> "CALL($formula)"
                is AutomataEdgeType.MethodEnter -> "ENTER($formula)"
            }
        }

        AutomataEdgeType.PatternEnd -> "PatternEnd"
        AutomataEdgeType.PatternStart -> "PatternStart"
    }
}

class TaintRegisterStateAutomataView(
    val automata: TaintRegisterStateAutomata
) : PrintableGraph<TaintRegisterStateAutomata.State, TaintRegisterStateAutomata.Edge> {
    override fun allNodes() = automata.successors.keys.toList()

    override fun successors(node: TaintRegisterStateAutomata.State): List<Pair<TaintRegisterStateAutomata.Edge, TaintRegisterStateAutomata.State>> =
        automata.successors[node]?.toList() ?: emptyList()

    override fun nodeLabel(node: TaintRegisterStateAutomata.State): String =
        "${automata.stateId(node)}${if (node.node.accept) " ACCEPT " else ""}(${node.register.assignedVars})"

    override fun edgeLabel(edge: TaintRegisterStateAutomata.Edge): String =
        automataEdgeLabel(edge)
}

fun TaintRegisterStateAutomata.view() {
    TaintRegisterStateAutomataView(this).view()
}

class TaintRuleGenerationContextView(
    val ctx: TaintRuleGenerationCtx
) : PrintableGraph<TaintRegisterStateAutomata.State, TaintRuleEdge> {
    private fun buildSuccessors(): Map<TaintRegisterStateAutomata.State, Set<TaintRuleEdge>> {
        val successors = hashMapOf<TaintRegisterStateAutomata.State, MutableSet<TaintRuleEdge>>()
        for (edge in ctx.finalEdges) {
            successors.getOrPut(edge.stateFrom, ::hashSetOf).add(edge)
        }
        for (edge in ctx.edges) {
            successors.getOrPut(edge.stateFrom, ::hashSetOf).add(edge)
        }
        return successors
    }

    private val successors by lazy { buildSuccessors() }

    override fun allNodes() = successors.keys.toList()

    override fun successors(node: TaintRegisterStateAutomata.State) =
        successors[node]?.map { it to it.stateTo }.orEmpty()

    override fun edgeLabel(edge: TaintRuleEdge): String {
        var label = automataEdgeLabel(edge.edge)
        if (edge.checkGlobalState) {
            label = "STATE == ${ctx.automata.stateId(edge.stateFrom)}\n" + label
        }
        return label
    }

    override fun nodeLabel(node: TaintRegisterStateAutomata.State): String {
        val stateId = ctx.automata.stateId(node)
        val assignedVars = node.register.assignedVars
        val stateVar = if (node in ctx.globalStateAssignStates) "[STATE = $stateId]" else ""
        var nodeAnnotation = ""
        if (node in ctx.automata.final) {
            nodeAnnotation = (if (node.node.accept) "SINK" else "CLEANER")
        }
        return "$stateId $assignedVars $stateVar $nodeAnnotation"
    }
}

fun TaintRuleGenerationCtx.view() {
    TaintRuleGenerationContextView(this).view()
}

private fun automataEdgeLabel(edge: TaintRegisterStateAutomata.Edge): String = when (edge) {
    is TaintRegisterStateAutomata.Edge.MethodCall -> "CALL(${edge.condition.prettyPrint()}{${edge.effect.prettyPrint()}})"
    is TaintRegisterStateAutomata.Edge.MethodEnter -> "ENTER(${edge.condition.prettyPrint()}{${edge.effect.prettyPrint()}})"
    TaintRegisterStateAutomata.Edge.AnalysisEnd -> "END"
}

interface PrintableGraph<Node, EdgeLabel> {
    fun allNodes(): List<Node>
    fun nodeLabel(node: Node): String
    fun successors(node: Node): List<Pair<EdgeLabel, Node>>
    fun edgeLabel(edge: EdgeLabel): String

    fun view() {
        val path = toFile("dot")
        Util.sh(arrayOf("xdg-open", "file://$path"))
    }
}

private fun <GNode, EdgeLabel> PrintableGraph<GNode, EdgeLabel>.toFile(dotCmd: String): Path {
    Graph.setDefaultCmd(dotCmd)

    val graph = Graph("automata")

    graph.setBgColor(Color.X11.transparent)
    graph.setFontSize(12.0)
    graph.setFontName("Fira Mono")

    val nodes = mutableMapOf<GNode, Node>()

    fun mkNode(node: GNode): Node = nodes.getOrPut(node) {
        val index = nodes.size
        val label = nodeLabel(node)
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
                val label = edgeLabel.split("\n").joinToString("\\\n") { line -> "${line.replace("\"", "\\\"")}\\l" }
                it.setLabel(label)
                it.setStyle(Style.Edge.solid)
            })
        }
    }

    val outFile = graph.dot2file("svg")
    val newFile = "${outFile.removeSuffix("out")}svg"
    val resultingFile = File(newFile).toPath()
    Files.move(File(outFile).toPath(), resultingFile)
    return resultingFile
}

fun MethodFormula.prettyPrint(manager: MethodFormulaManager, lineLengthLimit: Int): String {
    fun MethodFormula.formatNode(indent: Int): String {
        val currentIndent = " ".repeat(indent)

        return when (this) {
            is And -> {
                val children = all.joinToString(",\n") { it.formatNode(indent + 4) }
                wrapMultiline(
                    prefix = "And(",
                    body = children,
                    suffix = ")",
                    currentIndent = currentIndent,
                    lineLengthLimit = lineLengthLimit
                )
            }

            is MethodFormula.Or -> {
                val children = any.joinToString(",\n") { it.formatNode(indent + 4) }
                wrapMultiline(
                    prefix = "Or(",
                    body = children,
                    suffix = ")",
                    currentIndent = currentIndent,
                    lineLengthLimit = lineLengthLimit
                )
            }

            True -> "True"
            MethodFormula.False -> "False"

            is Cube -> this.prettyPrint(manager, currentIndent, lineLengthLimit)

            is MethodFormula.Literal -> wrapMultiline(
                prefix = "",
                body = this.prettyPrint(manager),
                suffix = "",
                currentIndent = currentIndent,
                lineLengthLimit = lineLengthLimit
            )
        }
    }

    return formatNode(0)
}

private fun TaintRegisterStateAutomata.EdgeEffect.prettyPrint(lineLengthLimit: Int = 40): String {
    val parts = mutableListOf<TaintRegisterStateAutomata.MethodPredicate>()
    assignMetaVar.values.flatMapTo(parts) { it }
    return parts.prettyPrint(lineLengthLimit)
}

private fun TaintRegisterStateAutomata.EdgeCondition.prettyPrint(lineLengthLimit: Int = 40): String {
    val parts = mutableListOf<TaintRegisterStateAutomata.MethodPredicate>()
    readMetaVar.values.flatMapTo(parts) { it }
    parts.addAll(other)
    return parts.prettyPrint(lineLengthLimit)
}

private fun List<TaintRegisterStateAutomata.MethodPredicate>.prettyPrint(lineLengthLimit: Int = 40): String {
    val predicates = map {
        val predicateStr = it.predicate.prettyPrint()
        if (it.negated) "Not($predicateStr)" else predicateStr
    }

    val predicatesStr = when (predicates.size) {
        0 -> "T"
        1 -> predicates.single()
        else -> predicates.joinToString(",\n", prefix = "And(", postfix = ")")
    }

    return wrapMultiline(prefix = "", predicatesStr, suffix = "", currentIndent = "", lineLengthLimit)
}

private fun MethodFormula.Literal.prettyPrint(manager: MethodFormulaManager): String {
    val predicateStr = manager.predicate(predicate).prettyPrint()
    return if (negated) "Not($predicateStr)" else predicateStr
}

private fun Cube.prettyPrint(
    manager: MethodFormulaManager,
    currentIndent: String = "",
    lineLengthLimit: Int = 40
): String {
    val predicates = cube.prettyPrint(manager)
    val predicatesStr = when (predicates.size) {
        0 -> "T"
        1 -> predicates.single()
        else -> {
            val predicateIndent = currentIndent + " ".repeat(4)
            predicates.joinToString(",\n", prefix = "And(", postfix = ")") { predicateIndent + it }
        }
    }

    val cubeStr = if (negated) "Not($predicatesStr)" else predicatesStr

    return wrapMultiline(prefix = "", cubeStr, suffix = "", currentIndent, lineLengthLimit)
}

private fun MethodFormulaCubeCompact.prettyPrint(manager: MethodFormulaManager): List<String> {
    val result = mutableListOf<String>()
    positiveLiterals.forEach { litVar ->
        result += manager.predicate(litVar).prettyPrint()
    }
    negativeLiterals.forEach { litVar ->
        result += "Not(${manager.predicate(litVar).prettyPrint()})"
    }
    return result
}

private fun Predicate.prettyPrint(): String{
    if (constraint == null) return "P(${signature.prettyPrint()})"
    return "P(${signature.prettyPrint()}, ${constraint.prettyPrint()})"
}

private fun MethodSignature.prettyPrint(): String =
    "${enclosingClassName.name ?: "*"}.${methodName.name ?: "*"}"

private fun MethodConstraint.prettyPrint(): String = when (this) {
    is ClassModifierConstraint -> "C@($modifier)"
    is MethodModifierConstraint -> "M@($modifier)"
    is NumberOfArgsConstraint -> "Args($num)"
    is ParamConstraint -> prettyPrint()
}

private fun ParamConstraint.prettyPrint(): String {
    val position = when (position) {
        is Position.Argument -> "Arg(${position.index?.toString() ?: "*"})"
        Position.Object -> "Object"
        Position.Result -> "Result"
    }
    return "Param($position, $condition)"
}

private fun wrapMultiline(
    prefix: String,
    body: String,
    suffix: String,
    currentIndent: String,
    lineLengthLimit: Int
): String {
    val lines = body.split("\n")
    val singleLine = lines.joinToString(" ", prefix, suffix) { it.trim() }
    if (singleLine.length + currentIndent.length <= lineLengthLimit) {
        return "$currentIndent$singleLine"
    }

    return buildString {
        append(currentIndent)
        append(prefix.trimStart())
        append("\n")
        for (line in lines) {
            append(line)
            append("\n")
        }
        append(currentIndent)
        append(suffix)
    }
}
