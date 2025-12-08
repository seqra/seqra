package org.opentaint.jvm.sast.sarif

import mu.KLogging
import org.opentaint.ir.api.common.CommonMethod
import org.opentaint.ir.api.common.cfg.CommonInst
import org.opentaint.ir.api.common.cfg.CommonReturnInst
import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.ap.ifds.trace.MethodTraceResolver.TraceEdge
import org.opentaint.dataflow.ap.ifds.trace.MethodTraceResolver.TraceEntry
import org.opentaint.dataflow.ap.ifds.trace.MethodTraceResolver.TraceEntryAction
import org.opentaint.dataflow.configuration.CommonTaintAction
import org.opentaint.dataflow.configuration.jvm.Argument
import org.opentaint.dataflow.configuration.jvm.AssignMark
import org.opentaint.dataflow.configuration.jvm.ClassStatic
import org.opentaint.dataflow.configuration.jvm.CopyAllMarks
import org.opentaint.dataflow.configuration.jvm.CopyMark
import org.opentaint.dataflow.configuration.jvm.Position
import org.opentaint.dataflow.configuration.jvm.PositionAccessor
import org.opentaint.dataflow.configuration.jvm.PositionWithAccess
import org.opentaint.dataflow.configuration.jvm.RemoveAllMarks
import org.opentaint.dataflow.configuration.jvm.RemoveMark
import org.opentaint.dataflow.configuration.jvm.Result
import org.opentaint.dataflow.configuration.jvm.This
import org.opentaint.dataflow.ap.ifds.TaintMarkAccessor
import org.opentaint.dataflow.ap.ifds.access.InitialFactAp
import org.opentaint.dataflow.util.SarifTraits

const val ArtificialMetavarName = "<ARTIFICIAL>"
const val ArtificialStateName = "__<STATE>__"
const val GeneralTaintName = "taint"

data class TracePathNodeWithMsg(
    val node: TracePathNode,
    val kind: String,
    val message: String,
)

enum class GroupTraceKind {
    SOURCE_CALL, SOURCE_ASSIGN, CALL, CALL_ASSIGN, SINGLE,
}

sealed interface Mark {
    data class StringMark(val mark: String) : Mark

    data object ArtificialMark : Mark

    data object StateMark : Mark

    data object TaintMark : Mark
}

class GroupTraceKindTracker {
    private var isSource: Boolean = false
    private var isAssign: Boolean = false
    private var isPrintable: Boolean = false

    private fun getKind(): GroupTraceKind {
        if (!isPrintable)
            return GroupTraceKind.SINGLE
        if (isSource && isAssign)
            return GroupTraceKind.SOURCE_ASSIGN
        if (!isSource && isAssign)
            return GroupTraceKind.CALL_ASSIGN
        if (isSource)
            return GroupTraceKind.SOURCE_CALL
        return GroupTraceKind.CALL
    }

    private fun reset() {
        isSource = false
        isAssign = false
        isPrintable = false
    }

    fun setCall() {
        isPrintable = true
    }

    fun setSourceCall() {
        isSource = true
        isPrintable = true
    }

    fun setAssign() {
        isAssign = true
        isPrintable = true
    }

    fun getKindAndReset() = getKind().also { reset() }
}

data class GroupTraceWithKind(
    val nodes: List<TracePathNode>,
    val kind: GroupTraceKind,
)

class TraceMessageBuilder(
    private val traits: SarifTraits<CommonMethod, CommonInst>,
    private val sinkMessage: String,
    private val ruleId: String,
) {
    private val stringBuilderAppendName = "String concatenation"
    private val initializerSuffix = "initializer"
    private val classInitializerSuffix = "class initializer"

    private fun getMethodCalleeName(node: TracePathNode): String? {
        val callExpr = traits.getCallExpr(node.statement)
        return callExpr?.let { traits.getCallee(it).name }
    }

    private fun badOutput(reason: String) =
        "<#[$reason]#>"

    private fun getMethodCalleeNameInPrint(node: TracePathNode): String {
        val callExpr = traits.getCallExpr(node.statement) ?: return badOutput("bad callee")
        val className = traits.getCalleeClassName(callExpr)
        val name = getMethodCalleeName(node)
        if (name == "<init>")
            return "\"$className\" $initializerSuffix"
        if (name == "<clinit>")
            return "\"$className\" $classInitializerSuffix"
        if (className == "StringBuilder" && name == "append")
            return stringBuilderAppendName
        return "\"$name\""
    }

    private fun createDefaultMessage(node: TracePathNode) = when(node.kind) {
        TracePathNodeKind.SOURCE -> badOutput("unresolved taint_source")
        TracePathNodeKind.SINK -> sinkMessage
        TracePathNodeKind.CALL -> badOutput("unresolved call")
        TracePathNodeKind.OTHER -> badOutput("unknown")
        TracePathNodeKind.RETURN -> generateMessageForReturn(node)
    }

    private fun getSarifKind(node: TracePathNode) = when(node.kind) {
        TracePathNodeKind.SOURCE -> "taint"
        TracePathNodeKind.SINK -> "taint"
        TracePathNodeKind.CALL -> "call"
        TracePathNodeKind.OTHER -> "unknown"
        TracePathNodeKind.RETURN -> "return"
    }

    fun isGoodTrace(node: TracePathNode): Boolean {
        // filtering return nodes that do not contain any new information
        if (node.entry == null && node.kind == TracePathNodeKind.RETURN) {
            return false
        }

        val entry = node.entry as? TraceEntry.Action ?: return true

        val primaryAction = entry.primaryAction

        // filtering CallSummary traces where tainted data ends up where it started
        if (primaryAction is TraceEntryAction.CallSummary) {
            val summaryTraceFacts = primaryAction.summaryTrace.final.edges
            if (summaryTraceFacts.all { it is TraceEdge.MethodTraceEdge && it.initialFact.base == it.fact.base }) {
                logger.debug {
                    "Skipping trace entry on line ${traits.lineNumber(node.statement)} " +
                            "because initial and final places are the same"
                }
                return false
            }
        }

        // filtering nodes that became unimportant
        if (primaryAction is TraceEntryAction.UnresolvedCallSkip) {
            return false
        }

        // filtering Call trace entries that contain unexpected Remove actions
        if (primaryAction == null) {
            if (node.entry.otherActions.all {
                    it is TraceEntryAction.CallRuleAction && (it.action is RemoveMark || it.action is RemoveAllMarks)
                }) {
                logger.warn {
                    "Trace entry on line ${traits.lineNumber(node.statement)} because of unexpected Remove action!"
                }
                return false
            }
        }

        // filtering calls to toString methods
        if (node.kind != TracePathNodeKind.SOURCE && node.kind != TracePathNodeKind.SINK) {
            val name = getMethodCalleeName(node)
            if (name == "toString")
                return false
        }

        return true
    }

    private fun createEntryMessage(node: TracePathNode) =
        "Calling ${getMethodCalleeNameInPrint(node)}"

    private fun createExitMessage(node: TracePathNode) =
        "Exiting \"${node.statement.location.method.name}\""

    private fun InitialFactAp.getMark(): Mark {
        val taintMarks = getAllAccessors().filterIsInstance<TaintMarkAccessor>()
        check(taintMarks.size == 1) { "expected exactly one taint mark!" }
        return getMarkFromString(taintMarks.first().mark)
    }

    data class TaintInfo(val mark: Mark, val pos: AccessPathBase)

    private fun Mark?.inMessage(): String {
        return when (this) {
            is Mark.ArtificialMark -> "tainted"
            is Mark.StateMark -> badOutput("unexpected mark")
            is Mark.TaintMark -> "tainted"
            is Mark.StringMark -> mark
            null -> badOutput("unresolved mark name")
        }
    }

    private fun TaintInfo.print(node: TracePathNode, relation: String = "at"): String {
        return "${mark.inMessage()} data $relation ${pos.inMessage(node)}"
    }

    private fun printTaints(node: TracePathNode, taints: List<TaintInfo>, relation: String = "at"): String {
        return taints.joinToString(", ") { it.print(node, relation) }
    }

    private fun printPositions(node: TracePathNode, taints: List<TaintInfo>): String {
        return taints.joinToString(", ") { it.pos.inMessage(node) }
    }

    private fun printMarks(taints: List<TaintInfo>): String {
        return taints.joinToString(", ") { it.mark.inMessage() }
    }

    data class EdgesInfo(val starts: List<TaintInfo>, val follows: List<TaintInfo>)
    private fun collectDataflow(edges: List<TraceEdge>): EdgesInfo {
        val starts = mutableListOf<TaintInfo>()
        val follows = mutableListOf<TaintInfo>()

        fun addTaintInfo(where: MutableList<TaintInfo>, what: InitialFactAp) {
            val mark = what.getMark()
            if (mark !is Mark.StateMark)
                where.add(TaintInfo(mark, what.base))
        }

        for (edge in edges) {
            addTaintInfo(follows, edge.fact)
            if (edge is TraceEdge.MethodTraceEdge) {
                addTaintInfo(starts, edge.initialFact)
            }
        }

        return EdgesInfo(starts.distinct(), follows.distinct())
    }

    private fun createTraceEntryMessage(node: TracePathNode): String {
        return when (val entry = node.entry) {
            is TraceEntry.Final -> entry.createMessage(node)

            is TraceEntry.MethodEntry -> {
                val methodName = entry.entryPoint.method.name
                val taints = printTaints(node, collectDataflow(entry.edges.toList()).starts)
                "Entering \"$methodName\" with $taints"
            }

            is TraceEntry.Action -> {
                val primaryAction = entry.primaryAction
                val total = entry.otherActions.size + if (primaryAction != null) 1 else 0
                if (total == 1) {
                    if (primaryAction != null) {
                        return when (primaryAction) {
                            is TraceEntryAction.CallSummary -> primaryAction.createMessage(node)
                            is TraceEntryAction.Sequential -> primaryAction.createMessage(node)
                            is TraceEntryAction.CallSourceSummary -> primaryAction.createMessage(node)
                            is TraceEntryAction.UnresolvedCallSkip -> createDefaultMessage(node)
                        }
                    }
                    else {
                        when (val otherAction = entry.otherActions.first()) {
                            is TraceEntryAction.CallRule -> otherAction.createMessage(node)
                            is TraceEntryAction.CallSourceRule -> otherAction.createMessage(node)
                            is TraceEntryAction.EntryPointSourceRule -> otherAction.createMessage(node)
                        }
                    }
                }
                else {
                    createMethodCallTaintPropagationMessageWithTaints(node, entry.edges.toList())
                }
            }

            is TraceEntry.SourceStartEntry -> {
                val primaryAction = entry.sourcePrimaryAction
                val total = entry.sourceOtherActions.size + if (primaryAction != null) 1 else 0
                if (total == 1) {
                    if (primaryAction != null) {
                        return when (primaryAction) {
                            is TraceEntryAction.CallSourceSummary -> primaryAction.createMessage(node)
                        }
                    }
                    else {
                        when (val otherAction = entry.sourceOtherActions.first()) {
                            is TraceEntryAction.CallSourceRule -> otherAction.createMessage(node)
                            is TraceEntryAction.EntryPointSourceRule -> otherAction.createMessage(node)
                        }
                    }
                }
                else {
                    createMethodCallTaintPropagationMessageWithTaints(node, entry.edges.toList())
                }
            }

            is TraceEntry.Unchanged -> {
                badOutput("unchanged entry")
            }

            null -> when (node.kind) {
                TracePathNodeKind.RETURN -> createExitMessage(node)

                // calls that happen before reaching the taint source
                TracePathNodeKind.CALL -> createEntryMessage(node)

                else -> createDefaultMessage(node)
            }
        }
    }

    private fun getGroupKind(group: List<TracePathNode>): String {
        var kind = "unknown"
        if (group.size == 1) {
            kind = when (group.single().kind) {
                TracePathNodeKind.SOURCE -> "taint"
                TracePathNodeKind.SINK -> "taint"
                TracePathNodeKind.CALL -> "call"
                TracePathNodeKind.RETURN -> "return"
                TracePathNodeKind.OTHER -> "unknown"
            }
        } else {
            if (group.any {
                    it.kind == TracePathNodeKind.SOURCE || it.kind == TracePathNodeKind.SINK
                })
                kind = "taint"
        }
        return kind
    }

    private fun groupPrintableTraces(traces: List<TracePathNode>): List<GroupTraceWithKind> {
        val result = mutableListOf<GroupTraceWithKind>()
        var curList = mutableListOf<TracePathNode>()
        val kindTracker = GroupTraceKindTracker()

        fun addCurListAndClean() {
            if (curList.isNotEmpty()) {
                val kind = kindTracker.getKindAndReset()
                result.add(GroupTraceWithKind(curList, kind))
                curList = mutableListOf()
            }
        }

        fun addAsSingle(trace: TracePathNode) {
            addCurListAndClean()
            curList.add(trace)
            addCurListAndClean()
        }

        for (trace in traces) {
            when (val entry = trace.entry) {
                is TraceEntry.SourceStartEntry -> {
                    if (entry.sourcePrimaryAction is TraceEntryAction.CallAction || entry.sourceOtherActions.any { it is TraceEntryAction.CallAction }) {
                        curList.add(trace)
                        kindTracker.setSourceCall()
                    } else {
                        addAsSingle(trace)
                    }
                }

                is TraceEntry.Action -> {
                    if (entry.primaryAction is TraceEntryAction.Sequential) {
                        curList.add(trace)
                        kindTracker.setAssign()
                        addCurListAndClean()
                    } else if (entry.primaryAction is TraceEntryAction.CallAction || entry.otherActions.any { it is TraceEntryAction.CallAction }) {
                        curList.add(trace)
                        kindTracker.setCall()
                    }
                }

                else -> addAsSingle(trace)
            }
        }
        addCurListAndClean()
        return result
    }

    private fun getMarkFromString(rawMark: String): Mark {
        if (!rawMark.contains('#'))
            // running with config
            return Mark.StringMark(rawMark)
        val ruleLength = ruleId.length
        check(rawMark.length > ruleLength && rawMark[ruleLength] == '#') {
            "expected ruleId at the start of mark!"
        }
        val noRuleId = rawMark.substring(ruleLength + 1)
        if (noRuleId == GeneralTaintName)
            return Mark.TaintMark
        if (noRuleId.contains(ArtificialStateName))
            return Mark.StateMark
        if (noRuleId.contains(ArtificialMetavarName))
            return Mark.ArtificialMark
        val split = noRuleId.split("|")
        check(split.size >= 2) { "mark must contain at least two parts!" }
        return Mark.StringMark(split[1])
    }

    private fun getMarkVarName(action: CommonTaintAction, neutralMark: String): Mark? {
        val name = when (action) {
            is AssignMark -> action.mark.name
            is CopyMark -> action.mark.name
            is CopyAllMarks -> return Mark.StringMark(neutralMark)
            else -> return null
        }
        return getMarkFromString(name)
    }

    private fun getAssignTaintOut(entry: TraceEntry?) = when (entry?.statement) {
        is CommonReturnInst -> "the returning value"
        null -> badOutput("unresolved null assignee")
        else -> entry.let {
            traits.getReadableAssignee(entry.statement)
        } ?: badOutput("unresolved assignee")
    }

    private fun takeCalleeOf(node: TracePathNode): String {
        if (getMethodCalleeName(node) == null)
            return ""
        return " of ${getMethodCalleeNameInPrint(node)}"
    }

    data class TaintsWithOwner(val node: TracePathNode, val taints: List<TaintInfo>)
    private fun getGroupTraceMessage(start: TaintsWithOwner, follow: TaintsWithOwner): String {
        if (follow.taints.isEmpty())
            return "Intermediate line of the rule"
        val beginOf = takeCalleeOf(start.node)
        val endOf = takeCalleeOf(follow.node)
        if (start.taints.isEmpty()) {
            val results = printTaints(follow.node, follow.taints, "to")
            return "Puts $results$endOf"
        }
        val results = printTaints(follow.node, follow.taints)
        val condition = printTaints(start.node, start.taints)
        return "Takes $condition$beginOf and ends up with $results$endOf"
    }

    fun createGroupTraceMessage(group: List<TracePathNode>): List<TracePathNodeWithMsg> =
        groupPrintableTraces(group).map { printableGroup ->
            if (printableGroup.kind == GroupTraceKind.SINGLE
                || printableGroup.nodes.size == 1) {
                val node = printableGroup.nodes.first()
                TracePathNodeWithMsg(node, getSarifKind(node), createTraceEntryMessage(node))
            }
            else {
                val groupKind = getGroupKind(printableGroup.nodes)
                val lastNode = printableGroup.nodes.last()
                val firstNode = printableGroup.nodes.first()
                val starts = firstNode.entry?.edges?.let { collectDataflow(it.toList()).starts } ?: emptyList()
                val follows = lastNode.entry?.edges?.let { collectDataflow(it.toList()).follows } ?: emptyList()
                val message = getGroupTraceMessage(TaintsWithOwner(firstNode, starts), TaintsWithOwner(lastNode, follows))
                TracePathNodeWithMsg(lastNode, groupKind, message)
            }
        }

    private fun printReturnedValue(node: TracePathNode): String {
        val assignee = traits.getReadableAssignee(node.statement)
        if (assignee == null || traits.isRegister(assignee)) return "the returned value"
        return "$assignee"
    }

    private fun AccessPathBase.inMessage(node: TracePathNode) = when (this) {
        is AccessPathBase.This -> traits.printThis(node.statement)
        is AccessPathBase.Argument -> traits.printArgument(idx)
        is AccessPathBase.ClassStatic -> "a static variable"
        is AccessPathBase.LocalVar -> {
            traits.getLocalName(node.statement.location.method, idx)?.let { "\"$it\"" } ?: "a local variable"
        }
        is AccessPathBase.Return -> printReturnedValue(node)
        is AccessPathBase.Constant -> "a const value"
        else -> badOutput("unresolved value")
    }

    private fun Position.inMessage(node: TracePathNode): String = when (this) {
        is This -> traits.printThis(node.statement)
        is Argument -> traits.printArgument(index)
        is Result -> printReturnedValue(node)
        is PositionWithAccess -> when (this.access) {
            is PositionAccessor.ElementAccessor -> "an element of ${base.inMessage(node)}"
            is PositionAccessor.AnyFieldAccessor -> base.inMessage(node)
            is PositionAccessor.FieldAccessor ->
                (this.access as PositionAccessor.FieldAccessor).inMessage()?.let {
                    "$it of ${base.inMessage(node)}"
                } ?: base.inMessage(node)
        }
        is ClassStatic -> "\"$className\" static"
    }

    private fun PositionAccessor.FieldAccessor.inMessage(): String? {
        if (className.startsWith("java.lang.Object")) {
            if (fieldName == "Element")
                return "an element"
            if (fieldName == "MapValue")
                return "a map value"
            if (fieldName == "MapKey")
                return "a map key"
        }
        return null
    }

    private fun CommonTaintAction.getPropagated(node: TracePathNode) = when (this) {
        is CopyMark -> to.inMessage(node)
        is CopyAllMarks -> to.inMessage(node)
        is AssignMark -> position.inMessage(node)
        is RemoveMark -> "<!Untainted>"
        is RemoveAllMarks -> "<!Untainted>"
        else -> "<!Tainted>"
    }

    private fun CommonTaintAction.getTainted(node: TracePathNode) = when (this) {
        is CopyMark -> from.inMessage(node)
        is CopyAllMarks -> from.inMessage(node)
        is AssignMark -> "<!Unknown>"
        is RemoveMark -> "<!Untainted>"
        is RemoveAllMarks -> "<!Untainted>"
        else -> "<!Tainted>"
    }

    private fun createMethodCallTaintPropagationMessage(
        node: TracePathNode,
        taint: String,
        from: String,
        to: String,
    ): String {
        val calleeName = getMethodCalleeNameInPrint(node)
        if (calleeName == stringBuilderAppendName)
            return "Concatenated String contains $taint data"
        return "Call to $calleeName propagates $taint data from $from to $to"
    }

    private fun isOneMark(infos: EdgesInfo): Boolean {
        val all = infos.starts + infos.follows
        if (all.isEmpty())
            return true
        val mark = all[0].mark
        for (info in all) {
            if (mark != info.mark)
                return false
        }
        return true
    }

    private fun createMethodCallTaintPropagationMessageWithTaints(
        node: TracePathNode,
        edges: List<TraceEdge>,
    ): String {
        val calleeName = getMethodCalleeNameInPrint(node)
        val infos = collectDataflow(edges)
        if (calleeName == stringBuilderAppendName) {
            val taint = printMarks(infos.follows)
            return "Concatenated String contains data with $taint"
        }
        return createPropagationMessageFromTaints("Call to $calleeName", node, infos)
    }

    private fun createPropagationMessageFromTaints(
        subject: String,
        node: TracePathNode,
        infos: EdgesInfo,
    ): String {
        if (infos.follows.isEmpty()) {
            if (infos.starts.isEmpty()) {
                return subject
            }
            val condition = printTaints(node, infos.starts)
            return "$subject with $condition"
        }
        if (isOneMark(infos)) {
            val taint = infos.follows[0].mark.inMessage()
            val to = printPositions(node, infos.follows)
            if (infos.starts.isEmpty()) {
                return createMethodCallTaintCreationMessage(node, taint, to)
            }
            val from = printPositions(node, infos.starts)
            return createMethodCallTaintPropagationMessage(node, taint, from, to)
        }
        val results = printTaints(node, infos.follows)
        if (infos.starts.isEmpty()) {
            return "$subject produces $results"
        }
        val condition = printTaints(node, infos.starts)
        return "$subject takes $condition, which results in $results"
    }

    private fun createMethodCallTaintCreationMessage(
        node: TracePathNode,
        taint: String,
        pos: String
    ): String {
        var calleeName = getMethodCalleeNameInPrint(node)
        if (calleeName == stringBuilderAppendName)
        // it's unlikely this method will once become a source of bad/leaked data...but who knows?
            calleeName = "\"StringBuilder.append\""
        if (calleeName.endsWith(initializerSuffix)) {
            return "$calleeName creates an object with $taint data"
        }
        return "Call to $calleeName puts $taint data to $pos"
    }

    private fun createMethodCallTaintCreationMessageWithTaints(
        node: TracePathNode,
        taints: List<TraceEdge>,
    ): String {
        var calleeName = getMethodCalleeNameInPrint(node)
        val taintInfos = collectDataflow(taints).follows
        if (calleeName == stringBuilderAppendName)
            // it's unlikely this method will once become a source of bad/leaked data...but who knows?
            calleeName = "\"StringBuilder.append\""
        if (calleeName.endsWith(initializerSuffix)) {
            val taint = printMarks(taintInfos)
            return "$calleeName creates an object with $taint data"
        }
        val taint = printTaints(node, taintInfos, "to")
        return "Call to $calleeName puts $taint"
    }

    private fun createMethodCallTaintCreationMessage(
        node: TracePathNode,
        action: CommonTaintAction,
        neutralMark: String,
    ): String {
        val pos = (action as AssignMark).position.inMessage(node)
        val mark = getMarkVarName(action, neutralMark)
        if (mark is Mark.StateMark) {
            val calleeName = getMethodCalleeNameInPrint(node)
            return "Call to $calleeName"
        }
        return createMethodCallTaintCreationMessage(node, mark.inMessage(), pos)
    }

    private fun TraceEntry.Final.createMessage(node: TracePathNode): String {
        if (node.kind != TracePathNodeKind.SINK) {
            if (node.statement is CommonReturnInst)
                return createExitMessage(node)
            val callExpr = traits.getCallExpr(node.statement)
            val starts = collectDataflow(edges.toList()).starts
            val suffix = if (starts.isEmpty()) "" else " with ${printTaints(node, starts)}"
            if (callExpr != null)
                return "Calling ${getMethodCalleeNameInPrint(node)}$suffix"
            return badOutput("unknown final")
        }
        return createDefaultMessage(node)
    }

    private fun TraceEntryAction.EntryPointSourceRule.createMessage(node: TracePathNode): String {
        val pos = action.getTainted(node)
        val mark = getMarkVarName(action, "tainted") ?: "tainted"
        return "Potential $mark data at $pos of the method"
    }

    private fun TraceEntryAction.CallRule.createMessage(node: TracePathNode): String {
        if (action is CopyMark || action is CopyAllMarks) {
            val taintSource = action.getTainted(node)
            val taintFollow = action.getPropagated(node)
            val neutralMark = printMarks(collectDataflow(edges.toList()).follows)
            val mark = getMarkVarName(action, neutralMark).inMessage()
            return createMethodCallTaintPropagationMessage(node, mark, taintSource, taintFollow)
        }
        if (action is AssignMark) {
            val neutralMark = printMarks(collectDataflow(edges.toList()).follows)
            return createMethodCallTaintCreationMessage(node, action, neutralMark)
        }
        return badOutput("unknown CallRule")
    }

    private fun TraceEntryAction.CallSummary.createMessage(node: TracePathNode): String {
        return createMethodCallTaintPropagationMessageWithTaints(node, edges.toList())
    }

    private fun TraceEntryAction.CallSourceRule.createMessage(node: TracePathNode): String {
        val neutralMark = printMarks(collectDataflow(edges.toList()).follows)
        return createMethodCallTaintCreationMessage(node, action, neutralMark)
    }

    private fun TraceEntryAction.CallSourceSummary.createMessage(node: TracePathNode): String {
        return createMethodCallTaintCreationMessageWithTaints(node, edges.toList())
    }

    private fun TraceEntryAction.Sequential.createMessage(node: TracePathNode): String {
        val assignee = getAssignTaintOut(node.entry)
        val taint = printMarks(collectDataflow(edges.toList()).follows)
        return "$assignee is assigned a value with $taint data"
    }

    private fun generateMessageForReturn(node: TracePathNode): String {
        if (node.kind != TracePathNodeKind.RETURN) return badOutput("unknown Return")
        return "Returning from ${getMethodCalleeNameInPrint(node)}"
    }

    companion object {
        val logger = object : KLogging() {}.logger
    }
}
