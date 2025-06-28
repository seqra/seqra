package org.opentaint.ir.api.jvm.cfg

import org.opentaint.ir.api.common.cfg.CommonArgument
import org.opentaint.ir.api.common.cfg.CommonArrayAccess
import org.opentaint.ir.api.common.cfg.CommonAssignInst
import org.opentaint.ir.api.common.cfg.CommonCallExpr
import org.opentaint.ir.api.common.cfg.CommonCallInst
import org.opentaint.ir.api.common.cfg.CommonExpr
import org.opentaint.ir.api.common.cfg.CommonFieldRef
import org.opentaint.ir.api.common.cfg.CommonGotoInst
import org.opentaint.ir.api.common.cfg.CommonIfInst
import org.opentaint.ir.api.common.cfg.CommonInst
import org.opentaint.ir.api.common.cfg.CommonInstLocation
import org.opentaint.ir.api.common.cfg.CommonInstanceCallExpr
import org.opentaint.ir.api.common.cfg.CommonReturnInst
import org.opentaint.ir.api.common.cfg.CommonThis
import org.opentaint.ir.api.common.cfg.CommonValue
import org.opentaint.ir.api.jvm.JIRField
import org.opentaint.ir.api.jvm.JIRMethod
import org.opentaint.ir.api.jvm.JIRType
import org.opentaint.ir.api.jvm.JIRTypedField
import org.opentaint.ir.api.jvm.JIRTypedMethod

interface TypedMethodRef {
    val name: String
    val method: JIRTypedMethod
}

interface VirtualTypedMethodRef : TypedMethodRef {
    val declaredMethod: JIRTypedMethod
}

interface JIRInstLocation : CommonInstLocation {
    override val method: JIRMethod
    val index: Int
    val lineNumber: Int
}

interface JIRInst : CommonInst {
    override val location: JIRInstLocation
    val operands: List<JIRExpr>

    override val method: JIRMethod
        get() = location.method
    val lineNumber: Int
        get() = location.lineNumber

    fun <T> accept(visitor: JIRInstVisitor<T>): T
}

abstract class AbstractJIRInst(override val location: JIRInstLocation) : JIRInst {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as AbstractJIRInst

        if (location != other.location) return false

        return true
    }

    override fun hashCode(): Int {
        return location.hashCode()
    }
}

data class JIRInstRef(
    val index: Int,
)

class JIRAssignInst(
    location: JIRInstLocation,
    override val lhv: JIRValue,
    override val rhv: JIRExpr,
) : AbstractJIRInst(location), CommonAssignInst {
    override val operands: List<JIRExpr>
        get() = listOf(lhv, rhv)

    override fun toString(): String = "$lhv = $rhv"

    override fun <T> accept(visitor: JIRInstVisitor<T>): T {
        return visitor.visitJIRAssignInst(this)
    }
}

class JIREnterMonitorInst(
    location: JIRInstLocation,
    val monitor: JIRValue,
) : AbstractJIRInst(location) {
    override val operands: List<JIRExpr>
        get() = listOf(monitor)

    override fun toString(): String = "enter monitor $monitor"

    override fun <T> accept(visitor: JIRInstVisitor<T>): T {
        return visitor.visitJIREnterMonitorInst(this)
    }
}

class JIRExitMonitorInst(
    location: JIRInstLocation,
    val monitor: JIRValue,
) : AbstractJIRInst(location) {
    override val operands: List<JIRExpr>
        get() = listOf(monitor)

    override fun toString(): String = "exit monitor $monitor"

    override fun <T> accept(visitor: JIRInstVisitor<T>): T {
        return visitor.visitJIRExitMonitorInst(this)
    }
}

class JIRCallInst(
    location: JIRInstLocation,
    val callExpr: JIRCallExpr,
) : AbstractJIRInst(location), CommonCallInst {
    override val operands: List<JIRExpr>
        get() = listOf(callExpr)

    override fun toString(): String = "$callExpr"

    override fun <T> accept(visitor: JIRInstVisitor<T>): T {
        return visitor.visitJIRCallInst(this)
    }
}

interface JIRTerminatingInst : JIRInst

class JIRReturnInst(
    location: JIRInstLocation,
    override val returnValue: JIRValue?,
) : AbstractJIRInst(location), JIRTerminatingInst, CommonReturnInst {
    override val operands: List<JIRExpr>
        get() = listOfNotNull(returnValue)

    override fun toString(): String = "return" + (returnValue?.let { " $it" } ?: "")

    override fun <T> accept(visitor: JIRInstVisitor<T>): T {
        return visitor.visitJIRReturnInst(this)
    }
}

class JIRThrowInst(
    location: JIRInstLocation,
    val throwable: JIRValue,
) : AbstractJIRInst(location), JIRTerminatingInst {
    override val operands: List<JIRExpr>
        get() = listOf(throwable)

    override fun toString(): String = "throw $throwable"

    override fun <T> accept(visitor: JIRInstVisitor<T>): T {
        return visitor.visitJIRThrowInst(this)
    }
}

class JIRCatchInst(
    location: JIRInstLocation,
    val throwable: JIRValue,
    val throwableTypes: List<JIRType>,
    val throwers: List<JIRInstRef>,
) : AbstractJIRInst(location) {
    override val operands: List<JIRExpr>
        get() = listOf(throwable)

    override fun toString(): String = "catch ($throwable: ${throwable.type.typeName})"

    override fun <T> accept(visitor: JIRInstVisitor<T>): T {
        return visitor.visitJIRCatchInst(this)
    }
}

interface JIRBranchingInst : JIRInst {
    val successors: List<JIRInstRef>
}

class JIRGotoInst(
    location: JIRInstLocation,
    val target: JIRInstRef,
) : AbstractJIRInst(location), JIRBranchingInst, CommonGotoInst {
    override val operands: List<JIRExpr>
        get() = emptyList()

    override val successors: List<JIRInstRef>
        get() = listOf(target)

    override fun toString(): String = "goto $target"

    override fun <T> accept(visitor: JIRInstVisitor<T>): T {
        return visitor.visitJIRGotoInst(this)
    }
}

class JIRIfInst(
    location: JIRInstLocation,
    val condition: JIRConditionExpr,
    val trueBranch: JIRInstRef,
    val falseBranch: JIRInstRef,
) : AbstractJIRInst(location), JIRBranchingInst, CommonIfInst {
    override val operands: List<JIRExpr>
        get() = listOf(condition)

    override val successors: List<JIRInstRef>
        get() = listOf(trueBranch, falseBranch)

    override fun toString(): String = "if ($condition)"

    override fun <T> accept(visitor: JIRInstVisitor<T>): T {
        return visitor.visitJIRIfInst(this)
    }
}

class JIRSwitchInst(
    location: JIRInstLocation,
    val key: JIRValue,
    val branches: Map<JIRValue, JIRInstRef>,
    val default: JIRInstRef,
) : AbstractJIRInst(location), JIRBranchingInst {
    override val operands: List<JIRExpr>
        get() = listOf(key) + branches.keys

    override val successors: List<JIRInstRef>
        get() = branches.values + default

    override fun toString(): String = "switch ($key)"

    override fun <T> accept(visitor: JIRInstVisitor<T>): T {
        return visitor.visitJIRSwitchInst(this)
    }
}

interface JIRExpr : CommonExpr {
    val type: JIRType
    val operands: List<JIRValue>

    override val typeName: String
        get() = type.typeName

    fun <T> accept(visitor: JIRExprVisitor<T>): T
}

interface JIRBinaryExpr : JIRExpr {
    val lhv: JIRValue
    val rhv: JIRValue
}

data class JIRAddExpr(
    override val type: JIRType,
    override val lhv: JIRValue,
    override val rhv: JIRValue,
) : JIRBinaryExpr {
    override val operands: List<JIRValue>
        get() = listOf(lhv, rhv)

    override fun toString(): String = "$lhv + $rhv"

    override fun <T> accept(visitor: JIRExprVisitor<T>): T {
        return visitor.visitJIRAddExpr(this)
    }
}

data class JIRAndExpr(
    override val type: JIRType,
    override val lhv: JIRValue,
    override val rhv: JIRValue,
) : JIRBinaryExpr {
    override val operands: List<JIRValue>
        get() = listOf(lhv, rhv)

    override fun toString(): String = "$lhv & $rhv"

    override fun <T> accept(visitor: JIRExprVisitor<T>): T {
        return visitor.visitJIRAndExpr(this)
    }
}

data class JIRCmpExpr(
    override val type: JIRType,
    override val lhv: JIRValue,
    override val rhv: JIRValue,
) : JIRBinaryExpr {
    override val operands: List<JIRValue>
        get() = listOf(lhv, rhv)

    override fun toString(): String = "$lhv cmp $rhv"

    override fun <T> accept(visitor: JIRExprVisitor<T>): T {
        return visitor.visitJIRCmpExpr(this)
    }
}

data class JIRCmpgExpr(
    override val type: JIRType,
    override val lhv: JIRValue,
    override val rhv: JIRValue,
) : JIRBinaryExpr {
    override val operands: List<JIRValue>
        get() = listOf(lhv, rhv)

    override fun toString(): String = "$lhv cmpg $rhv"

    override fun <T> accept(visitor: JIRExprVisitor<T>): T {
        return visitor.visitJIRCmpgExpr(this)
    }
}

data class JIRCmplExpr(
    override val type: JIRType,
    override val lhv: JIRValue,
    override val rhv: JIRValue,
) : JIRBinaryExpr {
    override val operands: List<JIRValue>
        get() = listOf(lhv, rhv)

    override fun toString(): String = "$lhv cmpl $rhv"

    override fun <T> accept(visitor: JIRExprVisitor<T>): T {
        return visitor.visitJIRCmplExpr(this)
    }
}

data class JIRDivExpr(
    override val type: JIRType,
    override val lhv: JIRValue,
    override val rhv: JIRValue,
) : JIRBinaryExpr {
    override val operands: List<JIRValue>
        get() = listOf(lhv, rhv)

    override fun toString(): String = "$lhv / $rhv"

    override fun <T> accept(visitor: JIRExprVisitor<T>): T {
        return visitor.visitJIRDivExpr(this)
    }
}

data class JIRMulExpr(
    override val type: JIRType,
    override val lhv: JIRValue,
    override val rhv: JIRValue,
) : JIRBinaryExpr {
    override val operands: List<JIRValue>
        get() = listOf(lhv, rhv)

    override fun toString(): String = "$lhv * $rhv"

    override fun <T> accept(visitor: JIRExprVisitor<T>): T {
        return visitor.visitJIRMulExpr(this)
    }
}

interface JIRConditionExpr : JIRBinaryExpr

data class JIREqExpr(
    override val type: JIRType,
    override val lhv: JIRValue,
    override val rhv: JIRValue,
) : JIRConditionExpr {
    override val operands: List<JIRValue>
        get() = listOf(lhv, rhv)

    override fun toString(): String = "$lhv == $rhv"

    override fun <T> accept(visitor: JIRExprVisitor<T>): T {
        return visitor.visitJIREqExpr(this)
    }
}

data class JIRNeqExpr(
    override val type: JIRType,
    override val lhv: JIRValue,
    override val rhv: JIRValue,
) : JIRConditionExpr {
    override val operands: List<JIRValue>
        get() = listOf(lhv, rhv)

    override fun toString(): String = "$lhv != $rhv"

    override fun <T> accept(visitor: JIRExprVisitor<T>): T {
        return visitor.visitJIRNeqExpr(this)
    }
}

data class JIRGeExpr(
    override val type: JIRType,
    override val lhv: JIRValue,
    override val rhv: JIRValue,
) : JIRConditionExpr {
    override val operands: List<JIRValue>
        get() = listOf(lhv, rhv)

    override fun toString(): String = "$lhv >= $rhv"

    override fun <T> accept(visitor: JIRExprVisitor<T>): T {
        return visitor.visitJIRGeExpr(this)
    }
}

data class JIRGtExpr(
    override val type: JIRType,
    override val lhv: JIRValue,
    override val rhv: JIRValue,
) : JIRConditionExpr {
    override val operands: List<JIRValue>
        get() = listOf(lhv, rhv)

    override fun toString(): String = "$lhv > $rhv"

    override fun <T> accept(visitor: JIRExprVisitor<T>): T {
        return visitor.visitJIRGtExpr(this)
    }
}

data class JIRLeExpr(
    override val type: JIRType,
    override val lhv: JIRValue,
    override val rhv: JIRValue,
) : JIRConditionExpr {
    override val operands: List<JIRValue>
        get() = listOf(lhv, rhv)

    override fun toString(): String = "$lhv <= $rhv"

    override fun <T> accept(visitor: JIRExprVisitor<T>): T {
        return visitor.visitJIRLeExpr(this)
    }
}

data class JIRLtExpr(
    override val type: JIRType,
    override val lhv: JIRValue,
    override val rhv: JIRValue,
) : JIRConditionExpr {
    override val operands: List<JIRValue>
        get() = listOf(lhv, rhv)

    override fun toString(): String = "$lhv < $rhv"

    override fun <T> accept(visitor: JIRExprVisitor<T>): T {
        return visitor.visitJIRLtExpr(this)
    }
}

data class JIROrExpr(
    override val type: JIRType,
    override val lhv: JIRValue,
    override val rhv: JIRValue,
) : JIRBinaryExpr {
    override val operands: List<JIRValue>
        get() = listOf(lhv, rhv)

    override fun toString(): String = "$lhv | $rhv"

    override fun <T> accept(visitor: JIRExprVisitor<T>): T {
        return visitor.visitJIROrExpr(this)
    }
}

data class JIRRemExpr(
    override val type: JIRType,
    override val lhv: JIRValue,
    override val rhv: JIRValue,
) : JIRBinaryExpr {
    override val operands: List<JIRValue>
        get() = listOf(lhv, rhv)

    override fun toString(): String = "$lhv % $rhv"

    override fun <T> accept(visitor: JIRExprVisitor<T>): T {
        return visitor.visitJIRRemExpr(this)
    }
}

data class JIRShlExpr(
    override val type: JIRType,
    override val lhv: JIRValue,
    override val rhv: JIRValue,
) : JIRBinaryExpr {
    override val operands: List<JIRValue>
        get() = listOf(lhv, rhv)

    override fun toString(): String = "$lhv << $rhv"

    override fun <T> accept(visitor: JIRExprVisitor<T>): T {
        return visitor.visitJIRShlExpr(this)
    }
}

data class JIRShrExpr(
    override val type: JIRType,
    override val lhv: JIRValue,
    override val rhv: JIRValue,
) : JIRBinaryExpr {
    override val operands: List<JIRValue>
        get() = listOf(lhv, rhv)

    override fun toString(): String = "$lhv >> $rhv"

    override fun <T> accept(visitor: JIRExprVisitor<T>): T {
        return visitor.visitJIRShrExpr(this)
    }
}

data class JIRSubExpr(
    override val type: JIRType,
    override val lhv: JIRValue,
    override val rhv: JIRValue,
) : JIRBinaryExpr {
    override val operands: List<JIRValue>
        get() = listOf(lhv, rhv)

    override fun toString(): String = "$lhv - $rhv"

    override fun <T> accept(visitor: JIRExprVisitor<T>): T {
        return visitor.visitJIRSubExpr(this)
    }
}

data class JIRUshrExpr(
    override val type: JIRType,
    override val lhv: JIRValue,
    override val rhv: JIRValue,
) : JIRBinaryExpr {
    override val operands: List<JIRValue>
        get() = listOf(lhv, rhv)

    override fun toString(): String = "$lhv u<< $rhv"

    override fun <T> accept(visitor: JIRExprVisitor<T>): T {
        return visitor.visitJIRUshrExpr(this)
    }
}

data class JIRXorExpr(
    override val type: JIRType,
    override val lhv: JIRValue,
    override val rhv: JIRValue,
) : JIRBinaryExpr {
    override val operands: List<JIRValue>
        get() = listOf(lhv, rhv)

    override fun toString(): String = "$lhv ^ $rhv"

    override fun <T> accept(visitor: JIRExprVisitor<T>): T {
        return visitor.visitJIRXorExpr(this)
    }
}

data class JIRLengthExpr(
    override val type: JIRType,
    val array: JIRValue,
) : JIRExpr {
    override val operands: List<JIRValue>
        get() = listOf(array)

    override fun toString(): String = "$array.length"

    override fun <T> accept(visitor: JIRExprVisitor<T>): T {
        return visitor.visitJIRLengthExpr(this)
    }
}

data class JIRNegExpr(
    override val type: JIRType,
    val operand: JIRValue,
) : JIRExpr {
    override val operands: List<JIRValue>
        get() = listOf(operand)

    override fun toString(): String = "-$operand"

    override fun <T> accept(visitor: JIRExprVisitor<T>): T {
        return visitor.visitJIRNegExpr(this)
    }
}

data class JIRCastExpr(
    override val type: JIRType,
    val operand: JIRValue,
) : JIRExpr {
    override val operands: List<JIRValue>
        get() = listOf(operand)

    override fun toString(): String = "(${type.typeName}) $operand"

    override fun <T> accept(visitor: JIRExprVisitor<T>): T {
        return visitor.visitJIRCastExpr(this)
    }
}

data class JIRNewExpr(
    override val type: JIRType,
) : JIRExpr {
    override val operands: List<JIRValue>
        get() = emptyList()

    override fun toString(): String = "new ${type.typeName}"

    override fun <T> accept(visitor: JIRExprVisitor<T>): T {
        return visitor.visitJIRNewExpr(this)
    }
}

data class JIRNewArrayExpr(
    override val type: JIRType,
    val dimensions: List<JIRValue>,
) : JIRExpr {

    override val operands: List<JIRValue>
        get() = dimensions

    override fun toString(): String = "new ${arrayTypeToStringWithDimensions(type.typeName, dimensions)}"

    override fun <T> accept(visitor: JIRExprVisitor<T>): T {
        return visitor.visitJIRNewArrayExpr(this)
    }

    companion object {
        private val regexToProcessDimensions = Regex("\\[(.*?)]")

        private fun arrayTypeToStringWithDimensions(typeName: String, dimensions: List<JIRValue>): String {
            var curDim = 0
            return regexToProcessDimensions.replace(typeName) {
                "[${dimensions.getOrNull(curDim++) ?: ""}]"
            }
        }
    }
}

data class JIRInstanceOfExpr(
    override val type: JIRType,
    val operand: JIRValue,
    val targetType: JIRType,
) : JIRExpr {
    override val operands: List<JIRValue>
        get() = listOf(operand)

    override fun toString(): String = "$operand instanceof $targetType"

    override fun <T> accept(visitor: JIRExprVisitor<T>): T {
        return visitor.visitJIRInstanceOfExpr(this)
    }
}

interface JIRCallExpr : JIRExpr, CommonCallExpr {
    val method: JIRTypedMethod

    override val args: List<JIRValue>

    override val type: JIRType
        get() = method.returnType

    override val operands: List<JIRValue>
        get() = args
}

interface JIRInstanceCallExpr : JIRCallExpr, CommonInstanceCallExpr {
    override val instance: JIRValue
    val declaredMethod: JIRTypedMethod

    override val operands: List<JIRValue>
        get() = listOf(instance) + args
}

data class JIRPhiExpr(
    override val type: JIRType,
    val values: List<JIRValue>,
    val args: List<JIRArgument>,
) : JIRExpr {

    override val operands: List<JIRValue>
        get() = values

    override fun <T> accept(visitor: JIRExprVisitor<T>): T {
        return visitor.visitJIRPhiExpr(this)
    }
}

/**
 * JIRLambdaExpr is created when we can resolve the `invokedynamic` instruction.
 * When Java or Kotlin compiles a code with the lambda call, it generates
 * an `invokedynamic` instruction which returns a call cite object. When we can
 * resolve the lambda call, we create `JIRLambdaExpr` that returns a similar call cite
 * object, but stores a reference to the actual method
 */
data class JIRLambdaExpr(
    private val bsmRef: TypedMethodRef,
    val actualMethod: TypedMethodRef,
    val interfaceMethodType: BsmMethodTypeArg,
    val dynamicMethodType: BsmMethodTypeArg,
    val callSiteMethodName: String,
    val callSiteArgTypes: List<JIRType>,
    val callSiteReturnType: JIRType,
    val callSiteArgs: List<JIRValue>,
) : JIRCallExpr {

    override val method get() = bsmRef.method
    override val args get() = callSiteArgs

    override fun <T> accept(visitor: JIRExprVisitor<T>): T {
        return visitor.visitJIRLambdaExpr(this)
    }
}

data class JIRDynamicCallExpr(
    private val bsmRef: TypedMethodRef,
    val bsmArgs: List<BsmArg>,
    val callSiteMethodName: String,
    val callSiteArgTypes: List<JIRType>,
    val callSiteReturnType: JIRType,
    val callSiteArgs: List<JIRValue>,
) : JIRCallExpr {

    override val method get() = bsmRef.method
    override val args get() = callSiteArgs

    override fun <T> accept(visitor: JIRExprVisitor<T>): T {
        return visitor.visitJIRDynamicCallExpr(this)
    }
}

/**
 * `invokevirtual` and `invokeinterface` instructions of the bytecode
 * are both represented with `JIRVirtualCallExpr` for simplicity
 */
data class JIRVirtualCallExpr(
    private val methodRef: VirtualTypedMethodRef,
    override val instance: JIRValue,
    override val args: List<JIRValue>,
) : JIRInstanceCallExpr {

    override val method: JIRTypedMethod
        get() {
            return methodRef.method
        }

    override val declaredMethod: JIRTypedMethod
        get() {
            return methodRef.declaredMethod
        }

    override fun toString(): String =
        "$instance.${methodRef.name}${args.joinToString(prefix = "(", postfix = ")", separator = ", ")}"

    override fun <T> accept(visitor: JIRExprVisitor<T>): T {
        return visitor.visitJIRVirtualCallExpr(this)
    }
}

data class JIRStaticCallExpr(
    private val methodRef: TypedMethodRef,
    override val args: List<JIRValue>,
) : JIRCallExpr {

    override val method: JIRTypedMethod get() = methodRef.method

    override fun toString(): String =
        "${method.method.enclosingClass.name}.${methodRef.name}${
            args.joinToString(
                prefix = "(",
                postfix = ")",
                separator = ", "
            )
        }"

    override fun <T> accept(visitor: JIRExprVisitor<T>): T {
        return visitor.visitJIRStaticCallExpr(this)
    }
}

data class JIRSpecialCallExpr(
    private val methodRef: TypedMethodRef,
    override val instance: JIRValue,
    override val args: List<JIRValue>,
) : JIRInstanceCallExpr {

    override val method: JIRTypedMethod get() = methodRef.method

    override val declaredMethod: JIRTypedMethod
        get() = method

    override fun toString(): String =
        "$instance.${methodRef.name}${args.joinToString(prefix = "(", postfix = ")", separator = ", ")}"

    override fun <T> accept(visitor: JIRExprVisitor<T>): T {
        return visitor.visitJIRSpecialCallExpr(this)
    }
}

interface JIRValue : JIRExpr, CommonValue

interface JIRSimpleValue : JIRValue {
    override val operands: List<JIRValue>
        get() = emptyList()
}

data class JIRThis(override val type: JIRType) : JIRLocal, CommonThis {
    override val name: String
        get() = "this"

    override fun toString(): String = "this"

    override fun <T> accept(visitor: JIRExprVisitor<T>): T {
        return visitor.visitJIRThis(this)
    }
}

interface JIRLocal : JIRSimpleValue {
    val name: String
}

/**
 * @param name isn't considered in `equals` and `hashcode`
 */
data class JIRArgument(
    override val index: Int,
    override val name: String,
    override val type: JIRType,
) : JIRLocal, CommonArgument {

    companion object {
        @JvmStatic
        fun of(index: Int, name: String?, type: JIRType): JIRArgument {
            return JIRArgument(index, name ?: "arg$$index", type)
        }
    }

    override fun toString(): String = name

    override fun <T> accept(visitor: JIRExprVisitor<T>): T {
        return visitor.visitJIRArgument(this)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as JIRArgument

        if (index != other.index) return false
        if (type != other.type) return false

        return true
    }

    override fun hashCode(): Int {
        var result = index
        result = 31 * result + type.hashCode()
        return result
    }
}

/**
 * @param name isn't considered in `equals` and `hashcode`
 */
data class JIRLocalVar(
    val index: Int,
    override val name: String,
    override val type: JIRType,
) : JIRLocal {

    override fun toString(): String = name

    override fun <T> accept(visitor: JIRExprVisitor<T>): T {
        return visitor.visitJIRLocalVar(this)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as JIRLocalVar

        if (index != other.index) return false
        if (type != other.type) return false

        return true
    }

    override fun hashCode(): Int {
        var result = index
        result = 31 * result + type.hashCode()
        return result
    }
}

interface JIRComplexValue : JIRValue

data class JIRFieldRef(
    override val instance: JIRValue?,
    val field: JIRTypedField,
) : JIRComplexValue, CommonFieldRef {

    override val type: JIRType
        get() = this.field.type

    override val classField: JIRField
        get() = this.field.field

    override val operands: List<JIRValue>
        get() = listOfNotNull(instance)

    override fun toString(): String = "${instance ?: field.enclosingType.typeName}.${field.name}"

    override fun <T> accept(visitor: JIRExprVisitor<T>): T {
        return visitor.visitJIRFieldRef(this)
    }
}

data class JIRArrayAccess(
    override val array: JIRValue,
    override val index: JIRValue,
    override val type: JIRType,
) : JIRComplexValue, CommonArrayAccess {

    override val operands: List<JIRValue>
        get() = listOf(array, index)

    override fun toString(): String = "$array[$index]"

    override fun <T> accept(visitor: JIRExprVisitor<T>): T {
        return visitor.visitJIRArrayAccess(this)
    }
}

interface JIRConstant : JIRSimpleValue

interface JIRNumericConstant : JIRConstant {

    val value: Number

    fun isEqual(c: JIRNumericConstant): Boolean = c.value == value

    fun isNotEqual(c: JIRNumericConstant): Boolean = !isEqual(c)

    fun isLessThan(c: JIRNumericConstant): Boolean

    fun isLessThanOrEqual(c: JIRNumericConstant): Boolean = isLessThan(c) || isEqual(c)

    fun isGreaterThan(c: JIRNumericConstant): Boolean

    fun isGreaterThanOrEqual(c: JIRNumericConstant): Boolean = isGreaterThan(c) || isEqual(c)

    operator fun plus(c: JIRNumericConstant): JIRNumericConstant

    operator fun minus(c: JIRNumericConstant): JIRNumericConstant

    operator fun times(c: JIRNumericConstant): JIRNumericConstant

    operator fun div(c: JIRNumericConstant): JIRNumericConstant

    operator fun rem(c: JIRNumericConstant): JIRNumericConstant

    operator fun unaryMinus(): JIRNumericConstant

}

data class JIRBool(val value: Boolean, override val type: JIRType) : JIRConstant {
    override fun toString(): String = "$value"

    override fun <T> accept(visitor: JIRExprVisitor<T>): T {
        return visitor.visitJIRBool(this)
    }
}

data class JIRByte(override val value: Byte, override val type: JIRType) : JIRNumericConstant {
    override fun toString(): String = "$value"

    override fun plus(c: JIRNumericConstant): JIRNumericConstant {
        return JIRInt(value + c.value.toByte(), type)
    }

    override fun minus(c: JIRNumericConstant): JIRNumericConstant {
        return JIRInt(value - c.value.toByte(), type)
    }

    override fun times(c: JIRNumericConstant): JIRNumericConstant {
        return JIRInt(value * c.value.toByte(), type)
    }

    override fun div(c: JIRNumericConstant): JIRNumericConstant {
        return JIRInt(value / c.value.toByte(), type)
    }

    override fun rem(c: JIRNumericConstant): JIRNumericConstant {
        return JIRInt(value % c.value.toByte(), type)
    }

    override fun unaryMinus(): JIRNumericConstant {
        return JIRInt(-value, type)
    }

    override fun isLessThan(c: JIRNumericConstant): Boolean {
        return value < c.value.toByte()
    }

    override fun isGreaterThan(c: JIRNumericConstant): Boolean {
        return value > c.value.toByte()
    }

    override fun <T> accept(visitor: JIRExprVisitor<T>): T {
        return visitor.visitJIRByte(this)
    }
}

data class JIRChar(val value: Char, override val type: JIRType) : JIRConstant {
    override fun toString(): String = "$value"

    override fun <T> accept(visitor: JIRExprVisitor<T>): T {
        return visitor.visitJIRChar(this)
    }
}

data class JIRShort(override val value: Short, override val type: JIRType) : JIRNumericConstant {
    override fun toString(): String = "$value"

    override fun plus(c: JIRNumericConstant): JIRNumericConstant {
        return JIRInt(value + c.value.toShort(), type)
    }

    override fun minus(c: JIRNumericConstant): JIRNumericConstant {
        return JIRInt(value - c.value.toShort(), type)
    }

    override fun times(c: JIRNumericConstant): JIRNumericConstant {
        return JIRInt(value * c.value.toInt(), type)
    }

    override fun div(c: JIRNumericConstant): JIRNumericConstant {
        return JIRInt(value / c.value.toShort(), type)
    }

    override fun rem(c: JIRNumericConstant): JIRNumericConstant {
        return JIRInt(value % c.value.toShort(), type)
    }

    override fun unaryMinus(): JIRNumericConstant {
        return JIRInt(-value, type)
    }

    override fun isLessThan(c: JIRNumericConstant): Boolean {
        return value < c.value.toShort()
    }

    override fun isGreaterThan(c: JIRNumericConstant): Boolean {
        return value > c.value.toShort()
    }

    override fun <T> accept(visitor: JIRExprVisitor<T>): T {
        return visitor.visitJIRShort(this)
    }
}

data class JIRInt(override val value: Int, override val type: JIRType) : JIRNumericConstant {
    override fun toString(): String = "$value"

    override fun plus(c: JIRNumericConstant): JIRNumericConstant {
        return JIRInt(value + c.value.toInt(), type)
    }

    override fun minus(c: JIRNumericConstant): JIRNumericConstant {
        return JIRInt(value - c.value.toInt(), type)
    }

    override fun times(c: JIRNumericConstant): JIRNumericConstant {
        return JIRInt(value * c.value.toInt(), type)
    }

    override fun div(c: JIRNumericConstant): JIRNumericConstant {
        return JIRInt(value / c.value.toInt(), type)
    }

    override fun rem(c: JIRNumericConstant): JIRNumericConstant {
        return JIRInt(value % c.value.toInt(), type)
    }

    override fun unaryMinus(): JIRNumericConstant {
        return JIRInt(-value, type)
    }

    override fun isLessThan(c: JIRNumericConstant): Boolean {
        return value < c.value.toInt()
    }

    override fun isGreaterThan(c: JIRNumericConstant): Boolean {
        return value > c.value.toInt()
    }

    override fun <T> accept(visitor: JIRExprVisitor<T>): T {
        return visitor.visitJIRInt(this)
    }
}

data class JIRLong(override val value: Long, override val type: JIRType) : JIRNumericConstant {
    override fun toString(): String = "$value"

    override fun plus(c: JIRNumericConstant): JIRNumericConstant {
        return JIRLong(value + c.value.toLong(), type)
    }

    override fun minus(c: JIRNumericConstant): JIRNumericConstant {
        return JIRLong(value - c.value.toLong(), type)
    }

    override fun times(c: JIRNumericConstant): JIRNumericConstant {
        return JIRLong(value * c.value.toLong(), type)
    }

    override fun div(c: JIRNumericConstant): JIRNumericConstant {
        return JIRLong(value / c.value.toLong(), type)
    }

    override fun rem(c: JIRNumericConstant): JIRNumericConstant {
        return JIRLong(value % c.value.toLong(), type)
    }

    override fun unaryMinus(): JIRNumericConstant {
        return JIRLong(-value, type)
    }

    override fun isLessThan(c: JIRNumericConstant): Boolean {
        return value < c.value.toLong()
    }

    override fun isGreaterThan(c: JIRNumericConstant): Boolean {
        return value > c.value.toLong()
    }

    override fun <T> accept(visitor: JIRExprVisitor<T>): T {
        return visitor.visitJIRLong(this)
    }
}

data class JIRFloat(override val value: Float, override val type: JIRType) : JIRNumericConstant {
    override fun toString(): String = "$value"

    override fun plus(c: JIRNumericConstant): JIRNumericConstant {
        return JIRFloat(value + c.value.toFloat(), type)
    }

    override fun minus(c: JIRNumericConstant): JIRNumericConstant {
        return JIRFloat(value - c.value.toFloat(), type)
    }

    override fun times(c: JIRNumericConstant): JIRNumericConstant {
        return JIRFloat(value * c.value.toFloat(), type)
    }

    override fun div(c: JIRNumericConstant): JIRNumericConstant {
        return JIRFloat(value / c.value.toFloat(), type)
    }

    override fun rem(c: JIRNumericConstant): JIRNumericConstant {
        return JIRFloat(value % c.value.toFloat(), type)
    }

    override fun unaryMinus(): JIRNumericConstant {
        return JIRFloat(-value, type)
    }

    override fun isLessThan(c: JIRNumericConstant): Boolean {
        return value < c.value.toFloat()
    }

    override fun isGreaterThan(c: JIRNumericConstant): Boolean {
        return value > c.value.toFloat()
    }

    override fun <T> accept(visitor: JIRExprVisitor<T>): T {
        return visitor.visitJIRFloat(this)
    }
}

data class JIRDouble(override val value: Double, override val type: JIRType) : JIRNumericConstant {
    override fun toString(): String = "$value"

    override fun plus(c: JIRNumericConstant): JIRNumericConstant {
        return JIRDouble(value + c.value.toDouble(), type)
    }

    override fun minus(c: JIRNumericConstant): JIRNumericConstant {
        return JIRDouble(value.div(c.value.toDouble()), type)
    }

    override fun times(c: JIRNumericConstant): JIRNumericConstant {
        return JIRDouble(value * c.value.toDouble(), type)
    }

    override fun div(c: JIRNumericConstant): JIRNumericConstant {
        return JIRDouble(value.div(c.value.toDouble()), type)
    }

    override fun rem(c: JIRNumericConstant): JIRNumericConstant {
        return JIRDouble(value.rem(c.value.toDouble()), type)
    }

    override fun unaryMinus(): JIRNumericConstant {
        return JIRDouble(-value, type)
    }

    override fun isLessThan(c: JIRNumericConstant): Boolean {
        return value < c.value.toDouble()
    }

    override fun isGreaterThan(c: JIRNumericConstant): Boolean {
        return value > c.value.toDouble()
    }

    override fun <T> accept(visitor: JIRExprVisitor<T>): T {
        return visitor.visitJIRDouble(this)
    }
}

data class JIRNullConstant(override val type: JIRType) : JIRConstant {
    override fun toString(): String = "null"

    override fun <T> accept(visitor: JIRExprVisitor<T>): T {
        return visitor.visitJIRNullConstant(this)
    }
}

data class JIRStringConstant(val value: String, override val type: JIRType) : JIRConstant {
    override fun toString(): String = "\"$value\""

    override fun <T> accept(visitor: JIRExprVisitor<T>): T {
        return visitor.visitJIRStringConstant(this)
    }
}

/**
 * klass may be JIRClassType or JIRArrayType for constructions like byte[].class
 */
data class JIRClassConstant(val klass: JIRType, override val type: JIRType) : JIRConstant {

    override fun toString(): String = "${klass.typeName}.class"

    override fun <T> accept(visitor: JIRExprVisitor<T>): T {
        return visitor.visitJIRClassConstant(this)
    }
}

data class JIRMethodConstant(
    val method: JIRTypedMethod,
    override val type: JIRType,
) : JIRConstant {
    override fun toString(): String = "${method.method.enclosingClass.name}::${method.name}${
        method.parameters.joinToString(
            prefix = "(",
            postfix = ")",
            separator = ", "
        )
    }:${method.returnType}"

    override fun <T> accept(visitor: JIRExprVisitor<T>): T {
        return visitor.visitJIRMethodConstant(this)
    }
}

data class JIRMethodType(
    val argumentTypes: List<JIRType>,
    val returnType: JIRType,
    override val type: JIRType,
) : JIRConstant {
    override fun toString(): String = "${
        argumentTypes.joinToString(
            prefix = "(",
            postfix = ")",
            separator = ", "
        )
    }:${returnType}"

    override fun <T> accept(visitor: JIRExprVisitor<T>): T {
        return visitor.visitJIRMethodType(this)
    }
}
