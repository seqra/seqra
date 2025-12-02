package org.opentaint.jvm.sast.sarif

import mu.KLogging
import org.opentaint.ir.api.common.CommonMethod
import org.opentaint.ir.api.common.cfg.CommonInst
import org.opentaint.ir.api.common.cfg.CommonReturnInst
import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.ap.ifds.trace.MethodTraceResolver
import org.opentaint.dataflow.configuration.CommonTaintAction
import org.opentaint.dataflow.configuration.CommonTaintAssignAction
import org.opentaint.dataflow.util.SarifTraits
import org.opentaint.dataflow.configuration.jvm.CopyMark
import org.opentaint.dataflow.configuration.jvm.CopyAllMarks
import org.opentaint.dataflow.configuration.jvm.AssignMark
import org.opentaint.dataflow.configuration.jvm.RemoveMark
import org.opentaint.dataflow.configuration.jvm.RemoveAllMarks
import org.opentaint.dataflow.configuration.jvm.Position
import org.opentaint.dataflow.configuration.jvm.ClassStatic
import org.opentaint.dataflow.configuration.jvm.This
import org.opentaint.dataflow.configuration.jvm.Result
import org.opentaint.dataflow.configuration.jvm.Argument
import org.opentaint.dataflow.configuration.jvm.PositionWithAccess
import org.opentaint.dataflow.configuration.jvm.PositionAccessor

data class TracePathNodeWithMsg(
    val node: TracePathNode,
    val kind: String,
    val message: String,
)

enum class GroupTraceKind {
    SOURCE_CALL, SOURCE_ASSIGN, CALL, CALL_ASSIGN, SINGLE,
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
    private val localNameResolver: LocalNameResolver,
    private var taintType: String,
    private val sinkMessage: String,
) {
    private val stringBuilderAppendName = "String concatenation"
    private val initializerSuffix = " initializer"
    private val classInitializerSuffix = " class initializer"

    private var taintTypeNew: String? = null

    private fun getOrdinal(i: Int): String {
        val suffix = if (i % 100 in 11..13) "th" else when (i % 10) {
            1 -> "st"
            2 -> "nd"
            3 -> "rd"
            else -> "th"
        }
        return "$i$suffix"
    }

    private fun getMethodCalleeName(node: TracePathNode): String? {
        val callExpr = traits.getCallExpr(node.statement)
        return callExpr?.let { traits.getCallee(it).name }
    }

    private fun getMethodCalleeNameInPrint(node: TracePathNode): String {
        val callExpr = traits.getCallExpr(node.statement) ?: return "<callee>"
        val className = traits.getCalleeClassName(callExpr)
        val name = getMethodCalleeName(node)
        if (name == "<init>")
            return "\"$className\"$initializerSuffix"
        if (name == "<clinit>")
            return "\"$className\"$classInitializerSuffix"
        if (className == "StringBuilder" && name == "append")
            return stringBuilderAppendName
        return "\"$name\""
    }

    private fun createDefaultMessage(node: TracePathNode) = when(node.kind) {
        TracePathNodeKind.SOURCE -> "<unresolved taint_source>"
        TracePathNodeKind.SINK -> sinkMessage
        TracePathNodeKind.CALL -> "<unresolved call>"
        TracePathNodeKind.OTHER -> "<unknown>"
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
        // filtering CallSummary traces where tainted data ends up where it started
        if (node.entry is MethodTraceResolver.TraceEntry.CallSummary
            && node.entry.summaryTrace.initial.fact.base == node.entry.summaryTrace.final.fact.base) {
            logger.debug {
                "Skipping trace entry on line ${traits.lineNumber(node.statement)}" +
                        "because initial and final places are the same"
            }
            return false
        }

        // filtering Call trace entries that contain unexpected Remove actions
        if (node.entry is MethodTraceResolver.TraceEntry.CallRule
            && (node.entry.action is RemoveMark || node.entry.action is RemoveAllMarks)) {
            logger.warn {
                "Trace entry on line ${traits.lineNumber(node.statement)} because of unexpected Remove action!"
            }
            return false
        }

        // filtering return nodes that do not contain any new information
        if (node.entry == null && node.kind == TracePathNodeKind.RETURN) {
            return false
        }

        // filtering calls to toString methods
        if (node.kind != TracePathNodeKind.SOURCE && node.kind != TracePathNodeKind.SINK
            && node.entry is MethodTraceResolver.TraceEntry.CallTraceEntry) {
            val name = getMethodCalleeName(node)
            if (name == "toString")
                return false
        }

        // filtering nodes that became unimportant
        if (node.entry is MethodTraceResolver.TraceEntry.UnresolvedCallSkip) {
            return false
        }

        return true
    }

    private fun createEntryMessage(node: TracePathNode) =
        "Calling ${getMethodCalleeNameInPrint(node)}"

    private fun createExitMessage(node: TracePathNode) =
        "Exiting \"${node.statement.method.name}\""

    private fun createTraceEntryMessage(node: TracePathNode) =
        when (val entry = node.entry) {
            is MethodTraceResolver.TraceEntry.CallRule -> entry.createMessage(node)
            is MethodTraceResolver.TraceEntry.CallSummary -> entry.createMessage(node)
            is MethodTraceResolver.TraceEntry.CallSourceRule -> entry.createMessage(node)
            is MethodTraceResolver.TraceEntry.CallSourceSummary -> entry.createMessage(node)
            is MethodTraceResolver.TraceEntry.Sequential -> entry.createMessage(node)
            is MethodTraceResolver.TraceEntry.Final -> entry.createMessage(node)
            is MethodTraceResolver.TraceEntry.EntryPointSourceRule -> entry.createMessage(node)
            is MethodTraceResolver.TraceEntry.MethodEntry ->
                "Entering \"${entry.entryPoint.method.name}\" with $taintType data at ${entry.fact.base.inMessage(node)}"

            null -> when (node.kind) {
                TracePathNodeKind.RETURN -> createExitMessage(node)

                // calls that happen before reaching the taint source
                TracePathNodeKind.CALL -> createEntryMessage(node)

                else -> createDefaultMessage(node)
            }
            else -> createDefaultMessage(node)
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
                is MethodTraceResolver.TraceEntry.CallTraceEntry -> {
                    curList.add(trace)
                    if (entry is MethodTraceResolver.TraceEntry.SourceStartEntry)
                        kindTracker.setSourceCall()
                    else
                        kindTracker.setCall()
                }
                is MethodTraceResolver.TraceEntry.Sequential -> {
                    curList.add(trace)
                    kindTracker.setAssign()
                    addCurListAndClean()
                }
                else -> addAsSingle(trace)
            }
        }
        addCurListAndClean()
        return result
    }

    private fun getTaintOf(taint: String, of: TracePathNode): String {
        val ofName = getMethodCalleeNameInPrint(of)
        // StringBuilder.append is a string concatenation method often hidden by the "+"
        // its taint is always the first argument, not providing any meaningful information
        if (ofName == stringBuilderAppendName) return ofName
        return "$taint of $ofName"
    }

    private val markNameRegex = Regex("""(\$.*)_\d+""")

    private fun getMarkVarName(action: CommonTaintAction): String? {
        val name = when (action) {
            is AssignMark -> action.mark.name
            is CopyMark -> action.mark.name
            else -> return null
        }
        return markNameRegex.find(name)?.let { it.groups[1]?.value }
    }

    private fun getTaintType(node: TracePathNode): String? {
        val action = when (val entry = node.entry) {
            is MethodTraceResolver.TraceEntry.CallSourceRule -> entry.action
            is MethodTraceResolver.TraceEntry.EntryPointSourceRule -> entry.action
            is MethodTraceResolver.TraceEntry.CallRule -> entry.action
            else -> null
        }
        return action?.let { getMarkVarName(it) }
    }

    private fun updateTaintType(node: TracePathNode) {
        taintTypeNew?.let { taintType = it }
        taintTypeNew = getTaintType(node)
    }

    private fun getCallTaintIn(node: TracePathNode): String {
        val taintName = when(val entry = node.entry) {
            is MethodTraceResolver.TraceEntry.CallRule ->
                entry.action.getTainted(node)
            is MethodTraceResolver.TraceEntry.CallSummary ->
                entry.summaryTrace.initial.fact.base.inMessage(node)
            else -> "<unresolved call bad source>"
        }
        return getTaintOf(taintName, node)
    }

    private fun getCallTaintOut(node: TracePathNode): String {
        val taintName = when(val entry = node.entry) {
            is MethodTraceResolver.TraceEntry.CallRule ->
                entry.action.getPropagated(node)
            is MethodTraceResolver.TraceEntry.CallSummary ->
                entry.summaryTrace.final.fact.base.inMessage(node)
            else -> "<unresolved call bad sink>"
        }
        return getTaintOf(taintName, node)
    }

    private fun getAssignTaintOut(entry: MethodTraceResolver.TraceEntry?) = when (entry?.statement) {
        is CommonReturnInst -> "the returning value"
        null -> "<unresolved null assignee>"
        else -> entry.let {
            traits.getReadableAssignee(entry.statement)?.let { "\"$it\"" }
        } ?: "<unresolved assignee>"
    }

    private fun getSourceCallTaint(node: TracePathNode): String {
        val taintName = when (val entry = node.entry) {
            is MethodTraceResolver.TraceEntry.CallSourceRule ->
                entry.action.getPositionMessage(node)

            is MethodTraceResolver.TraceEntry.CallSourceSummary ->
                entry.summaryTrace.final.fact.base.inMessage(node)

            else -> "<unresolved call bad source>"
        }
        return getTaintOf(taintName, node)
    }

    fun createGroupTraceMessage(group: List<TracePathNode>): List<TracePathNodeWithMsg> =
        groupPrintableTraces(group).map { printableGroup ->
            if (printableGroup.kind == GroupTraceKind.SINGLE
                || printableGroup.nodes.size == 1) {
                val node = printableGroup.nodes.first()
                updateTaintType(node)
                TracePathNodeWithMsg(node, getSarifKind(node), createTraceEntryMessage(node))
            }
            else {
                val groupKind = getGroupKind(printableGroup.nodes)
                val lastNode = printableGroup.nodes.last()
                val firstNode = printableGroup.nodes.first()
                updateTaintType(lastNode)
                val message = when (printableGroup.kind) {
                    GroupTraceKind.CALL -> {
                        val taintIn = getCallTaintIn(firstNode)
                        val taintOut = getCallTaintOut(lastNode)
                        createCallChainTaintPropagationMessage(taintIn, taintOut)
                    }

                    GroupTraceKind.SOURCE_CALL -> {
                        val taintIn = getSourceCallTaint(firstNode)
                        val taintOut = getCallTaintOut(lastNode)
                        createSourceCallChainTaintMessage(taintIn, taintOut)
                    }

                    GroupTraceKind.CALL_ASSIGN -> {
                        val taintIn = getCallTaintIn(firstNode)
                        val taintOut = getAssignTaintOut(lastNode.entry)
                        createCallChainTaintAssignMessage(taintIn, taintOut)
                    }

                    GroupTraceKind.SOURCE_ASSIGN -> {
                        val taintIn = getSourceCallTaint(firstNode)
                        val taintOut = getAssignTaintOut(lastNode.entry)
                        createSourceCallChainTaintAssignMessage(taintIn, taintOut)
                    }

                    // GroupTraceKind.SINGLE is taken care of at the top of this function
                    else -> "<unresolved group trace kind>"
                }
                TracePathNodeWithMsg(lastNode, groupKind, message)
            }
        }

    private fun printThis(node: TracePathNode) =
        if (getMethodCalleeName(node) == "<init>") "the created object" else "the calling object"

    private fun printArgument(index: Int) =
        "the ${getOrdinal(index + 1)} argument"

    private fun printReturnedValue(node: TracePathNode): String {
        val assignee = traits.getReadableAssignee(node.statement)
        if (assignee == null || localNameResolver.isRegister(assignee)) return "the returned value"
        return "\"$assignee\""
    }

    private fun AccessPathBase.inMessage(node: TracePathNode) = when (this) {
        is AccessPathBase.This -> printThis(node)
        is AccessPathBase.Argument -> printArgument(idx)
        is AccessPathBase.ClassStatic -> "a static variable"
        is AccessPathBase.LocalVar -> {
            localNameResolver.getLocalName(node.statement.method, idx)?.let { "\"$it\"" } ?: "a local variable"
        }
        is AccessPathBase.Return -> printReturnedValue(node)
        is AccessPathBase.Constant -> "a const value"
        else -> "<unresolved value>"
    }

    private fun CommonTaintAssignAction.getPositionMessage(node: TracePathNode): String = when (this) {
        is AssignMark -> position.inMessage(node)
        else -> "<unresolved assign position>"
    }

    private fun Position.inMessage(node: TracePathNode): String = when (this) {
        is This -> printThis(node)
        is Argument -> printArgument(index)
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

    private fun addTaintTypeChangeAs(msg: String): String {
        return taintTypeNew?.let { "$msg as $taintTypeNew" } ?: msg
    }

    private fun createMethodCallTaintPropagationMessage(
        node: TracePathNode,
        from: String,
        to: String,
    ): String {
        val calleeName = getMethodCalleeNameInPrint(node)
        if (calleeName == stringBuilderAppendName)
            return addTaintTypeChangeAs("Concatenated String contains $taintType data")
        return addTaintTypeChangeAs("Call to $calleeName propagates $taintType data from $from to $to")
    }

    private fun createCallChainTaintPropagationMessage(
        from: String,
        to: String,
    ): String {
        return addTaintTypeChangeAs("Call chain propagates $taintType data from $from to $to")
    }

    private fun createCallChainTaintAssignMessage(
        from: String,
        to: String,
    ): String {
        return addTaintTypeChangeAs("Call chain propagates $taintType data from $from and assigns it to $to")
    }

    private fun createSourceCallChainTaintMessage(
        from: String,
        to: String,
    ): String {
        return addTaintTypeChangeAs("Call chain gets $taintType data from $from and propagates it to $to")
    }

    private fun createSourceCallChainTaintAssignMessage(
        from: String,
        to: String,
    ): String {
        return addTaintTypeChangeAs("Call chain gets $taintType data from $from and assigns it to $to")
    }

    private fun createMethodCallTaintCreationMessage(
        node: TracePathNode,
        pos: String,
    ): String {
        var calleeName = getMethodCalleeNameInPrint(node)
        if (calleeName == stringBuilderAppendName)
            // it's unlikely this method will once become a source of bad/leaked data...but who knows?
            calleeName = "StringBuilder.append"
        if (calleeName.endsWith(initializerSuffix))
            return addTaintTypeChangeAs("$calleeName creates an object with $taintType data")
        return addTaintTypeChangeAs("Call to $calleeName puts $taintType data to $pos")
    }

    private fun MethodTraceResolver.TraceEntry.Final.createMessage(node: TracePathNode): String {
        if (node.kind != TracePathNodeKind.SINK) {
            if (node.statement is CommonReturnInst)
                return createExitMessage(node)
            val callExpr = traits.getCallExpr(node.statement)
            val tainted = fact.base.inMessage(node)

            if (callExpr != null)
                return "Calling ${getMethodCalleeNameInPrint(node)} with $taintType data coming from $tainted"
            return "<unknown final>"
        }
        return createDefaultMessage(node)
    }

    private fun MethodTraceResolver.TraceEntry.EntryPointSourceRule.createMessage(node: TracePathNode): String {
        val tainted = fact.base.inMessage(node)
        return "Potential $taintType data at $tainted of the method"
    }

    private fun MethodTraceResolver.TraceEntry.CallRule.createMessage(node: TracePathNode): String {
        if (action is CopyMark || action is CopyAllMarks) {
            val taintSource = action.getTainted(node)
            val taintFollow = action.getPropagated(node)
            return createMethodCallTaintPropagationMessage(node, taintSource, taintFollow)
        }
        if (action is AssignMark) {
            val taintedPos = (action as AssignMark).position.inMessage(node)
            return createMethodCallTaintCreationMessage(node, taintedPos)
        }
        return "<!CallRule>"
    }

    private fun MethodTraceResolver.TraceEntry.CallSummary.createMessage(node: TracePathNode): String {
        val taintSource = summaryTrace.initial.fact.base.inMessage(node)
        val taintFollow = summaryTrace.final.fact.base.inMessage(node)
        return createMethodCallTaintPropagationMessage(node, taintSource, taintFollow)
    }

    private fun MethodTraceResolver.TraceEntry.CallSourceRule.createMessage(node: TracePathNode): String {
        val taintedPos = action.getPositionMessage(node)
        return createMethodCallTaintCreationMessage(node, taintedPos)
    }

    private fun MethodTraceResolver.TraceEntry.CallSourceSummary.createMessage(node: TracePathNode): String {
        val taintedPos = fact.base.inMessage(node)
        return createMethodCallTaintCreationMessage(node, taintedPos)
    }

    private fun MethodTraceResolver.TraceEntry.Sequential.createMessage(node: TracePathNode): String {
        val assignee = getAssignTaintOut(node.entry)
        return addTaintTypeChangeAs("$assignee is assigned a value with $taintType data")
    }

    private fun generateMessageForReturn(node: TracePathNode): String {
        if (node.kind != TracePathNodeKind.RETURN) return "<!Return>"
        return "Returning from ${getMethodCalleeNameInPrint(node)}"
    }

    companion object {
        val logger = object : KLogging() {}.logger
    }
}
