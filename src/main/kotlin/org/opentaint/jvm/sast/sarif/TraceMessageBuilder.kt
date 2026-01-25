package org.opentaint.jvm.sast.sarif

import mu.KLogging
import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.ap.ifds.access.InitialFactAp
import org.opentaint.dataflow.ap.ifds.trace.MethodTraceResolver.TraceEdge
import org.opentaint.dataflow.ap.ifds.trace.MethodTraceResolver.TraceEntry
import org.opentaint.dataflow.ap.ifds.trace.MethodTraceResolver.TraceEntryAction
import org.opentaint.dataflow.configuration.jvm.RemoveAllMarks
import org.opentaint.dataflow.configuration.jvm.RemoveMark
import org.opentaint.dataflow.jvm.ap.ifds.LambdaAnonymousClassFeature.JIRLambdaMethod
import org.opentaint.dataflow.util.SarifTraits
import org.opentaint.ir.api.common.CommonMethod
import org.opentaint.ir.api.common.cfg.CommonAssignInst
import org.opentaint.ir.api.common.cfg.CommonCallExpr
import org.opentaint.ir.api.common.cfg.CommonInst
import org.opentaint.ir.api.common.cfg.CommonReturnInst
import org.opentaint.ir.api.jvm.JIRMethod
import org.opentaint.ir.api.jvm.cfg.JIRAssignInst
import org.opentaint.ir.api.jvm.cfg.JIRCallInst
import org.opentaint.ir.api.jvm.cfg.JIRThis
import org.opentaint.ir.api.jvm.cfg.JIRThrowInst
import org.opentaint.jvm.sast.project.spring.SpringGeneratedMethod
import org.opentaint.org.opentaint.semgrep.pattern.Mark
import org.opentaint.org.opentaint.semgrep.pattern.Mark.Companion.getMark

data class TracePathNodeWithMsg(
    val node: TracePathNode,
    val kind: String,
    val message: String,
)

class TraceMessageBuilder(
    private val traits: SarifTraits<CommonMethod, CommonInst>,
    private val sinkMessage: String,
    private val ruleId: String,
    fullPath: List<TracePathNode>,
) {
    private val memoizedMethods = hashSetOf<CommonMethod>()
    private val lambdaCapturedVars = hashMapOf<String, List<String>>()
    private val lambdaToArtificialClass = hashMapOf<CommonMethod, String>()

    private fun memoizeLambdaCaptures(method: JIRMethod) {
        for (inst in method.instList) {
            if (inst.isLambdaCreation()) {
                val call = inst as? JIRCallInst
                if (call == null) {
                    logger.error { "Lambda created on non-call instruction! $inst" }
                    return
                }

                val expr = call.callExpr
                // skip `this` as the first argument as it is not used in lambda's call
                val captureStart = if (expr.args.isNotEmpty() && expr.args[0] is JIRThis) 1 else 0
                val lambdaCapture = expr.args.drop(captureStart).map { param ->
                    traits.getReadableValue(call, param) ?: badOutput("unresolved lambda capture")
                }
                val artificialClassName = expr.method.enclosingType.typeName
                if (lambdaCapturedVars.containsKey(artificialClassName)) {
                    if (lambdaCapturedVars[artificialClassName] != lambdaCapture) {
                        logger.error { "Lambda is re-created with different values captured! $artificialClassName" }
                    }
                }
                lambdaCapturedVars[artificialClassName] = lambdaCapture
            }
        }
    }

    init {
        for ((idx, node) in fullPath.withIndex()) {
            val method = node.getMethod()
            if (method !is JIRMethod) {
                logger.error { "Unexpected CommonMethod! Only JIRMethod's are supported!" }
                continue
            }
            if (memoizedMethods.add(method)) {
                memoizeLambdaCaptures(method)
            }
            if (node.isLambdaEntry()) {
                val artificialMethod = fullPath.getOrNull(idx - 1)?.getMethod() as? JIRMethod
                val artificialClassName = artificialMethod?.enclosingClass?.name
                val actualMethod = node.getMethod()
                if (artificialClassName == null || !artificialClassName.contains(artificialLambdaClassMark)) {
                    logger.error { "Lambda entered, but did not find call from artificial method!" }
                    continue
                }
                if (!lambdaCapturedVars.containsKey(artificialClassName)) {
                    logger.error { "lambda entered, but could not find its captured variables!" }
                    continue
                }
                if (lambdaToArtificialClass.containsKey(actualMethod)) {
                    if (lambdaToArtificialClass[actualMethod] != artificialClassName) {
                        logger.error { "Artificial lambda class changed upon next lambda call for \"${actualMethod.name}\"!" }
                    }
                }
                lambdaToArtificialClass[actualMethod] = artificialClassName
            }
        }
    }

    private fun TracePathNode.getMethod() =
        this.statement.location.method

    private fun CommonInst.isLambdaCreation() =
        this is JIRCallInst && this.callExpr.method.method is JIRLambdaMethod

    private fun TracePathNode.isLambdaCreation() =
        this.statement.isLambdaCreation()

    private fun TracePathNode.isLambdaEntry() =
        this.entry is TraceEntry.MethodEntry && this.entry.entryPoint.method.name.startsWith(lambdaMark)

    fun TracePathNode.isInsideLambda() =
        lambdaToArtificialClass.containsKey(this.getMethod())

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
        if (method.startsWith(lambdaMark))
            return "lambda"
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

        if (isGeneratedLocation(node.statement)) {
            return false
        }

        // filtering calls to toString methods
        if (node.kind != TracePathNodeKind.SOURCE && node.kind != TracePathNodeKind.SINK) {
            val name = getMethodCalleeName(node)
            if (name == "toString")
                return false
        }

        // filtering instructions inserted for lambda invocations
        if (node.getMethod() is JIRLambdaMethod)
            return false

        val entry = node.entry as? TraceEntry.Action ?: return true

        val primaryAction = entry.primaryAction

        // filtering nodes that became unimportant
        if (primaryAction is TraceEntryAction.UnresolvedCallSkip) {
            return false
        }

        // filtering CallSummary traces where tainted data ends up where it started
        if (primaryAction is TraceEntryAction.CallSummary && entry.otherActions.isEmpty()) {
            val summaryTraceFacts = primaryAction.summaryTrace.final.edges
            if (summaryTraceFacts.all { it is TraceEdge.MethodTraceEdge && it.initialFact.base == it.fact.base }) {
                logger.debug {
                    "Skipping trace entry on line ${traits.lineNumber(node.statement)} " +
                            "because initial and final places are the same"
                }
                return false
            }
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

        return true
    }

    private fun isGeneratedLocation(stmt: CommonInst): Boolean {
        val locationMethod = stmt.location.method
        if (locationMethod is SpringGeneratedMethod) return true
        return false
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
        val method = node.getMethod()
        val name = method.name
        val className = traits.getMethodClassName(method)
        return "Exiting ${getMethodCalleeNameInPrint(name, className)}"
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
        val mark = fact.getMark(ruleId)
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

        val starts = this.collectStarts()
        val follows = this.collectFollows() - starts.toSet()

        return EdgesInfo(starts, follows)
    }

    private fun getActionEdges(action: TraceEntryAction?) =
        when (action) {
            is TraceEntryAction.PassAction -> mapToTaintInfos(action.edgesAfter)

            is TraceEntryAction.SourceAction -> mapToTaintInfos(action.sourceEdges)

            else -> emptyList()
        }

    private fun TraceEntry?.collectStarts() =
        if (this != null && this !is TraceEntry.SourceStartEntry) {
            mapToTaintInfos(relevantEdges()).distinct()
        } else emptyList()

    private fun TraceEntry?.collectFollows() =
        when (this) {
            is TraceEntry.Action -> otherActions.flatMap { getActionEdges(it) } + getActionEdges(primaryAction)

            is TraceEntry.SourceStartEntry ->
                sourceOtherActions.flatMap { getActionEdges(it) } + getActionEdges(sourcePrimaryAction)

            else -> emptyList()
        }.distinct()

    private fun createTraceEntryMessage(node: TracePathNode): String {
        if (node.isLambdaCreation())
            return createLambdaCreationMessage(node)
        return when (val entry = node.entry) {
            is TraceEntry.Final -> entry.createMessage(node)

            is TraceEntry.MethodEntry -> {
                val methodName = getMethodCalleeNameInPrint(entry.entryPoint.method.name, className = "")
                val taints = printTaints(node, entry.collectStarts())
                val withTaints = if (taints.isEmpty()) "" else " with $taints"
                "Entering $methodName$withTaints"
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
        var skipLambdaAssign = false

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
            if (skipLambdaAssign) {
                skipLambdaAssign = false
                if (trace.statement is JIRAssignInst)
                    continue
            }
            if (trace.kind == TracePathNodeKind.CALL) {
                addAsSingle(trace)
                continue
            }
            if (trace.isLambdaCreation()) {
                skipLambdaAssign = true
                addAsSingle(trace)
                continue
            }
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

    fun createGroupTraceMessages(locs: List<List<TracePathNode>>) =
        locs.flatMap { group ->
            if (group.isEmpty())
                return@flatMap listOf<TracePathNodeWithMsg>()

            createGroupTraceMessage(group)
        }

    private fun createGroupTraceMessage(group: List<TracePathNode>): List<TracePathNodeWithMsg> =
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
                val follows = lastNode.entry.collectFollows() - starts.toSet()
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

    private fun printLambdaArgument(node: TracePathNode, index: Int): String {
        if (!node.isInsideLambda()) {
            logger.error { "Called outside of lambda!" }
            return badOutput("No lambdas present")
        }
        val captured = lambdaCapturedVars[lambdaToArtificialClass[node.getMethod()]]
        if (captured == null) {
            logger.error { "No captured variables present while being inside lambda! \"${node.getMethod().name}\"" }
            return badOutput("Unresolved lambda arg")
        }
        if (index < captured.size)
            return captured[index]
        return traits.printArgument(node.getMethod(), index - captured.size)
    }

    private fun printArgument(node: TracePathNode, index: Int): String =
        if (node.kind == TracePathNodeKind.CALL && node.entry !is TraceEntry.Final) {
            val stmt = node.statement as? CommonAssignInst
            val call = stmt?.let { it.rhv as? CommonCallExpr }
            val default = traits.printArgumentNth(index, call?.let { traits.getCallee(it).name })
            val argument = call?.let {
                it.args.getOrNull(index)?.let { arg -> traits.getReadableValue(node.statement, arg) }
            }
            argument ?: default
        }
        else if (node.isInsideLambda()) {
            printLambdaArgument(node, index)
        }
        else {
            traits.printArgument(node.getMethod(), index)
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

    data class TaintPropagationInfo(val taint: String, val from: String?, val to: String?)

    private fun getCallAction(node: TracePathNode, method: String): String {
        if (node.kind == TracePathNodeKind.OTHER) {
            if (method.endsWith(initializerSuffix))
                return method
            return "Method $method"
        }
        return "Call to $method"
    }

    private fun createMethodCallTaintPropagationMessage(
        node: TracePathNode,
        taints: List<TaintPropagationInfo>
    ): String {
        val calleeName = getMethodCalleeNameInPrint(node)
        val callAction = getCallAction(node, calleeName)
        if (calleeName == stringBuilderAppendName) {
            val taint = taints.joinToString(", ") { it.taint }
            return "Concatenated String contains $taint data"
        }
        val propagated = mutableListOf<TaintPropagationInfo>()
        val created = mutableListOf<TaintPropagationInfo>()
        val source = mutableListOf<TaintPropagationInfo>()
        for (taint in taints) {
            if (taint.to == null)
                source.add(taint)
            else if (taint.from == null)
                created.add(taint)
            else
                propagated.add(taint)
        }
        val propagatedJoin = propagated.joinToString("; ") { "${it.taint} data from ${it.from} to ${it.to}" }
        val createdJoin = created.joinToString("; ") { "${it.taint} data to ${it.to}" }
        val sourceJoin = source.joinToString("; ") { "${it.taint} at ${it.from}" }
        val joiner1 =
            if (sourceJoin.isNotEmpty() and (propagatedJoin.isNotEmpty() or createdJoin.isNotEmpty()))
                ", then"
            else
                ""
        val joiner2 = if (propagatedJoin.isNotEmpty() and createdJoin.isNotEmpty()) " and" else ""
        val propagatedText = if (propagatedJoin.isNotEmpty()) " propagates $propagatedJoin" else ""
        val createdText = if (createdJoin.isNotEmpty()) " puts $createdJoin" else ""
        val sourceText = if (source.isNotEmpty()) " starts with $sourceJoin" else ""
        val taintChanges = "$sourceText$joiner1$propagatedText$joiner2$createdText"
        if (taintChanges.isEmpty() && node.kind == TracePathNodeKind.SOURCE) {
            return "$callAction creates a mark"
        }
        return "$callAction$taintChanges"
    }

    private fun isOneMark(infos: EdgesInfo): Boolean {
        val all = infos.starts + infos.follows
        if (all.isEmpty())
            return true
        val mark = all[0].mark
        return all.all { it.mark == mark }
    }

    private fun createLambdaCreationMessage(node: TracePathNode): String {
        val starts = node.entry.collectStarts()
        val suffix = if (starts.isEmpty()) "" else " with captured ${printTaints(node, starts)}"
        return "Lambda created$suffix"
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
            return getCallAction(node, calleeName)
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

    private fun TracePathNode.collectTaintPropagationInfo(): List<TaintPropagationInfo> {
        val dataflow = this.entry.collectDataflow()
        val markFollows = hashMapOf<Mark, HashSet<String>>()
        val markStarts = hashMapOf<Mark, HashSet<String>>()
        dataflow.follows.filter { it.mark !is Mark.StateMark }.forEach {
            markFollows.getOrPut(it.mark, ::hashSetOf).add(it.pos.inMessage(this))
        }
        dataflow.starts.filter { it.mark !is Mark.StateMark }.forEach {
            markStarts.getOrPut(it.mark, ::hashSetOf).add(it.pos.inMessage(this))
        }
        val propagations = mutableListOf<TaintPropagationInfo>()
        markFollows.forEach { (mark, positions) ->
            val starts = markStarts.getOrDefault(mark, hashSetOf()).joinToString(", ")
            val follows = positions.joinToString(", ")
            propagations.add(TaintPropagationInfo(mark.inMessage(), starts.ifEmpty { null }, follows))
        }
        markStarts.filter { (mark, _) -> !markFollows.containsKey(mark) }.forEach { (mark, positions) ->
            propagations.add(TaintPropagationInfo(mark.inMessage(), positions.joinToString(", "), null))
        }
        return propagations
    }

    private fun TraceEntryAction.EntryPointSourceRule.createMessage(node: TracePathNode): String {
        val taints = node.collectTaintPropagationInfo().filter { it.to != null }
            .joinToString("; ") { "${it.to} as ${it.taint}" }
        if (taints.isEmpty()) {
            return "Marked data at method entry"
        }
        return "Method entry marks $taints"
    }

    private fun TraceEntryAction.SequentialSourceRule.createMessage(node: TracePathNode): String {
        val taints = node.collectTaintPropagationInfo().filter { it.to != null }.map { "${it.taint} data at ${it.to}" }
        if (taints.isEmpty()) {
            return "Value with marked data"
        }
        val taintsJoin = taints.joinToString("; ")
        return "Value with $taintsJoin"
    }

    private fun TraceEntryAction.CallRule.createMessage(node: TracePathNode): String {
        val taintInfos = node.collectTaintPropagationInfo()
        return createMethodCallTaintPropagationMessage(node, taintInfos)
    }

    private fun TraceEntryAction.CallSummary.createMessage(node: TracePathNode): String {
        if (node.kind == TracePathNodeKind.CALL) {
            val calleeName = getMethodCalleeNameInPrint(node)
            // this is a call that updates marks;
            // we will need its follows on its return, which is marked as TracePathNodeKind.OTHER
            val starts = node.entry.collectStarts()
            return createPropagationMessageFromTaints("Calling $calleeName", node, EdgesInfo(starts, emptyList()))
        }
        return createMethodCallTaintPropagationMessageWithTaints(node)
    }

    private fun TraceEntryAction.CallSourceRule.createMessage(node: TracePathNode): String {
        val taintInfos = node.collectTaintPropagationInfo()
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
        private val stringBuilderAppendName = "String concatenation"
        private val initializerSuffix = "initializer"
        private val classInitializerSuffix = "class initializer"
        private val defaultTaintMark = "marked"
        private val lambdaMark = "lambda$"
        private val artificialLambdaClassMark = "jIR_lambda$"

        val logger = object : KLogging() {}.logger
    }
}
