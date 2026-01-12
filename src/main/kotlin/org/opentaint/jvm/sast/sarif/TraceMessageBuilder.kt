package org.opentaint.jvm.sast.sarif

import mu.KLogging
import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.ap.ifds.TaintMarkAccessor
import org.opentaint.dataflow.ap.ifds.access.InitialFactAp
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
import org.opentaint.dataflow.util.SarifTraits
import org.opentaint.ir.api.common.CommonMethod
import org.opentaint.ir.api.common.cfg.CommonAssignInst
import org.opentaint.ir.api.common.cfg.CommonCallExpr
import org.opentaint.ir.api.common.cfg.CommonInst
import org.opentaint.ir.api.common.cfg.CommonReturnInst
import org.opentaint.ir.api.jvm.cfg.JIRThrowInst
import org.opentaint.org.opentaint.semgrep.pattern.Mark

data class TracePathNodeWithMsg(
    val node: TracePathNode,
    val kind: String,
    val message: String,
)

class TraceMessageBuilder(
    private val traits: SarifTraits<CommonMethod, CommonInst>,
    private val sinkMessage: String,
    private val ruleId: String,
) {
    private val stringBuilderAppendName = "String concatenation"
    private val initializerSuffix = "initializer"
    private val classInitializerSuffix = "class initializer"
    private val defaultTaintMark = "marked"

    private fun getMethodCalleeName(node: TracePathNode): String? {
        val callExpr = traits.getCallExpr(node.statement)
        return callExpr?.let { traits.getCallee(it).name }
    }

    private fun badOutput(reason: String) =
        "<#[$reason]#>"

    private fun getMethodCalleeNameInPrint(method: String, className: String): String {
        if (method == "<init>")
            return "\"$className\" $initializerSuffix"
        if (method == "<clinit>")
            return "\"$className\" $classInitializerSuffix"
        if (className == "StringBuilder" && method == "append")
            return stringBuilderAppendName
        return "\"$method\""
    }

    private fun getMethodCalleeNameInPrint(node: TracePathNode): String {
        val callExpr = traits.getCallExpr(node.statement)
        val name = getMethodCalleeName(node)
        if (callExpr == null || name == null)
            return badOutput("bad callee")
        val className = traits.getCalleeClassName(callExpr)
        return getMethodCalleeNameInPrint(name, className)
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
                    when (it) {
                        is TraceEntryAction.CallRuleAction -> {
                            it.action.all { mark -> (mark is RemoveMark || mark is RemoveAllMarks) }
                        }

                        is TraceEntryAction.SequentialSourceRule -> false
                    }
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

    fun TraceEntry?.isPureEntryPoint() =
        when (this) {
            is TraceEntry.SourceStartEntry -> {
                (sourcePrimaryAction == null && sourceOtherActions.all { it is TraceEntryAction.EntryPointSourceRule })
            }

            is TraceEntry.Action -> {
                (primaryAction == null && otherActions.all { it is TraceEntryAction.EntryPointSourceRule })
            }

            else -> false
        }

    private fun createEntryMessage(node: TracePathNode) =
        "Inside of ${getMethodCalleeNameInPrint(node)}"

    private fun createExitMessage(node: TracePathNode): String {
        val name = node.statement.location.method.name
        val className = traits.getMethodClassName(node.statement.location.method)
        return "Exiting ${getMethodCalleeNameInPrint(name, className)}"
    }

    private fun InitialFactAp.getMark(): Mark {
        val taintMarks = getAllAccessors().filterIsInstance<TaintMarkAccessor>()
        if (taintMarks.size != 1) {
            logger.error { "Expected exactly one taint mark but got ${taintMarks.size}!" }
        }
        if (taintMarks.isEmpty()) {
            return Mark.TaintMark
        }
        return Mark.getMarkFromString(taintMarks.first().mark, ruleId)
    }

    data class TaintInfo(val mark: Mark, val pos: AccessPathBase)

    private fun Mark?.inMessage(): String {
        return when (this) {
            is Mark.ArtificialMark -> defaultTaintMark
            is Mark.StateMark -> badOutput("unexpected mark")
            is Mark.TaintMark -> defaultTaintMark
            is Mark.StringMark -> mark
            null -> badOutput("unresolved mark name")
        }
    }

    private fun TraceEntry.relevantEdges(): List<TraceEdge> {
        return when (this) {
            is TraceEntry.Action -> (edges - unchanged).toList()
            else -> edges.toList()
        }
    }

    private fun TaintInfo.print(node: TracePathNode, relation: String = "at"): String {
        return "${mark.inMessage()} data $relation ${pos.inMessage(node)}"
    }

    private fun printTaints(node: TracePathNode, taints: List<TaintInfo>, relation: String = "at"): String {
        val relevant = if (taints.any { it.mark is Mark.StringMark }) {
            taints.filter { it.mark is Mark.StringMark }
        } else taints
        return relevant.joinToString(", ") { it.print(node, relation) }
    }

    private fun printPositions(node: TracePathNode, taints: List<TaintInfo>): String {
        val relevant = taints.map { it.pos }.distinct()
        return relevant.joinToString(", ") { it.inMessage(node) }
    }

    private fun printMarks(taints: List<TaintInfo>): String {
        if (taints.any { it.mark is Mark.StringMark }) {
            val relevant = taints.map { it.mark }.filterIsInstance<Mark.StringMark>().distinct()
            return relevant.joinToString(", ") { it.inMessage() }
        }
        if (taints.any { it.mark is Mark.StateMark }) {
            return badOutput("state")
        }
        return defaultTaintMark
    }

    private fun factToTaintInfo(fact: InitialFactAp): TaintInfo? {
        val mark = fact.getMark()
        if (mark is Mark.StateMark) return null
        return TaintInfo(mark, fact.base)
    }

    data class EdgesInfo(val starts: List<TaintInfo>, val follows: List<TaintInfo>)

    private fun mapToTaintInfos(input: Collection<TraceEdge>) =
        input.mapNotNull { factToTaintInfo(it.fact) }

    private fun TraceEntry?.collectDataflow(): EdgesInfo {
        if (this == null) {
            return EdgesInfo(emptyList(), emptyList())
        }

        val starts = if (this !is TraceEntryAction.SourceAction) {
            mapToTaintInfos(relevantEdges())
        } else emptyList()

        fun getActionEdges(action: TraceEntryAction?) =
            when (action) {
                is TraceEntryAction.PassAction -> mapToTaintInfos(action.edgesAfter)

                is TraceEntryAction.SourceAction -> mapToTaintInfos(action.sourceEdges)

                else -> emptyList()
            }

        val follows = when(this) {
            is TraceEntry.Action -> otherActions.flatMap { getActionEdges(it) } + getActionEdges(primaryAction)

            else -> emptyList()
        }

        return EdgesInfo(starts.distinct(), follows.distinct())
    }

    private fun TraceEntry?.collectStarts() =
        this?.collectDataflow()?.starts ?: emptyList()

    private fun TraceEntry?.collectFollows() =
        this?.collectDataflow()?.follows ?: emptyList()

    private fun createTraceEntryMessage(node: TracePathNode): String {
        return when (val entry = node.entry) {
            is TraceEntry.Final -> entry.createMessage(node)

            is TraceEntry.MethodEntry -> {
                val methodName = entry.entryPoint.method.name
                val taints = printTaints(node, entry.collectStarts())
                val withTaints = if (taints.isEmpty()) "" else " with $taints"
                "Entering \"$methodName\"$withTaints"
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
                            is TraceEntryAction.SequentialSourceRule -> otherAction.createMessage(node)
                        }
                    }
                }
                else {
                    createMethodCallTaintPropagationMessageWithTaints(node)
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
                            is TraceEntryAction.SequentialSourceRule -> otherAction.createMessage(node)
                        }
                    }
                }
                else {
                    if (entry.isPureEntryPoint()) {
                        createEntryPointMessage(node, entry.collectDataflow().follows)
                    }
                    else {
                        createMethodCallTaintPropagationMessageWithTaints(node)
                    }
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

    private fun groupPrintableTraces(traces: List<TracePathNode>): List<List<TracePathNode>> {
        val result = mutableListOf<List<TracePathNode>>()
        var curList = mutableListOf<TracePathNode>()

        fun addCurListAndClean() {
            if (curList.isNotEmpty()) {
                result.add(curList)
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
                    if (entry.isPureEntryPoint()) {
                        addAsSingle(trace)
                    }
                    else {
                        curList.add(trace)
                    }
                }

                is TraceEntry.Action -> {
                    if (entry.primaryAction is TraceEntryAction.Sequential) {
                        curList.add(trace)
                        addCurListAndClean()
                    } else if (entry.primaryAction is TraceEntryAction.CallAction || entry.otherActions.any { it is TraceEntryAction.CallAction }) {
                        curList.add(trace)
                    }
                    else {
                        addAsSingle(trace)
                    }
                }

                else -> addAsSingle(trace)
            }
        }
        addCurListAndClean()
        return result
    }

    private fun getMarkVarName(action: CommonTaintAction, neutralMark: String): Mark? {
        val name = when (action) {
            is AssignMark -> action.mark.name
            is CopyMark -> action.mark.name
            is CopyAllMarks -> return Mark.StringMark(neutralMark)
            else -> return null
        }
        return Mark.getMarkFromString(name, ruleId)
    }

    private fun getAssignTaintOut(entry: TraceEntry?) = when (entry?.statement) {
        is CommonReturnInst -> "the returning value"
        null -> badOutput("unresolved null assignee")
        else -> entry.let {
            traits.getReadableAssignee(entry.statement)
        } ?: badOutput("unresolved assignee")
    }

    data class TaintsWithOwner(val node: TracePathNode, val taints: List<TaintInfo>)
    private fun getGroupTraceMessage(start: TaintsWithOwner, follow: TaintsWithOwner, isSource: Boolean): String {
        if (follow.taints.isEmpty())
            return "Point of interest"
        if (start.taints.isEmpty()) {
            val results = printTaints(follow.node, follow.taints, "to")
            return "Puts $results"
        }
        val results = printTaints(follow.node, follow.taints)
        val condition = printTaints(start.node, start.taints)
        val markSource = if (isSource) "Creates" else "Takes"
        return "$markSource $condition and ends up with $results"
    }

    fun createGroupTraceMessage(group: List<TracePathNode>): List<TracePathNodeWithMsg> =
        groupPrintableTraces(group).map { printableGroup ->
            if (printableGroup.size == 1) {
                val node = printableGroup.first()
                TracePathNodeWithMsg(node, getSarifKind(node), createTraceEntryMessage(node))
            }
            else {
                val groupKind = getGroupKind(printableGroup)
                val lastNode = printableGroup.last()
                val firstNode = printableGroup.first()
                val starts = firstNode.entry.collectStarts()
                val follows = lastNode.entry.collectFollows()
                val message = getGroupTraceMessage(
                    TaintsWithOwner(firstNode, starts),
                    TaintsWithOwner(lastNode, follows),
                    printableGroup.any { it.kind == TracePathNodeKind.SOURCE },
                )
                TracePathNodeWithMsg(lastNode, groupKind, message)
            }
        }

    private fun printReturnedValue(node: TracePathNode): String {
        val assignee = traits.getReadableAssignee(node.statement)
        if (assignee == null || traits.isRegister(assignee)) return "the returned value"
        return "$assignee"
    }

    private fun printArgument(node: TracePathNode, index: Int) =
        if (node.kind != TracePathNodeKind.CALL || node.entry is TraceEntry.Final) {
            traits.printArgument(node.statement.location.method, index)
        }
        else {
            val stmt = node.statement as? CommonAssignInst
            val call = stmt?.let { it.rhv as? CommonCallExpr }
            val default = traits.printArgumentNth(index, call?.let { traits.getCallee(it).name })
            val argument = call?.let {
                it.args.getOrNull(index)?.let { arg -> traits.getReadableValue(node.statement, arg) }
            }
            argument ?: default
        }

    private fun AccessPathBase.inMessage(node: TracePathNode) = when (this) {
        is AccessPathBase.This -> traits.printThis(node.statement)
        is AccessPathBase.Argument -> printArgument(node, idx)
        is AccessPathBase.ClassStatic -> "a static field"
        is AccessPathBase.LocalVar -> {
            traits.getLocalName(node.statement.location.method, idx)?.let { "\"$it\"" } ?: "a local variable"
        }
        is AccessPathBase.Return -> printReturnedValue(node)
        is AccessPathBase.Constant -> "a const value"
        else -> badOutput("unresolved base")
    }

    private fun Position.inMessage(node: TracePathNode): String = when (this) {
        is This -> traits.printThis(node.statement)
        is Argument -> printArgument(node, index)
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

    private fun CommonTaintAction.getTainted(node: TracePathNode) = when (this) {
        is CopyMark -> to.inMessage(node)
        is CopyAllMarks -> to.inMessage(node)
        is AssignMark -> position.inMessage(node)
        is RemoveMark -> badOutput("!Unmarked")
        is RemoveAllMarks -> badOutput("!Unmarked")
        else -> badOutput("!UnknownMark")
    }

    private fun CommonTaintAction.getInitial(node: TracePathNode) = when (this) {
        is CopyMark -> from.inMessage(node)
        is CopyAllMarks -> from.inMessage(node)
        is AssignMark -> badOutput("!Unknown")
        is RemoveMark -> badOutput("!Unmarked")
        is RemoveAllMarks -> badOutput("!Unmarked")
        else -> badOutput("!UnknownMark")
    }

    data class TaintPropagationInfo(val taint: String, val from: String?, val to: String)

    private fun getCallAction(node: TracePathNode): String {
        if (node.kind == TracePathNodeKind.OTHER) {
            return "Method"
        }
        return "Call to"
    }

    private fun createMethodCallTaintPropagationMessage(
        node: TracePathNode,
        taints: List<TaintPropagationInfo>
    ): String {
        val calleeName = getMethodCalleeNameInPrint(node)
        val callAction = getCallAction(node)
        if (calleeName == stringBuilderAppendName) {
            val taint = taints.joinToString(", ") { it.taint }
            return "Concatenated String contains $taint data"
        }
        val propagated = mutableListOf<TaintPropagationInfo>()
        val created = mutableListOf<TaintPropagationInfo>()
        for (taint in taints) {
            if (taint.from == null)
                created.add(taint)
            else
                propagated.add(taint)
        }
        val propagatedJoin = propagated.joinToString(", ") { "${it.taint} data from ${it.from} to ${it.to}" }
        val createdJoin = created.joinToString(", ") { "${it.taint} data to ${it.to}" }
        val joiner = if (propagatedJoin.isNotEmpty() and createdJoin.isNotEmpty()) " and " else ""
        val propagatedText = if (propagatedJoin.isNotEmpty()) " propagates $propagatedJoin" else ""
        val createdText = if (createdJoin.isNotEmpty()) " puts $createdJoin" else ""
        val taintChanges = "$propagatedText$joiner$createdText"
        if (taintChanges.isEmpty() && node.kind == TracePathNodeKind.SOURCE) {
            return "Call to $calleeName creates a mark"
        }
        return "$callAction $calleeName$taintChanges"
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
    ): String {
        val calleeName = getMethodCalleeNameInPrint(node)
        val infos = node.entry.collectDataflow()
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
            return createMethodCallTaintPropagationMessage(node, listOf(TaintPropagationInfo(taint, from, to)))
        }
        val results = printTaints(node, infos.follows)
        if (infos.starts.isEmpty()) {
            return "$subject produces $results"
        }
        val condition = printTaints(node, infos.starts)
        return "$subject takes $condition, which results in $results"
    }

    private fun createTaintedObjectCreationMessage(
        callee: String,
        taint: String,
    ): String {
        if (taint == defaultTaintMark)
            return "$callee creates a $defaultTaintMark object"
        return "$callee creates an object with $taint data"
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
            return createTaintedObjectCreationMessage(calleeName, taint)
        }
        return "Call to $calleeName puts $taint data to $pos"
    }

    private fun createMethodCallTaintCreationMessageWithTaints(
        node: TracePathNode,
    ): String {
        var calleeName = getMethodCalleeNameInPrint(node)
        val taintInfos = node.entry.collectFollows()
        if (calleeName == stringBuilderAppendName)
            // it's unlikely this method will once become a source of bad/leaked data...but who knows?
            calleeName = "\"StringBuilder.append\""
        if (calleeName.endsWith(initializerSuffix)) {
            val taint = printMarks(taintInfos)
            return createTaintedObjectCreationMessage(calleeName, taint)
        }
        val taint = printTaints(node, taintInfos, "to")
        if (taint.isEmpty()) {
            if (node.kind == TracePathNodeKind.SOURCE) {
                return "Call to $calleeName creates a mark"
            }
            val callAction = getCallAction(node)
            return "$callAction $calleeName"
        }
        return "Call to $calleeName puts $taint"
    }

    private fun TraceEntry.Final.createMessage(node: TracePathNode): String {
        if (node.kind != TracePathNodeKind.SINK) {
            if (node.statement is CommonReturnInst)
                return createExitMessage(node)
            val callExpr = traits.getCallExpr(node.statement)
            val starts = node.entry.collectStarts()
            val suffix = if (starts.isEmpty()) "" else " with ${printTaints(node, starts)}"
            if (callExpr != null)
                return "Calling ${getMethodCalleeNameInPrint(node)}$suffix"
            if (node.statement is JIRThrowInst)
                return "Exception thrown"
            return badOutput("unknown final")
        }
        return createDefaultMessage(node)
    }

    private fun createEntryPointMessage(node: TracePathNode, taints: List<TaintInfo>): String {
        var tainted = printTaints(node, taints)
        if (tainted.isEmpty()) {
            tainted = "tainted data"
        }
        return "Potential $tainted at the method entry"
    }

    private fun getTaintPropagationInfo(node: TracePathNode, action: CommonTaintAction, neutralMark: String): TaintPropagationInfo? {
        if (action is RemoveMark || action is RemoveAllMarks)
            return null
        val mark = getMarkVarName(action, neutralMark)
        if (mark is Mark.StateMark) {
            return null
        }
        val markReadable = mark?.inMessage() ?: defaultTaintMark
        val initial = if (action is AssignMark) null else action.getInitial(node)
        val follow = action.getTainted(node)
        return TaintPropagationInfo(markReadable, initial, follow)
    }

    private fun TraceEntryAction.collectTaintPropagationInfo(node: TracePathNode, actions: Iterable<CommonTaintAction>): List<TaintPropagationInfo> {
        if (this !is TraceEntryAction.PassAction) return emptyList()

        val neutralMark = printMarks(node.entry.collectFollows())
        // note: we may have multiple marks with the same name since we discard mark artificial suffix
        return actions.mapNotNull { getTaintPropagationInfo(node, it, neutralMark) }.distinct()
    }

    private fun TraceEntryAction.EntryPointSourceRule.createMessage(node: TracePathNode): String {
        val taints = this.collectTaintPropagationInfo(node, action).map { "${it.to} as ${it.taint}" }
        if (taints.isEmpty()) {
            return "Marked data at method entry"
        }
        val taintsJoin = taints.joinToString(", ")
        return "Method entry marks $taintsJoin"
    }

    private fun TraceEntryAction.SequentialSourceRule.createMessage(node: TracePathNode): String {
        val taints = this.collectTaintPropagationInfo(node, action).map { "${it.taint} data at ${it.to}" }
        if (taints.isEmpty()) {
            return "Value with marked data"
        }
        val taintsJoin = taints.joinToString(", ")
        return "Value with $taintsJoin"
    }

    private fun TraceEntryAction.CallRule.createMessage(node: TracePathNode): String {
        val taintInfos = this.collectTaintPropagationInfo(node, action)
        return createMethodCallTaintPropagationMessage(node, taintInfos)
    }

    private fun TraceEntryAction.CallSummary.createMessage(node: TracePathNode): String {
        return createMethodCallTaintPropagationMessageWithTaints(node)
    }

    private fun TraceEntryAction.CallSourceRule.createMessage(node: TracePathNode): String {
        val taintInfos = this.collectTaintPropagationInfo(node, action)
        return createMethodCallTaintPropagationMessage(node, taintInfos)
    }

    private fun TraceEntryAction.CallSourceSummary.createMessage(node: TracePathNode): String {
        return createMethodCallTaintCreationMessageWithTaints(node)
    }

    private fun TraceEntryAction.Sequential.createMessage(node: TracePathNode): String {
        val assignee = getAssignTaintOut(node.entry)
        val taint = printMarks(node.entry.collectFollows())
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
