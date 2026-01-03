package org.opentaint.dataflow.jvm.util

import org.opentaint.dataflow.util.SarifTraits
import org.opentaint.ir.api.common.cfg.CommonAssignInst
import org.opentaint.ir.api.common.cfg.CommonCallExpr
import org.opentaint.ir.api.common.cfg.CommonExpr
import org.opentaint.ir.api.jvm.JIRClasspath
import org.opentaint.ir.api.jvm.JIRMethod
import org.opentaint.ir.api.jvm.cfg.JIRAssignInst
import org.opentaint.ir.api.jvm.cfg.JIRCallExpr
import org.opentaint.ir.api.jvm.cfg.JIRExpr
import org.opentaint.ir.api.jvm.cfg.JIRFieldRef
import org.opentaint.ir.api.jvm.cfg.JIRArrayAccess
import org.opentaint.ir.api.jvm.cfg.JIRArgument
import org.opentaint.ir.api.jvm.cfg.JIRInst
import org.opentaint.ir.api.jvm.cfg.JIRLocalVar
import org.opentaint.ir.api.jvm.cfg.JIRThis
import org.opentaint.ir.api.jvm.cfg.JIRValue
import org.opentaint.ir.api.jvm.cfg.JIRInstanceCallExpr
import org.opentaint.ir.api.jvm.cfg.values
import org.opentaint.ir.api.jvm.ext.cfg.callExpr
import org.opentaint.ir.api.jvm.ext.toType
import org.opentaint.jvm.util.enclosingMethod

class JIRSarifTraits(
    val cp: JIRClasspath,
) : SarifTraits<JIRMethod, JIRInst> {
    private val methodCache = hashMapOf<JIRMethod, HashMap<Int, String>>()
    private val registerStart = '%'

    override fun isRegister(name: String): Boolean {
        return name[0] == registerStart
    }

    private fun loadLocalNames(md: JIRMethod) {
        if (methodCache.contains(md)) return
        val mdLocals = hashMapOf<Int, String>()
        md.flowGraph().instructions.forEach { insn ->
            getAssign(insn)?.let { assign ->
                getLocals(assign.lhv).filterNot { isRegister(it.name) }.forEach { localInfo ->
                    mdLocals[localInfo.idx] = localInfo.name
                }
            }
        }
        methodCache[md] = mdLocals
    }

    override fun getLocalName(md: JIRMethod, index: Int): String? {
        if (!methodCache.contains(md)) {
            loadLocalNames(md)
        }
        return methodCache[md]?.get(index)
    }

    private fun getOrdinal(i: Int): String {
        val suffix = if (i % 100 in 11..13) "th" else when (i % 10) {
            1 -> "st"
            2 -> "nd"
            3 -> "rd"
            else -> "th"
        }
        return "$i$suffix"
    }

    private fun getReadableInstance(statement: JIRInst) =
        if (statement !is JIRInstanceCallExpr)
            null
        else
            getReadableValue(statement, statement.instance)

    override fun printThis(statement: JIRInst) =
        if (getCallExpr(statement)?.let { getCallee(it).name } == "<init>") {
            "the created object"
        }
        else {
            getReadableInstance(statement) ?: "the calling object"
        }

    override fun printArgumentNth(index: Int, methodName: String?): String {
        val ofMethod = methodName?.let { " of \"$it\"" } ?: ""
        return "the ${getOrdinal(index + 1)} argument$ofMethod"
    }

    override fun printArgument(statement: JIRInst, index: Int): String {
        val methodName = statement.location.method.name
        val paramName = statement.location.method.parameters.getOrNull(index)?.name ?: return printArgumentNth(index, methodName)
        return "\"$paramName\""
    }

    override fun getCallee(callExpr: CommonCallExpr): JIRMethod {
        check(callExpr is JIRCallExpr)
        return callExpr.callee
    }

    override fun getCalleeClassName(callExpr: CommonCallExpr): String {
        check(callExpr is JIRCallExpr)
        return callExpr.callee.enclosingClass.simpleName
    }

    override fun getMethodClassName(md: JIRMethod): String {
        return md.enclosingClass.simpleName
    }

    override fun getCallExpr(statement: JIRInst): JIRCallExpr? {
        return statement.callExpr
    }

    sealed interface ReadableValue {
        data class Value(val string: String) : ReadableValue {
            override fun toString() =
                string
        }

        data object UnparsedArrayAccess : ReadableValue

        data object UnparsedLocalVar : ReadableValue
    }

    private fun tryGetReadableValue(statement: JIRInst, expr: CommonExpr): ReadableValue? {
        if (expr !is JIRValue) return null
        return when (expr) {
            is JIRFieldRef -> ReadableValue.Value("\"${expr.instance ?: expr.field.enclosingType.jIRClass.simpleName}.${expr.field.name}\"")
            is JIRArgument -> ReadableValue.Value(printArgument(statement, expr.index))
            is JIRArrayAccess -> {
                val arrName = tryGetReadableValue(statement, expr.array)
                val elemName = tryGetReadableValue(statement, expr.index)
                if (arrName is ReadableValue.Value)
                    if (elemName is ReadableValue.Value)
                        ReadableValue.Value("\"$arrName[$elemName]\"")
                    else
                        ReadableValue.Value("an element of \"$arrName\"")
                else
                    ReadableValue.UnparsedArrayAccess
            }
            is JIRLocalVar -> {
                if (!isRegister(expr.name))
                    ReadableValue.Value("\"${expr.name}\"")
                else {
                    val name = getLocalName(statement.enclosingMethod, expr.index)
                    if (name == null || isRegister(name))
                        ReadableValue.UnparsedLocalVar
                    else
                        ReadableValue.Value("\"name\"")
                }
            }
            is JIRThis -> ReadableValue.Value(printThis(statement))
            else -> ReadableValue.Value(expr.toString())
        }
    }

    override fun getReadableValue(statement: JIRInst, expr: CommonExpr): String? {
        val value = tryGetReadableValue(statement, expr) ?: return null
        return when (value) {
            is ReadableValue.Value -> value.string
            is ReadableValue.UnparsedLocalVar -> "a local variable"
            is ReadableValue.UnparsedArrayAccess -> "an element of array"
        }
    }

    override fun getReadableAssignee(statement: JIRInst): String? {
        if (statement !is JIRAssignInst) return null
        return getReadableValue(statement, statement.lhv)
    }

    override fun getLocals(expr: CommonExpr): List<SarifTraits.LocalInfo> {
        expr as JIRExpr
        return expr.values.filterIsInstance<JIRLocalVar>().map { SarifTraits.LocalInfo(it.index, it.name) }
    }

    override fun getAssign(statement: JIRInst): CommonAssignInst? {
        if (statement !is JIRAssignInst) return null
        return statement
    }

    override fun lineNumber(statement: JIRInst): Int {
        return statement.lineNumber
    }

    override fun locationFQN(statement: JIRInst): String {
        val method = statement.location.method
        return "${method.enclosingClass.name}#${method.name}"
    }

    override fun locationMachineName(statement: JIRInst): String =
        "${statement.location.method}:${statement.location.index}:($statement)"
}

val JIRMethod.thisInstance: JIRThis
    get() = JIRThis(enclosingClass.toType())

val JIRCallExpr.callee: JIRMethod
    get() = method.method
