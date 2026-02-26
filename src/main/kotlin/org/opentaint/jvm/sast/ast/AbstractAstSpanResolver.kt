package org.opentaint.jvm.sast.ast

import mu.KLogging
import org.antlr.v4.runtime.ParserRuleContext
import org.antlr.v4.runtime.Token
import org.antlr.v4.runtime.tree.AbstractParseTreeVisitor
import org.antlr.v4.runtime.tree.ParseTree
import org.antlr.v4.runtime.tree.RuleNode
import org.opentaint.dataflow.ap.ifds.trace.MethodTraceResolver
import org.opentaint.jvm.sast.sarif.JIRSarifTraits
import org.opentaint.ir.api.jvm.cfg.JIRArrayAccess
import org.opentaint.ir.api.jvm.cfg.JIRAssignInst
import org.opentaint.ir.api.jvm.cfg.JIRCallExpr
import org.opentaint.ir.api.jvm.cfg.JIRCallInst
import org.opentaint.ir.api.jvm.cfg.JIRFieldRef
import org.opentaint.ir.api.jvm.cfg.JIRInst
import org.opentaint.ir.api.jvm.cfg.JIRInstanceCallExpr
import org.opentaint.ir.api.jvm.cfg.JIRNewExpr
import org.opentaint.ir.api.jvm.cfg.JIRReturnInst
import org.opentaint.ir.api.jvm.cfg.JIRValue
import org.opentaint.jvm.sast.sarif.IntermediateLocation
import org.opentaint.jvm.sast.sarif.LocationSpan
import org.opentaint.jvm.sast.sarif.LocationType
import org.opentaint.jvm.sast.sarif.TracePathNodeKind
import org.opentaint.jvm.sast.sarif.isPureEntryPoint

abstract class AbstractAstSpanResolver(protected val traits: JIRSarifTraits) : AstSpanResolver {

    protected val logger = object : KLogging() {}.logger

    protected enum class InstructionKind {
        METHOD_CALL,
        OBJECT_CREATION,
        FIELD_ACCESS,
        ARRAY_ACCESS,
        RETURN,
        ASSIGNMENT,
        UNKNOWN
    }

    protected fun inferKind(inst: JIRInst): InstructionKind = when (inst) {
        is JIRReturnInst -> InstructionKind.RETURN
        is JIRCallInst -> if (isConstructorCall(inst.callExpr)) {
            InstructionKind.OBJECT_CREATION
        } else {
            InstructionKind.METHOD_CALL
        }
        is JIRAssignInst -> inferAssignKind(inst)
        else -> InstructionKind.UNKNOWN
    }

    protected fun isConstructorCall(call: JIRCallExpr): Boolean =
        call.method.method.isConstructor

    protected fun inferAssignKind(assign: JIRAssignInst): InstructionKind {
        val l = assign.lhv
        val r = assign.rhv
        if (r is JIRNewExpr) return InstructionKind.OBJECT_CREATION
        if (l is JIRArrayAccess || r is JIRArrayAccess) return InstructionKind.ARRAY_ACCESS
        if (l is JIRFieldRef || r is JIRFieldRef) return InstructionKind.FIELD_ACCESS
        if (r is JIRCallExpr && isConstructorCall(r)) return InstructionKind.OBJECT_CREATION
        if (r is JIRCallExpr) return InstructionKind.METHOD_CALL
        return InstructionKind.ASSIGNMENT
    }

    protected fun IntermediateLocation.isMethodEntry(): Boolean {
        val entry = this.node?.entry
        return entry is MethodTraceResolver.TraceEntry.MethodEntry || entry.isPureEntryPoint() || type == LocationType.RuleMethodEntry
    }

    protected fun IntermediateLocation.isMethodExit(): Boolean {
        if (node == null) return false
        return node.kind != TracePathNodeKind.SINK && node.entry is MethodTraceResolver.TraceEntry.Final
                && node.statement is JIRReturnInst
    }

    protected fun createLocationSpan(start: Token?, stop: Token?): LocationSpan? {
        if (start == null || stop == null) return null

        val (startLine, startCol) = start.line to (start.charPositionInLine + 1)
        val endLine = stop.line
        val endCol = tokenEndColumn(stop)

        return LocationSpan(
            startLine = startLine,
            startColumn = startCol,
            endLine = endLine,
            endColumn = endCol,
        )
    }

    protected fun tokenEndColumn(t: Token): Int {
        val textLen = t.text?.length
        if (textLen != null) return t.charPositionInLine + textLen
        val length = if (t.stopIndex >= t.startIndex) (t.stopIndex - t.startIndex + 1) else 1
        return t.charPositionInLine + length
    }

    protected fun ParserRuleContext?.findChildType(type: Class<out ParserRuleContext>): ParserRuleContext? {
        if (this == null || children == null) return null
        return children.find { type.isInstance(it) } as ParserRuleContext?
    }

    protected fun spanLen(ctx: ParserRuleContext): Int {
        val stop = ctx.stop ?: return Int.MAX_VALUE
        return stop.tokenIndex - ctx.start.tokenIndex
    }

    @SafeVarargs
    protected fun findSmallestOfTypes(
        root: ParseTree,
        line: Int,
        vararg types: Class<out ParserRuleContext>
    ): ParserRuleContext? {
        val set = types.toSet()
        val candidates = collectContexts(root, line) { ctx -> set.any { it.isInstance(ctx) } }
        return candidates.minByOrNull { spanLen(it) }
    }

    protected inline fun collectContexts(
        root: ParseTree,
        line: Int,
        crossinline predicate: (ParserRuleContext) -> Boolean
    ): List<ParserRuleContext> {
        val res = ArrayList<ParserRuleContext>()
        val stack = ArrayDeque<ParseTree>()
        stack.add(root)
        while (stack.isNotEmpty()) {
            val n = stack.removeFirst()
            if (n is ParserRuleContext) {
                if (!coversLine(n, line)) continue

                if (predicate(n)) {
                    res.add(n)
                }
            }
            val cnt = n.childCount
            for (i in 0 until cnt) stack.addLast(n.getChild(i))
        }
        return res
    }

    protected fun JIRInst.getArrayValue(): JIRValue? {
        if (this !is JIRAssignInst) return null
        if (lhv is JIRArrayAccess) return (lhv as JIRArrayAccess).array
        if (rhv is JIRArrayAccess) return (rhv as JIRArrayAccess).array
        return null
    }

    protected fun JIRInst.getRawValue(value: JIRValue) =
        traits.getReadableValue(this, value)?.replace("\"", "")

    protected fun JIRInst?.getArrayName(): String? {
        val value = this?.getArrayValue() ?: return null
        return getRawValue(value)
    }

    protected fun JIRInst?.getFieldName(): String? {
        if (this !is JIRAssignInst) return null
        if (lhv is JIRFieldRef) return (lhv as JIRFieldRef).field.name
        if (rhv is JIRFieldRef) return (rhv as JIRFieldRef).field.name
        return null
    }

    protected fun JIRInst?.getAssignee(): String? {
        // fix for initializer calls that are assignments in source
        if (this is JIRCallInst && callExpr.method.method.isConstructor && callExpr is JIRInstanceCallExpr) {
            return getRawValue((callExpr as JIRInstanceCallExpr).instance)
        }
        if (this !is JIRAssignInst) return null
        return getRawValue(lhv)
    }

    protected fun findSmallestSpan(root: ParseTree, line: Int): LocationSpan? {
        val lineContexts = collectContexts(root, line) { true }
        val smallest = lineContexts.minByOrNull { spanLen(it) } ?: return null
        return createLocationSpan(smallest.start, smallest.stop)
    }

    protected fun findBroadestSpan(root: ParseTree, line: Int): LocationSpan? {
        val lineContexts = collectContexts(root, line) { exactLine(it, line) }
        val broadest = lineContexts.maxByOrNull { spanLen(it) } ?: return null
        return createLocationSpan(broadest.start, broadest.stop)
    }

    companion object {
        fun exactLine(ctx: ParserRuleContext?, line: Int): Boolean {
            val stop = ctx?.stop ?: return false
            return ctx.start.line == line && line == stop.line
        }

        fun coversLine(ctx: ParserRuleContext, line: Int): Boolean {
            val stop = ctx.stop ?: return false
            return ctx.start.line <= line && line <= stop.line
        }

        fun <T> AbstractParseTreeVisitor<T>.visitChildrenWithLine(node: RuleNode, line: Int) {
            val n = node.childCount
            for (i in 0..<n) {
                val c = node.getChild(i)

                if (c is ParserRuleContext) {
                    if (!coversLine(c, line)) continue
                }

                c.accept(this)
            }
            return
        }
    }
}
