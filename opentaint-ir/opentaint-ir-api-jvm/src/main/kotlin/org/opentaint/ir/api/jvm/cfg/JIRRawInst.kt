package org.opentaint.ir.api.jvm.cfg

import org.opentaint.ir.api.core.TypeName
import org.opentaint.ir.api.jvm.JIRMethod

sealed interface JIRRawInst {
    val owner: JIRMethod

    val operands: List<JIRRawExpr>

    fun <T> accept(visitor: JIRRawInstVisitor<T>): T
}

class JIRRawAssignInst(
    override val owner: JIRMethod,
    val lhv: JIRRawValue,
    val rhv: JIRRawExpr
) : JIRRawInst {

    override val operands: List<JIRRawExpr>
        get() = listOf(lhv, rhv)

    override fun toString(): String = "$lhv = $rhv"

    override fun <T> accept(visitor: JIRRawInstVisitor<T>): T {
        return visitor.visitJIRRawAssignInst(this)
    }
}

class JIRRawEnterMonitorInst(
    override val owner: JIRMethod,
    val monitor: JIRRawSimpleValue
) : JIRRawInst {
    override val operands: List<JIRRawExpr>
        get() = listOf(monitor)

    override fun toString(): String = "enter monitor $monitor"

    override fun <T> accept(visitor: JIRRawInstVisitor<T>): T {
        return visitor.visitJIRRawEnterMonitorInst(this)
    }
}

class JIRRawExitMonitorInst(
    override val owner: JIRMethod,
    val monitor: JIRRawSimpleValue
) : JIRRawInst {
    override val operands: List<JIRRawExpr>
        get() = listOf(monitor)

    override fun toString(): String = "exit monitor $monitor"

    override fun <T> accept(visitor: JIRRawInstVisitor<T>): T {
        return visitor.visitJIRRawExitMonitorInst(this)
    }
}

class JIRRawCallInst(
    override val owner: JIRMethod,
    val callExpr: JIRRawCallExpr
) : JIRRawInst {
    override val operands: List<JIRRawExpr>
        get() = listOf(callExpr)

    override fun toString(): String = "$callExpr"

    override fun <T> accept(visitor: JIRRawInstVisitor<T>): T {
        return visitor.visitJIRRawCallInst(this)
    }
}

data class JIRRawLabelRef(val name: String) {
    override fun toString() = name
}

class JIRRawLineNumberInst(override val owner: JIRMethod, val lineNumber: Int, val start: JIRRawLabelRef) : JIRRawInst {

    override val operands: List<JIRRawExpr>
        get() = emptyList()

    override fun toString(): String = "line number $lineNumber:"

    override fun <T> accept(visitor: JIRRawInstVisitor<T>): T {
        return visitor.visitJIRRawLineNumberInst(this)
    }
}

class JIRRawLabelInst(
    override val owner: JIRMethod,
    val name: String
) : JIRRawInst {
    override val operands: List<JIRRawExpr>
        get() = emptyList()

    val ref get() = JIRRawLabelRef(name)

    override fun toString(): String = "label $name:"

    override fun <T> accept(visitor: JIRRawInstVisitor<T>): T {
        return visitor.visitJIRRawLabelInst(this)
    }
}

class JIRRawReturnInst(
    override val owner: JIRMethod,
    val returnValue: JIRRawValue?
) : JIRRawInst {
    override val operands: List<JIRRawExpr>
        get() = listOfNotNull(returnValue)

    override fun toString(): String = "return" + (returnValue?.let { " $it" } ?: "")

    override fun <T> accept(visitor: JIRRawInstVisitor<T>): T {
        return visitor.visitJIRRawReturnInst(this)
    }
}

class JIRRawThrowInst(
    override val owner: JIRMethod,
    val throwable: JIRRawValue
) : JIRRawInst {
    override val operands: List<JIRRawExpr>
        get() = listOf(throwable)

    override fun toString(): String = "throw $throwable"

    override fun <T> accept(visitor: JIRRawInstVisitor<T>): T {
        return visitor.visitJIRRawThrowInst(this)
    }
}

data class JIRRawCatchEntry(
    val acceptedThrowable: TypeName,
    val startInclusive: JIRRawLabelRef,
    val endExclusive: JIRRawLabelRef
)

class JIRRawCatchInst(
    override val owner: JIRMethod,
    val throwable: JIRRawValue,
    val handler: JIRRawLabelRef,
    val entries: List<JIRRawCatchEntry>,
) : JIRRawInst {
    override val operands: List<JIRRawExpr>
        get() = listOf(throwable)

    override fun toString(): String = "catch ($throwable: ${throwable.typeName})"

    override fun <T> accept(visitor: JIRRawInstVisitor<T>): T {
        return visitor.visitJIRRawCatchInst(this)
    }
}

sealed interface JIRRawBranchingInst : JIRRawInst {
    val successors: List<JIRRawLabelRef>
}

class JIRRawGotoInst(
    override val owner: JIRMethod,
    val target: JIRRawLabelRef
) : JIRRawBranchingInst {
    override val operands: List<JIRRawExpr>
        get() = emptyList()

    override val successors: List<JIRRawLabelRef>
        get() = listOf(target)

    override fun toString(): String = "goto $target"

    override fun <T> accept(visitor: JIRRawInstVisitor<T>): T {
        return visitor.visitJIRRawGotoInst(this)
    }
}

data class JIRRawIfInst(
    override val owner: JIRMethod,
    val condition: JIRRawConditionExpr,
    val trueBranch: JIRRawLabelRef,
    val falseBranch: JIRRawLabelRef
) : JIRRawBranchingInst {
    override val operands: List<JIRRawExpr>
        get() = listOf(condition)

    override val successors: List<JIRRawLabelRef>
        get() = listOf(trueBranch, falseBranch)

    override fun toString(): String = "if ($condition) goto $trueBranch else $falseBranch"

    override fun <T> accept(visitor: JIRRawInstVisitor<T>): T {
        return visitor.visitJIRRawIfInst(this)
    }
}

data class JIRRawSwitchInst(
    override val owner: JIRMethod,
    val key: JIRRawValue,
    val branches: Map<JIRRawValue, JIRRawLabelRef>,
    val default: JIRRawLabelRef
) : JIRRawBranchingInst {
    override val operands: List<JIRRawExpr>
        get() = listOf(key) + branches.keys

    override val successors: List<JIRRawLabelRef>
        get() = branches.values + default

    override fun toString(): String = buildString {
        append("switch ($key) { ")
        branches.forEach { (option, label) -> append("$option -> $label") }
        append("else -> ${default.name} }")
    }

    override fun <T> accept(visitor: JIRRawInstVisitor<T>): T {
        return visitor.visitJIRRawSwitchInst(this)
    }
}

sealed interface JIRRawExpr {
    val typeName: TypeName
    val operands: List<JIRRawValue>

    fun <T> accept(visitor: JIRRawExprVisitor<T>): T
}

interface JIRRawBinaryExpr : JIRRawExpr {
    val lhv: JIRRawValue
    val rhv: JIRRawValue
}

data class JIRRawAddExpr(
    override val typeName: TypeName,
    override val lhv: JIRRawValue,
    override val rhv: JIRRawValue
) : JIRRawBinaryExpr {
    override val operands: List<JIRRawValue>
        get() = listOf(lhv, rhv)

    override fun toString(): String = "$lhv + $rhv"

    override fun <T> accept(visitor: JIRRawExprVisitor<T>): T {
        return visitor.visitJIRRawAddExpr(this)
    }
}

data class JIRRawAndExpr(
    override val typeName: TypeName,
    override val lhv: JIRRawValue,
    override val rhv: JIRRawValue
) : JIRRawBinaryExpr {
    override val operands: List<JIRRawValue>
        get() = listOf(lhv, rhv)

    override fun toString(): String = "$lhv & $rhv"

    override fun <T> accept(visitor: JIRRawExprVisitor<T>): T {
        return visitor.visitJIRRawAndExpr(this)
    }
}

data class JIRRawCmpExpr(
    override val typeName: TypeName,
    override val lhv: JIRRawValue,
    override val rhv: JIRRawValue
) : JIRRawBinaryExpr {
    override val operands: List<JIRRawValue>
        get() = listOf(lhv, rhv)

    override fun toString(): String = "$lhv cmp $rhv"

    override fun <T> accept(visitor: JIRRawExprVisitor<T>): T {
        return visitor.visitJIRRawCmpExpr(this)
    }
}

data class JIRRawCmpgExpr(
    override val typeName: TypeName,
    override val lhv: JIRRawValue,
    override val rhv: JIRRawValue
) : JIRRawBinaryExpr {
    override val operands: List<JIRRawValue>
        get() = listOf(lhv, rhv)

    override fun toString(): String = "$lhv cmpg $rhv"

    override fun <T> accept(visitor: JIRRawExprVisitor<T>): T {
        return visitor.visitJIRRawCmpgExpr(this)
    }
}

data class JIRRawCmplExpr(
    override val typeName: TypeName,
    override val lhv: JIRRawValue,
    override val rhv: JIRRawValue
) : JIRRawBinaryExpr {
    override val operands: List<JIRRawValue>
        get() = listOf(lhv, rhv)

    override fun toString(): String = "$lhv cmpl $rhv"

    override fun <T> accept(visitor: JIRRawExprVisitor<T>): T {
        return visitor.visitJIRRawCmplExpr(this)
    }
}

data class JIRRawDivExpr(
    override val typeName: TypeName,
    override val lhv: JIRRawValue,
    override val rhv: JIRRawValue
) : JIRRawBinaryExpr {
    override val operands: List<JIRRawValue>
        get() = listOf(lhv, rhv)

    override fun toString(): String = "$lhv / $rhv"

    override fun <T> accept(visitor: JIRRawExprVisitor<T>): T {
        return visitor.visitJIRRawDivExpr(this)
    }
}

data class JIRRawMulExpr(
    override val typeName: TypeName,
    override val lhv: JIRRawValue,
    override val rhv: JIRRawValue
) : JIRRawBinaryExpr {
    override val operands: List<JIRRawValue>
        get() = listOf(lhv, rhv)

    override fun toString(): String = "$lhv * $rhv"

    override fun <T> accept(visitor: JIRRawExprVisitor<T>): T {
        return visitor.visitJIRRawMulExpr(this)
    }
}

sealed interface JIRRawConditionExpr : JIRRawBinaryExpr

data class JIRRawEqExpr(
    override val typeName: TypeName,
    override val lhv: JIRRawValue,
    override val rhv: JIRRawValue
) : JIRRawConditionExpr {
    override val operands: List<JIRRawValue>
        get() = listOf(lhv, rhv)

    override fun toString(): String = "$lhv == $rhv"

    override fun <T> accept(visitor: JIRRawExprVisitor<T>): T {
        return visitor.visitJIRRawEqExpr(this)
    }
}

data class JIRRawNeqExpr(
    override val typeName: TypeName,
    override val lhv: JIRRawValue,
    override val rhv: JIRRawValue
) : JIRRawConditionExpr {
    override val operands: List<JIRRawValue>
        get() = listOf(lhv, rhv)

    override fun toString(): String = "$lhv != $rhv"

    override fun <T> accept(visitor: JIRRawExprVisitor<T>): T {
        return visitor.visitJIRRawNeqExpr(this)
    }
}

data class JIRRawGeExpr(
    override val typeName: TypeName,
    override val lhv: JIRRawValue,
    override val rhv: JIRRawValue
) : JIRRawConditionExpr {
    override val operands: List<JIRRawValue>
        get() = listOf(lhv, rhv)

    override fun toString(): String = "$lhv >= $rhv"

    override fun <T> accept(visitor: JIRRawExprVisitor<T>): T {
        return visitor.visitJIRRawGeExpr(this)
    }
}

data class JIRRawGtExpr(
    override val typeName: TypeName,
    override val lhv: JIRRawValue,
    override val rhv: JIRRawValue
) : JIRRawConditionExpr {
    override val operands: List<JIRRawValue>
        get() = listOf(lhv, rhv)

    override fun toString(): String = "$lhv > $rhv"

    override fun <T> accept(visitor: JIRRawExprVisitor<T>): T {
        return visitor.visitJIRRawGtExpr(this)
    }
}

data class JIRRawLeExpr(
    override val typeName: TypeName,
    override val lhv: JIRRawValue,
    override val rhv: JIRRawValue
) : JIRRawConditionExpr {
    override val operands: List<JIRRawValue>
        get() = listOf(lhv, rhv)

    override fun toString(): String = "$lhv <= $rhv"

    override fun <T> accept(visitor: JIRRawExprVisitor<T>): T {
        return visitor.visitJIRRawLeExpr(this)
    }
}

data class JIRRawLtExpr(
    override val typeName: TypeName,
    override val lhv: JIRRawValue,
    override val rhv: JIRRawValue
) : JIRRawConditionExpr {
    override val operands: List<JIRRawValue>
        get() = listOf(lhv, rhv)

    override fun toString(): String = "$lhv < $rhv"

    override fun <T> accept(visitor: JIRRawExprVisitor<T>): T {
        return visitor.visitJIRRawLtExpr(this)
    }
}

data class JIRRawOrExpr(
    override val typeName: TypeName,
    override val lhv: JIRRawValue,
    override val rhv: JIRRawValue
) : JIRRawBinaryExpr {
    override val operands: List<JIRRawValue>
        get() = listOf(lhv, rhv)

    override fun toString(): String = "$lhv | $rhv"

    override fun <T> accept(visitor: JIRRawExprVisitor<T>): T {
        return visitor.visitJIRRawOrExpr(this)
    }
}

data class JIRRawRemExpr(
    override val typeName: TypeName,
    override val lhv: JIRRawValue,
    override val rhv: JIRRawValue
) : JIRRawBinaryExpr {
    override val operands: List<JIRRawValue>
        get() = listOf(lhv, rhv)

    override fun toString(): String = "$lhv % $rhv"

    override fun <T> accept(visitor: JIRRawExprVisitor<T>): T {
        return visitor.visitJIRRawRemExpr(this)
    }
}

data class JIRRawShlExpr(
    override val typeName: TypeName,
    override val lhv: JIRRawValue,
    override val rhv: JIRRawValue
) : JIRRawBinaryExpr {
    override val operands: List<JIRRawValue>
        get() = listOf(lhv, rhv)

    override fun toString(): String = "$lhv << $rhv"

    override fun <T> accept(visitor: JIRRawExprVisitor<T>): T {
        return visitor.visitJIRRawShlExpr(this)
    }
}

data class JIRRawShrExpr(
    override val typeName: TypeName,
    override val lhv: JIRRawValue,
    override val rhv: JIRRawValue
) : JIRRawBinaryExpr {
    override val operands: List<JIRRawValue>
        get() = listOf(lhv, rhv)

    override fun toString(): String = "$lhv >> $rhv"

    override fun <T> accept(visitor: JIRRawExprVisitor<T>): T {
        return visitor.visitJIRRawShrExpr(this)
    }
}

data class JIRRawSubExpr(
    override val typeName: TypeName,
    override val lhv: JIRRawValue,
    override val rhv: JIRRawValue
) : JIRRawBinaryExpr {
    override val operands: List<JIRRawValue>
        get() = listOf(lhv, rhv)

    override fun toString(): String = "$lhv - $rhv"

    override fun <T> accept(visitor: JIRRawExprVisitor<T>): T {
        return visitor.visitJIRRawSubExpr(this)
    }
}

data class JIRRawUshrExpr(
    override val typeName: TypeName,
    override val lhv: JIRRawValue,
    override val rhv: JIRRawValue
) : JIRRawBinaryExpr {
    override val operands: List<JIRRawValue>
        get() = listOf(lhv, rhv)

    override fun toString(): String = "$lhv u<< $rhv"

    override fun <T> accept(visitor: JIRRawExprVisitor<T>): T {
        return visitor.visitJIRRawUshrExpr(this)
    }
}

data class JIRRawXorExpr(
    override val typeName: TypeName,
    override val lhv: JIRRawValue,
    override val rhv: JIRRawValue
) : JIRRawBinaryExpr {
    override val operands: List<JIRRawValue>
        get() = listOf(lhv, rhv)

    override fun toString(): String = "$lhv ^ $rhv"

    override fun <T> accept(visitor: JIRRawExprVisitor<T>): T {
        return visitor.visitJIRRawXorExpr(this)
    }
}

data class JIRRawLengthExpr(
    override val typeName: TypeName,
    val array: JIRRawValue
) : JIRRawExpr {
    override val operands: List<JIRRawValue>
        get() = listOf(array)

    override fun toString(): String = "$array.length"

    override fun <T> accept(visitor: JIRRawExprVisitor<T>): T {
        return visitor.visitJIRRawLengthExpr(this)
    }
}

data class JIRRawNegExpr(
    override val typeName: TypeName,
    val operand: JIRRawValue
) : JIRRawExpr {
    override val operands: List<JIRRawValue>
        get() = listOf(operand)

    override fun toString(): String = "-$operand"

    override fun <T> accept(visitor: JIRRawExprVisitor<T>): T {
        return visitor.visitJIRRawNegExpr(this)
    }
}

data class JIRRawCastExpr(
    override val typeName: TypeName,
    val operand: JIRRawValue
) : JIRRawExpr {
    override val operands: List<JIRRawValue>
        get() = listOf(operand)

    override fun toString(): String = "($typeName) $operand"

    override fun <T> accept(visitor: JIRRawExprVisitor<T>): T {
        return visitor.visitJIRRawCastExpr(this)
    }
}

data class JIRRawNewExpr(
    override val typeName: TypeName
) : JIRRawExpr {
    override val operands: List<JIRRawValue>
        get() = emptyList()

    override fun toString(): String = "new $typeName"

    override fun <T> accept(visitor: JIRRawExprVisitor<T>): T {
        return visitor.visitJIRRawNewExpr(this)
    }
}

data class JIRRawNewArrayExpr(
    override val typeName: TypeName,
    val dimensions: List<JIRRawValue>
) : JIRRawExpr {
    constructor(typeName: TypeName, length: JIRRawValue) : this(typeName, listOf(length))

    override val operands: List<JIRRawValue>
        get() = dimensions

    override fun toString(): String = "new ${arrayTypeToStringWithDimensions(typeName, dimensions)}"

    override fun <T> accept(visitor: JIRRawExprVisitor<T>): T {
        return visitor.visitJIRRawNewArrayExpr(this)
    }
    
    companion object {
        private val regexToProcessDimensions = Regex("\\[(.*?)]")

        private fun arrayTypeToStringWithDimensions(typeName: TypeName, dimensions: List<JIRRawValue>) {
            var curDim = 0
            regexToProcessDimensions.replace("$typeName") {
                "[${dimensions.getOrNull(curDim++) ?: ""}]"
            }
        }
    }
}

data class JIRRawInstanceOfExpr(
    override val typeName: TypeName,
    val operand: JIRRawValue,
    val targetType: TypeName
) : JIRRawExpr {
    override val operands: List<JIRRawValue>
        get() = listOf(operand)

    override fun toString(): String = "$operand instanceof ${targetType.typeName}"

    override fun <T> accept(visitor: JIRRawExprVisitor<T>): T {
        return visitor.visitJIRRawInstanceOfExpr(this)
    }
}

sealed interface JIRRawCallExpr : JIRRawExpr {
    val declaringClass: TypeName
    val methodName: String
    val argumentTypes: List<TypeName>
    val returnType: TypeName
    val args: List<JIRRawValue>

    override val typeName get() = returnType

    override val operands: List<JIRRawValue>
        get() = args
}

sealed interface JIRRawInstanceExpr: JIRRawCallExpr {
    val instance: JIRRawValue
}

sealed interface BsmArg

data class BsmIntArg(val value: Int) : BsmArg {
    override fun toString(): String = value.toString()
}

data class BsmFloatArg(val value: Float) : BsmArg {
    override fun toString(): String = value.toString()
}

data class BsmLongArg(val value: Long) : BsmArg {
    override fun toString(): String = value.toString()
}

data class BsmDoubleArg(val value: Double) : BsmArg {
    override fun toString(): String = value.toString()
}

data class BsmStringArg(val value: String) : BsmArg {
    override fun toString(): String = "\"$value\""
}

data class BsmTypeArg(val typeName: TypeName) : BsmArg {
    override fun toString(): String = typeName.typeName
}

data class BsmMethodTypeArg(val argumentTypes: List<TypeName>, val returnType: TypeName) : BsmArg {
    override fun toString(): String = "(${argumentTypes.joinToString { it.typeName }}:${returnType.typeName})"
}

data class BsmHandle(
    val tag: Int,
    val declaringClass: TypeName,
    val name: String,
    val argTypes: List<TypeName>,
    val returnType: TypeName,
    val isInterface: Boolean,
) : BsmArg

data class JIRRawDynamicCallExpr(
    val bsm: BsmHandle,
    val bsmArgs: List<BsmArg>,
    val callSiteMethodName: String,
    val callSiteArgTypes: List<TypeName>,
    val callSiteReturnType: TypeName,
    val callSiteArgs: List<JIRRawValue>
) : JIRRawCallExpr {
    override val declaringClass get() = bsm.declaringClass
    override val methodName get() = bsm.name
    override val argumentTypes get() = bsm.argTypes
    override val returnType get() = bsm.returnType
    override val typeName get() = returnType
    override val args get() = callSiteArgs

    override fun <T> accept(visitor: JIRRawExprVisitor<T>): T {
        return visitor.visitJIRRawDynamicCallExpr(this)
    }
}

data class JIRRawVirtualCallExpr(
    override val declaringClass: TypeName,
    override val methodName: String,
    override val argumentTypes: List<TypeName>,
    override val returnType: TypeName,
    override val instance: JIRRawValue,
    override val args: List<JIRRawValue>,
) : JIRRawInstanceExpr {
    override val operands: List<JIRRawValue>
        get() = listOf(instance) + args

    override fun toString(): String =
        "$instance.$methodName${args.joinToString(prefix = "(", postfix = ")", separator = ", ")}"

    override fun <T> accept(visitor: JIRRawExprVisitor<T>): T {
        return visitor.visitJIRRawVirtualCallExpr(this)
    }
}

data class JIRRawInterfaceCallExpr(
    override val declaringClass: TypeName,
    override val methodName: String,
    override val argumentTypes: List<TypeName>,
    override val returnType: TypeName,
    override val instance: JIRRawValue,
    override val args: List<JIRRawValue>,
) : JIRRawInstanceExpr {
    override val operands: List<JIRRawValue>
        get() = listOf(instance) + args

    override fun toString(): String =
        "$instance.$methodName${args.joinToString(prefix = "(", postfix = ")", separator = ", ")}"

    override fun <T> accept(visitor: JIRRawExprVisitor<T>): T {
        return visitor.visitJIRRawInterfaceCallExpr(this)
    }
}

data class JIRRawStaticCallExpr(
    override val declaringClass: TypeName,
    override val methodName: String,
    override val argumentTypes: List<TypeName>,
    override val returnType: TypeName,
    override val args: List<JIRRawValue>,
) : JIRRawCallExpr {
    override fun toString(): String =
        "$declaringClass.$methodName${args.joinToString(prefix = "(", postfix = ")", separator = ", ")}"

    override fun <T> accept(visitor: JIRRawExprVisitor<T>): T {
        return visitor.visitJIRRawStaticCallExpr(this)
    }
}

data class JIRRawSpecialCallExpr(
    override val declaringClass: TypeName,
    override val methodName: String,
    override val argumentTypes: List<TypeName>,
    override val returnType: TypeName,
    override val instance: JIRRawValue,
    override val args: List<JIRRawValue>,
) : JIRRawInstanceExpr {
    override fun toString(): String =
        "$instance.$methodName${args.joinToString(prefix = "(", postfix = ")", separator = ", ")}"

    override fun <T> accept(visitor: JIRRawExprVisitor<T>): T {
        return visitor.visitJIRRawSpecialCallExpr(this)
    }
}

sealed interface JIRRawValue : JIRRawExpr {
    override val operands: List<JIRRawValue>
        get() = emptyList()
}

sealed interface JIRRawSimpleValue : JIRRawValue

sealed interface JIRRawLocal : JIRRawSimpleValue {
    val name: String
}

data class JIRRawThis(override val typeName: TypeName) : JIRRawSimpleValue {
    override fun toString(): String = "this"

    override fun <T> accept(visitor: JIRRawExprVisitor<T>): T {
        return visitor.visitJIRRawThis(this)
    }
}

data class JIRRawArgument(val index: Int, override val name: String, override val typeName: TypeName) : JIRRawLocal {
    companion object {
        @JvmStatic
        fun of(index: Int, name: String?, typeName: TypeName): JIRRawArgument {
            return JIRRawArgument(index, name ?: "arg$$index", typeName)
        }
    }

    override fun toString(): String = name

    override fun <T> accept(visitor: JIRRawExprVisitor<T>): T {
        return visitor.visitJIRRawArgument(this)
    }
}

data class JIRRawLocalVar(override val name: String, override val typeName: TypeName) : JIRRawLocal {
    override fun toString(): String = name

    override fun <T> accept(visitor: JIRRawExprVisitor<T>): T {
        return visitor.visitJIRRawLocalVar(this)
    }
}

sealed interface JIRRawComplexValue : JIRRawValue

data class JIRRawFieldRef(
    val instance: JIRRawValue?,
    val declaringClass: TypeName,
    val fieldName: String,
    override val typeName: TypeName
) : JIRRawComplexValue {
    constructor(declaringClass: TypeName, fieldName: String, typeName: TypeName) : this(
        null,
        declaringClass,
        fieldName,
        typeName
    )

    override fun toString(): String = "${instance ?: declaringClass}.$fieldName"

    override fun <T> accept(visitor: JIRRawExprVisitor<T>): T {
        return visitor.visitJIRRawFieldRef(this)
    }
}

data class JIRRawArrayAccess(
    val array: JIRRawValue,
    val index: JIRRawValue,
    override val typeName: TypeName
) : JIRRawComplexValue {
    override fun toString(): String = "$array[$index]"

    override fun <T> accept(visitor: JIRRawExprVisitor<T>): T {
        return visitor.visitJIRRawArrayAccess(this)
    }
}

sealed interface JIRRawConstant : JIRRawSimpleValue

data class JIRRawBool(val value: Boolean, override val typeName: TypeName) : JIRRawConstant {
    override fun toString(): String = "$value"

    override fun <T> accept(visitor: JIRRawExprVisitor<T>): T {
        return visitor.visitJIRRawBool(this)
    }
}

data class JIRRawByte(val value: Byte, override val typeName: TypeName) : JIRRawConstant {
    override fun toString(): String = "$value"

    override fun <T> accept(visitor: JIRRawExprVisitor<T>): T {
        return visitor.visitJIRRawByte(this)
    }
}

data class JIRRawChar(val value: Char, override val typeName: TypeName) : JIRRawConstant {
    override fun toString(): String = "$value"

    override fun <T> accept(visitor: JIRRawExprVisitor<T>): T {
        return visitor.visitJIRRawChar(this)
    }
}

data class JIRRawShort(val value: Short, override val typeName: TypeName) : JIRRawConstant {
    override fun toString(): String = "$value"

    override fun <T> accept(visitor: JIRRawExprVisitor<T>): T {
        return visitor.visitJIRRawShort(this)
    }
}

data class JIRRawInt(val value: Int, override val typeName: TypeName) : JIRRawConstant {
    override fun toString(): String = "$value"

    override fun <T> accept(visitor: JIRRawExprVisitor<T>): T {
        return visitor.visitJIRRawInt(this)
    }
}

data class JIRRawLong(val value: Long, override val typeName: TypeName) : JIRRawConstant {
    override fun toString(): String = "$value"

    override fun <T> accept(visitor: JIRRawExprVisitor<T>): T {
        return visitor.visitJIRRawLong(this)
    }
}

data class JIRRawFloat(val value: Float, override val typeName: TypeName) : JIRRawConstant {
    override fun toString(): String = "$value"

    override fun <T> accept(visitor: JIRRawExprVisitor<T>): T {
        return visitor.visitJIRRawFloat(this)
    }
}

data class JIRRawDouble(val value: Double, override val typeName: TypeName) : JIRRawConstant {
    override fun toString(): String = "$value"

    override fun <T> accept(visitor: JIRRawExprVisitor<T>): T {
        return visitor.visitJIRRawDouble(this)
    }
}

data class JIRRawNullConstant(override val typeName: TypeName) : JIRRawConstant {
    override fun toString(): String = "null"

    override fun <T> accept(visitor: JIRRawExprVisitor<T>): T {
        return visitor.visitJIRRawNullConstant(this)
    }
}

data class JIRRawStringConstant(val value: String, override val typeName: TypeName) : JIRRawConstant {
    override fun toString(): String = "\"$value\""

    override fun <T> accept(visitor: JIRRawExprVisitor<T>): T {
        return visitor.visitJIRRawStringConstant(this)
    }
}

data class JIRRawClassConstant(val className: TypeName, override val typeName: TypeName) : JIRRawConstant {
    override fun toString(): String = "$className.class"

    override fun <T> accept(visitor: JIRRawExprVisitor<T>): T {
        return visitor.visitJIRRawClassConstant(this)
    }
}

data class JIRRawMethodConstant(
    val declaringClass: TypeName,
    val name: String,
    val argumentTypes: List<TypeName>,
    val returnType: TypeName,
    override val typeName: TypeName
) : JIRRawConstant {
    override fun toString(): String =
        "$declaringClass.$name${argumentTypes.joinToString(prefix = "(", postfix = ")")}:$returnType"

    override fun <T> accept(visitor: JIRRawExprVisitor<T>): T {
        return visitor.visitJIRRawMethodConstant(this)
    }
}

data class JIRRawMethodType(
    val argumentTypes: List<TypeName>,
    val returnType: TypeName,
    private val methodType: TypeName
) : JIRRawConstant {
    override val typeName: TypeName = methodType

    override fun toString(): String =
        "${argumentTypes.joinToString(prefix = "(", postfix = ")")}:$returnType"

    override fun <T> accept(visitor: JIRRawExprVisitor<T>): T {
        return visitor.visitJIRRawMethodType(this)
    }
}

//
//fun JIRRawInstList.lineNumberOf(inst: JIRRawInst): Int? {
//    val idx: Int = instructions.indexOf(inst)
//    assert(idx != -1)
//
//    // Get index of labels and insnNode within method
//    val insnIt: ListIterator<AbstractInsnNode> = insnList.iterator(idx)
//    while (insnIt.hasPrevious()) {
//        val node: AbstractInsnNode = insnIt.previous()
//        if (node is LineNumberNode) {
//            return node as LineNumberNode
//        }
//    }
//    return null
//}