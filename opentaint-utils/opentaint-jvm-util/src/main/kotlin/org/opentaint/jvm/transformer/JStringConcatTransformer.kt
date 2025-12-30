package org.opentaint.jvm.transformer

import org.opentaint.ir.api.jvm.JIRClassType
import org.opentaint.ir.api.jvm.JIRInstExtFeature
import org.opentaint.ir.api.jvm.JIRMethod
import org.opentaint.ir.api.jvm.JIRPrimitiveType
import org.opentaint.ir.api.jvm.JIRRefType
import org.opentaint.ir.api.jvm.JIRType
import org.opentaint.ir.api.jvm.JIRTypedMethod
import org.opentaint.ir.api.jvm.cfg.BsmArg
import org.opentaint.ir.api.jvm.cfg.BsmDoubleArg
import org.opentaint.ir.api.jvm.cfg.BsmFloatArg
import org.opentaint.ir.api.jvm.cfg.BsmHandle
import org.opentaint.ir.api.jvm.cfg.BsmIntArg
import org.opentaint.ir.api.jvm.cfg.BsmLongArg
import org.opentaint.ir.api.jvm.cfg.BsmMethodTypeArg
import org.opentaint.ir.api.jvm.cfg.BsmStringArg
import org.opentaint.ir.api.jvm.cfg.BsmTypeArg
import org.opentaint.ir.api.jvm.cfg.JIRAssignInst
import org.opentaint.ir.api.jvm.cfg.JIRDynamicCallExpr
import org.opentaint.ir.api.jvm.cfg.JIRInst
import org.opentaint.ir.api.jvm.cfg.JIRInstList
import org.opentaint.ir.api.jvm.cfg.JIRStaticCallExpr
import org.opentaint.ir.api.jvm.cfg.JIRStringConstant
import org.opentaint.ir.api.jvm.cfg.JIRValue
import org.opentaint.ir.api.jvm.cfg.JIRVirtualCallExpr
import org.opentaint.ir.api.jvm.ext.objectType
import org.opentaint.ir.impl.cfg.TypedStaticMethodRefImpl
import org.opentaint.ir.impl.cfg.VirtualMethodRefImpl
import org.opentaint.jvm.transformer.JSingleInstructionTransformer.BlockGenerationContext

object JStringConcatTransformer : JIRInstExtFeature {
    private const val JAVA_STRING = "java.lang.String"
    private const val STRING_CONCAT_FACTORY = "java.lang.invoke.StringConcatFactory"
    private const val STRING_CONCAT_WITH_CONSTANTS = "makeConcatWithConstants"

    fun methodIsStringConcat(method: JIRMethod): Boolean =
        STRING_CONCAT_WITH_CONSTANTS == method.name && STRING_CONCAT_FACTORY == method.enclosingClass.name

    override fun transformInstList(method: JIRMethod, list: JIRInstList<JIRInst>): JIRInstList<JIRInst> {
        val stringConcatCalls = list.mapNotNull { inst ->
            val assignInst = inst as? JIRAssignInst ?: return@mapNotNull null
            val invokeDynamicExpr = assignInst.rhv as? JIRDynamicCallExpr ?: return@mapNotNull null
            if (!methodIsStringConcat(invokeDynamicExpr.method.method)) return@mapNotNull null
            assignInst to invokeDynamicExpr
        }

        if (stringConcatCalls.isEmpty()) return list

        val stringType = method.enclosingClass.classpath
            .findTypeOrNull(JAVA_STRING) as? JIRClassType
            ?: return list

        val stringConcatMethod = stringType.declaredMethods.singleOrNull {
            !it.isStatic && it.name == "concat" && it.parameters.size == 1
        } ?: return list

        val stringConcatElements = stringConcatCalls.mapNotNull { (assign, expr) ->
            val recipe = (expr.bsmArgs.lastOrNull() as? BsmStringArg)?.value ?: return@mapNotNull null
            val elements = parseStringConcatRecipe(
                stringType, recipe, expr.bsmArgs.dropLast(1).asReversed(),
                expr.callSiteArgs, expr.callSiteArgTypes
            ) ?: return@mapNotNull null

            assign to elements
        }

        if (stringConcatElements.isEmpty()) return list

        val transformer = JSingleInstructionTransformer(list)
        for ((assignment, concatElements) in stringConcatElements) {
            transformer.generateReplacementBlock(assignment) {
                generateConcatBlock(stringType, stringConcatMethod, assignment.lhv, concatElements)
            }
        }

        return transformer.buildInstList()
    }

    private fun BlockGenerationContext.generateConcatBlock(
        stringType: JIRClassType,
        stringConcatMethod: JIRTypedMethod,
        resultVariable: JIRValue,
        elements: List<StringConcatElement>
    ) {
        if (elements.isEmpty()) {
            addInstruction { loc ->
                JIRAssignInst(loc, resultVariable, JIRStringConstant("", stringType))
            }
            return
        }

        val elementsIter = elements.iterator()
        var current = elementStringValue(stringType, elementsIter.next())
        while (elementsIter.hasNext()) {
            val element = elementStringValue(stringType, elementsIter.next())
            current = generateStringConcat(stringType, stringConcatMethod, current, element)
        }

        addInstruction { loc ->
            JIRAssignInst(loc, resultVariable, current)
        }
    }

    private fun BlockGenerationContext.elementStringValue(
        stringType: JIRClassType,
        element: StringConcatElement
    ): JIRValue = when (element) {
        is StringConcatElement.StringElement -> element.value
        is StringConcatElement.OtherElement -> {
            val value = nextLocalVar("%str_val", stringType)
            val methodRef = element.toStringTransformer.staticMethodRef()
            val callExpr = JIRStaticCallExpr(methodRef, listOf(element.value))
            addInstruction { loc ->
                JIRAssignInst(loc, value, callExpr)
            }
            value
        }
    }

    private fun BlockGenerationContext.generateStringConcat(
        stringType: JIRClassType,
        stringConcatMethod: JIRTypedMethod,
        first: JIRValue,
        second: JIRValue
    ): JIRValue {
        val value = nextLocalVar("%str", stringType)
        val methodRef = stringConcatMethod.virtualMethodRef(stringType)
        val callExpr = JIRVirtualCallExpr(methodRef, first, listOf(second))
        addInstruction { loc ->
            JIRAssignInst(loc, value, callExpr)
        }
        return value
    }

    private fun JIRTypedMethod.virtualMethodRef(stringType: JIRClassType) =
        VirtualMethodRefImpl.of(stringType, this)

    private fun JIRTypedMethod.staticMethodRef() = TypedStaticMethodRefImpl(
        enclosingType as JIRClassType,
        name,
        method.parameters.map { it.type },
        method.returnType
    )

    private sealed interface StringConcatElement {
        data class StringElement(val value: JIRValue) : StringConcatElement
        data class OtherElement(val value: JIRValue, val toStringTransformer: JIRTypedMethod) : StringConcatElement
    }

    private fun parseStringConcatRecipe(
        stringType: JIRClassType,
        recipe: String,
        bsmArgs: List<BsmArg>,
        callArgs: List<JIRValue>,
        callArgTypes: List<JIRType>
    ): List<StringConcatElement>? {
        val elements = mutableListOf<StringConcatElement>()

        val acc = StringBuilder()

        var constCount = 0
        var argsCount = 0

        for (recipeCh in recipe) {
            when (recipeCh) {
                '\u0002' -> {
                    // Accumulate constant args along with any constants encoded
                    // into the recipe
                    val constant = bsmArgs.getOrNull(constCount++) ?: return null

                    val constantValue = when (constant) {
                        is BsmDoubleArg -> constant.value.toString()
                        is BsmFloatArg -> constant.value.toString()
                        is BsmIntArg -> constant.value.toString()
                        is BsmLongArg -> constant.value.toString()
                        is BsmStringArg -> constant.value
                        is BsmHandle,
                        is BsmMethodTypeArg,
                        is BsmTypeArg -> return null
                    }

                    acc.append(constantValue)
                }

                '\u0001' -> {
                    // Flush any accumulated characters into a constant
                    if (acc.isNotEmpty()) {
                        elements += StringConcatElement.StringElement(
                            JIRStringConstant(acc.toString(), stringType)
                        )
                        acc.setLength(0)
                    }

                    val argValue = callArgs.getOrNull(argsCount) ?: return null
                    val valueType = callArgTypes.getOrNull(argsCount) ?: return null
                    argsCount++

                    val argElement = valueStringElement(stringType, argValue, valueType) ?: return null
                    elements.add(argElement)
                }

                else -> {
                    // Not a special character, this is a constant embedded into
                    // the recipe itself.
                    acc.append(recipeCh)
                }
            }
        }

        // Flush the remaining characters as constant:
        if (acc.isNotEmpty()) {
            elements += StringConcatElement.StringElement(
                JIRStringConstant(acc.toString(), stringType)
            )
        }

        return elements
    }

    private fun valueStringElement(stringType: JIRClassType, value: JIRValue, valueType: JIRType): StringConcatElement? =
        when (valueType) {
            is JIRPrimitiveType -> {
                val valueOfMethod = stringType.findValueOfMethod(valueType)
                valueOfMethod?.let { StringConcatElement.OtherElement(value, it) }
            }

            stringType -> StringConcatElement.StringElement(value)

            is JIRRefType -> {
                val valueOfMethod = stringType.findValueOfMethod(stringType.classpath.objectType)
                valueOfMethod?.let { StringConcatElement.OtherElement(value, it) }
            }

            else -> null
        }

    private fun JIRClassType.findValueOfMethod(argumentType: JIRType): JIRTypedMethod? =
        declaredMethods.singleOrNull {
            it.isStatic && it.name == "valueOf" && it.parameters.size == 1 && it.parameters.first().type == argumentType
        }
}
