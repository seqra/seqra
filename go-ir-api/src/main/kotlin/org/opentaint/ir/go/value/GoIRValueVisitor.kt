package org.opentaint.ir.go.value

interface GoIRValueVisitor<out T> {
    fun visitConst(value: GoIRConstValue): T
    fun visitParameter(value: GoIRParameterValue): T
    fun visitFreeVar(value: GoIRFreeVarValue): T
    fun visitGlobal(value: GoIRGlobalValue): T
    fun visitFunction(value: GoIRFunctionValue): T
    fun visitBuiltin(value: GoIRBuiltinValue): T
    fun visitRegister(value: GoIRRegister): T

    interface Default<out T> : GoIRValueVisitor<T> {
        fun defaultVisitValue(value: GoIRValue): T

        override fun visitConst(value: GoIRConstValue) = defaultVisitValue(value)
        override fun visitParameter(value: GoIRParameterValue) = defaultVisitValue(value)
        override fun visitFreeVar(value: GoIRFreeVarValue) = defaultVisitValue(value)
        override fun visitGlobal(value: GoIRGlobalValue) = defaultVisitValue(value)
        override fun visitFunction(value: GoIRFunctionValue) = defaultVisitValue(value)
        override fun visitBuiltin(value: GoIRBuiltinValue) = defaultVisitValue(value)
        override fun visitRegister(value: GoIRRegister) = defaultVisitValue(value)
    }
}
