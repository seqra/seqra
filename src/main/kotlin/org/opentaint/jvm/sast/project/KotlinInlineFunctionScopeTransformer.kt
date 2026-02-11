package org.opentaint.jvm.sast.project

import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes
import org.opentaint.dataflow.jvm.util.typeName
import org.opentaint.ir.api.jvm.JIRInstExtFeature
import org.opentaint.ir.api.jvm.JIRMethod
import org.opentaint.ir.api.jvm.PredefinedPrimitives
import org.opentaint.ir.api.jvm.cfg.JIRAssignInst
import org.opentaint.ir.api.jvm.cfg.JIRInst
import org.opentaint.ir.api.jvm.cfg.JIRInstList
import org.opentaint.ir.api.jvm.cfg.JIRLocalVar
import org.opentaint.ir.api.jvm.cfg.JIRRawAssignInst
import org.opentaint.ir.api.jvm.cfg.JIRRawCatchInst
import org.opentaint.ir.api.jvm.cfg.JIRRawInst
import org.opentaint.ir.api.jvm.cfg.JIRRawLabelInst
import org.opentaint.ir.api.jvm.cfg.JIRRawLineNumberInst
import org.opentaint.ir.api.jvm.cfg.JIRRawLocalVar
import org.opentaint.ir.impl.cfg.JIRInstListImpl
import org.opentaint.ir.impl.cfg.JIRRawInt

object KotlinInlineFunctionScopeTransformer : JIRInstExtFeature {
    private class BaseMethodVisitor : MethodVisitor(Opcodes.ASM9)

    private val mVisitor = BaseMethodVisitor()

    private data class InlineScope(
        val startLabelIdx: Int,
        val endLabelIdx: Int,
        val scopeId: Int,
        val scopeName: String,
    )

    override fun transformRawInstList(method: JIRMethod, list: JIRInstList<JIRRawInst>): JIRInstList<JIRRawInst> {
        val scopes = findInlinedFunctionsScopes(method)
        if (scopes.isEmpty()) return list

        val scopeStart = scopes.associateBy { it.startLabelIdx }
        val scopeEnd = scopes.associateBy { it.endLabelIdx }

        var nextLocalVarIdx = maxLocalVarIdx(list) + 1

        val resultInstList = mutableListOf<JIRRawInst>()
        var instIdx = 0
        while (instIdx < list.size) {
            val inst = list[instIdx]
            resultInstList.add(inst)
            instIdx++

            if (inst !is JIRRawLabelInst || !inst.isOriginal()) {
                continue
            }

            val afterLabel = list[instIdx]
            if (afterLabel is JIRRawLineNumberInst) {
                resultInstList.add(afterLabel)
                instIdx++
            }

            val originalLabelIndex = inst.getOriginalLabelIndex()!!

            val end = scopeEnd[originalLabelIndex]
            if (end != null) {
                val name = mkVarName(SCOPE_END_PREFIX, end.scopeId, end.scopeName)
                val value = JIRRawLocalVar(nextLocalVarIdx++, name, PredefinedPrimitives.Int.typeName())
                resultInstList.add(JIRRawAssignInst(method, value, JIRRawInt(0)))
            }

            val start = scopeStart[originalLabelIndex]
            if (start != null) {
                val name = mkVarName(SCOPE_START_PREFIX, start.scopeId, start.scopeName)
                val value = JIRRawLocalVar(nextLocalVarIdx++, name, PredefinedPrimitives.Int.typeName())
                resultInstList.add(JIRRawAssignInst(method, value, JIRRawInt(0)))
            }
        }

        return JIRInstListImpl(resultInstList)
    }

    private fun findInlinedFunctionsScopes(method: JIRMethod): List<InlineScope> {
        val scopes = method.withAsmNode { md ->
            val insts = md.instructions
            insts.accept(mVisitor)
            // filtering local variables responsible for inlined method's ranges
            md.localVariables
                .filter { isInlineOrLambda(it.name) }
                .mapIndexed { idx, it ->
                    InlineScope(insts.indexOf(it.start), insts.indexOf(it.end), idx, it.name)
                }

        }
        return scopes
    }

    private fun maxLocalVarIdx(instList: JIRInstList<JIRRawInst>): Int {
        var result = -1
        for (inst in instList) {
            val value = when (inst) {
                is JIRRawAssignInst -> inst.lhv
                is JIRRawCatchInst -> inst.throwable
                else -> continue
            }

            if (value !is JIRRawLocalVar) continue

            result = maxOf(result, value.index)
        }
        return result
    }

    enum class ScopeManageType {
        START, END
    }

    data class ScopeDescriptor(val index: Int, val name: String)

    data class ScopeManageEvent(val type: ScopeManageType, val scope: ScopeDescriptor)

    fun findInlineFunctionScopeManageInst(inst: JIRInst): ScopeManageEvent? {
        if (inst !is JIRAssignInst) return null
        val lhv = inst.lhv as? JIRLocalVar ?: return null
        val name = lhv.name
        if (name.startsWith(SCOPE_START_PREFIX)) {
            return ScopeManageEvent(ScopeManageType.START, parseScopeDescriptor(name))
        }

        if (name.startsWith(SCOPE_END_PREFIX)) {
            return ScopeManageEvent(ScopeManageType.END, parseScopeDescriptor(name))
        }

        return null
    }

    fun isInlineFunctionScopeEvent(rawInst: JIRRawInst, event: ScopeManageEvent): Boolean {
        if (rawInst !is JIRRawAssignInst) return false
        val lhv = rawInst.lhv as? JIRRawLocalVar ?: return false
        val name = lhv.name
        val scopeDescriptorStr = when (event.type) {
            ScopeManageType.START -> name.takeIf { it.startsWith(SCOPE_START_PREFIX) }
            ScopeManageType.END -> name.takeIf { it.startsWith(SCOPE_END_PREFIX) }
        } ?: return false
        return parseScopeDescriptor(scopeDescriptorStr) == event.scope
    }

    private fun parseScopeDescriptor(name: String): ScopeDescriptor {
        val firstDelim = name.indexOf(':')
        val secondDelim = name.indexOf(':', firstDelim + 1)

        val scopeId = name.substring(firstDelim + 1, secondDelim).toInt()
        val scopeName = name.substring(secondDelim + 1)
        return ScopeDescriptor(scopeId, scopeName)
    }

    fun inlinedMethodName(descriptor: ScopeDescriptor): String {
        val name = descriptor.name
        if (name.startsWith(INLINE_LOCAL_PREFIX)) {
            return "method \"${name.drop(INLINE_LOCAL_PREFIX.length)}\""
        }
        return LAMBDA_MARKER
    }

    private fun mkVarName(prefix: String, scopeId: Int, scopeName: String): String =
        "$prefix:$scopeId:$scopeName"

    private fun isInlineOrLambda(name: String) =
        name.startsWith(INLINE_LOCAL_PREFIX) || name.startsWith(LAMBDA_LOCAL_PREFIX)

    private const val INLINE_LOCAL_PREFIX = "\$i\$f\$"
    private const val LAMBDA_LOCAL_PREFIX = "\$i\$a\$"
    const val LAMBDA_MARKER = "lambda"

    private const val SCOPE_START_PREFIX = "inline_scope_start"
    private const val SCOPE_END_PREFIX = "inline_scope_end"
}
