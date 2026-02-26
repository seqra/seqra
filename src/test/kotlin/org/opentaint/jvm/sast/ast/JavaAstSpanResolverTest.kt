package org.opentaint.jvm.sast.ast

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.opentaint.ir.api.jvm.cfg.JIRArrayAccess
import org.opentaint.ir.api.jvm.cfg.JIRAssignInst
import org.opentaint.ir.api.jvm.cfg.JIRCallExpr
import org.opentaint.ir.api.jvm.cfg.JIRCallInst
import org.opentaint.ir.api.jvm.cfg.JIRFieldRef
import org.opentaint.ir.api.jvm.cfg.JIRNullConstant
import org.opentaint.ir.api.jvm.cfg.JIRReturnInst
import org.opentaint.ir.api.jvm.cfg.JIRStringConstant
import org.opentaint.ir.api.jvm.ext.cfg.callExpr
import org.opentaint.jvm.sast.sarif.LocationType

class JavaAstSpanResolverTest : AbstractAstSpanResolverTest() {

    override val sourceFileExtension = "java"

    private lateinit var resolver: JavaAstSpanResolver

    @BeforeAll
    fun initResolver() {
        resolver = JavaAstSpanResolver(traits)
    }

    companion object {
        private const val SAMPLE_PACKAGE = "test.samples"
        private const val SAMPLE_FQN = "$SAMPLE_PACKAGE.SpanResolverSample"
        private const val CONSTRUCTOR_SAMPLE_FQN = "$SAMPLE_PACKAGE.ConstructorSample"
        private const val STATIC_SAMPLE_FQN = "$SAMPLE_PACKAGE.StaticMethodSample"
        private const val ANNOTATED_SAMPLE_FQN = "$SAMPLE_PACKAGE.AnnotatedSample"
    }

    @Test
    fun `resolve span for assignment with method call`() {
        val sourcePath = getSourcePath(SAMPLE_FQN)
        val assignInsts = getInstructionsOfType<JIRAssignInst>(SAMPLE_FQN, "assignmentWithCall")

        val getValueAssign = assignInsts.find { inst ->
            val rhv = inst.rhv
            rhv is JIRCallExpr && rhv.method.method.name == "getValue"
        }

        checkNotNull(getValueAssign) { "Assignment with getValue() call not found" }

        val location = createIntermediateLocation(getValueAssign)
        val span = resolver.computeSpan(sourcePath, location)

        val expectedSpan = parseSpanMarker(sourcePath, "s1")
        assertSpanMatchesMarker(span, expectedSpan)
    }

    @Test
    fun `resolve span for field access read`() {
        val sourcePath = getSourcePath(SAMPLE_FQN)
        val assignInsts = getInstructionsOfType<JIRAssignInst>(SAMPLE_FQN, "fieldAccess")

        val fieldReadAssign = assignInsts.find { inst ->
            inst.rhv is JIRFieldRef
        }

        checkNotNull(fieldReadAssign) { "Field read assignment not found" }

        val location = createIntermediateLocation(fieldReadAssign)
        val span = resolver.computeSpan(sourcePath, location)

        val expectedSpan = parseSpanMarker(sourcePath, "fieldRead")
        assertSpanMatchesMarker(span, expectedSpan)
    }

    @Test
    fun `resolve span for field access write`() {
        val sourcePath = getSourcePath(SAMPLE_FQN)
        val assignInsts = getInstructionsOfType<JIRAssignInst>(SAMPLE_FQN, "fieldAccess")

        val fieldWriteAssign = assignInsts.find { inst ->
            inst.lhv is JIRFieldRef
        }

        checkNotNull(fieldWriteAssign) { "Field write assignment not found" }

        val location = createIntermediateLocation(fieldWriteAssign)
        val span = resolver.computeSpan(sourcePath, location)

        val expectedSpan = parseSpanMarker(sourcePath, "fieldWrite")
        assertSpanMatchesMarker(span, expectedSpan)
    }

    @Test
    fun `resolve span for array read access`() {
        val sourcePath = getSourcePath(SAMPLE_FQN)
        val assignInsts = getInstructionsOfType<JIRAssignInst>(SAMPLE_FQN, "arrayAccess")

        val arrayReadAssign = assignInsts.find { inst ->
            inst.rhv is JIRArrayAccess
        }

        checkNotNull(arrayReadAssign) { "Array read assignment not found" }

        val location = createIntermediateLocation(arrayReadAssign)
        val span = resolver.computeSpan(sourcePath, location)

        val expectedSpan = parseSpanMarker(sourcePath, "arrayRead")
        assertSpanMatchesMarker(span, expectedSpan)
    }

    @Test
    fun `resolve span for array write access`() {
        val sourcePath = getSourcePath(SAMPLE_FQN)
        val assignInsts = getInstructionsOfType<JIRAssignInst>(SAMPLE_FQN, "arrayAccess")

        val arrayWriteAssign = assignInsts.find { inst ->
            inst.lhv is JIRArrayAccess
        }

        checkNotNull(arrayWriteAssign) { "Array write assignment not found" }

        val location = createIntermediateLocation(arrayWriteAssign)
        val span = resolver.computeSpan(sourcePath, location)

        val expectedSpan = parseSpanMarker(sourcePath, "arrayWrite")
        assertSpanMatchesMarker(span, expectedSpan)
    }

    @Test
    fun `resolve span for object creation`() {
        val sourcePath = getSourcePath(SAMPLE_FQN)
        val callInsts = getInstructionsOfType<JIRCallInst>(SAMPLE_FQN, "createObject")

        val constructorCall = callInsts.find {
            it.callExpr.method.method.isConstructor
        }

        checkNotNull(constructorCall) { "Constructor call not found" }

        val location = createIntermediateLocation(constructorCall)
        val span = resolver.computeSpan(sourcePath, location)

        val expectedSpan = parseSpanMarker(sourcePath, "newObject")
        assertSpanMatchesMarker(span, expectedSpan)
    }

    @Test
    fun `resolve span for return statement`() {
        val sourcePath = getSourcePath(SAMPLE_FQN)
        val returnInsts = getInstructionsOfType<JIRReturnInst>(SAMPLE_FQN, "returnStatement")

        check(returnInsts.isNotEmpty()) { "Return instruction not found" }

        val returnInst = returnInsts.first()
        val location = createIntermediateLocation(returnInst)
        val span = resolver.computeSpan(sourcePath, location)

        val expectedSpan = parseSpanMarker(sourcePath, "return")
        assertSpanMatchesMarker(span, expectedSpan)
    }

    @Test
    fun `resolve span for chained method call`() {
        val sourcePath = getSourcePath(SAMPLE_FQN)
        val callInsts = getInstructionsOfType<JIRAssignInst>(SAMPLE_FQN, "chainedCall")

        val toUpperCaseCall = callInsts.find {
            it.callExpr?.method?.method?.name == "toUpperCase"
        }

        checkNotNull(toUpperCaseCall) { "toUpperCase() call not found in chain" }

        val location = createIntermediateLocation(toUpperCaseCall)
        val span = resolver.computeSpan(sourcePath, location)

        val expectedSpan = parseSpanMarker(sourcePath, "chainedCall")
        assertSpanMatchesMarker(span, expectedSpan)
    }

    @Test
    fun `resolve span for constructor declaration`() {
        val sourcePath = getSourcePath(CONSTRUCTOR_SAMPLE_FQN)
        val constructorClass = findClass(CONSTRUCTOR_SAMPLE_FQN)

        val noArgConstructor = constructorClass.declaredMethods.find {
            it.isConstructor && it.parameters.isEmpty()
        }

        checkNotNull(noArgConstructor) { "No-arg constructor not found" }

        val firstInst = noArgConstructor.instList.firstOrNull()
        checkNotNull(firstInst) { "No instructions found in constructor" }

        val location = createIntermediateLocation(firstInst, LocationType.RuleMethodEntry)
        val span = resolver.computeSpan(sourcePath, location)

        val expectedSpan = parseSpanMarker(sourcePath, "noArgConstructor")
        assertSpanMatchesMarker(span, expectedSpan)
    }

    @Test
    fun `resolve span for static method call`() {
        val sourcePath = getSourcePath(STATIC_SAMPLE_FQN)
        val callInsts = getInstructionsOfType<JIRAssignInst>(STATIC_SAMPLE_FQN, "staticMethod")

        val toLowerCaseCall = callInsts.find {
            it.callExpr?.method?.method?.name == "toLowerCase"
        }

        checkNotNull(toLowerCaseCall) { "toLowerCase() call not found" }

        val location = createIntermediateLocation(toLowerCaseCall)
        val span = resolver.computeSpan(sourcePath, location)

        val expectedSpan = parseSpanMarker(sourcePath, "staticCall")
        assertSpanMatchesMarker(span, expectedSpan)
    }

    @Test
    fun `resolve parameter name for method argument`() {
        val sourcePath = getSourcePath(STATIC_SAMPLE_FQN)
        val method = findMethod(STATIC_SAMPLE_FQN, "staticMethodWithArgs")
        val firstInst = method.instList.first()

        val paramName = resolver.getParameterName(sourcePath, firstInst, 0)

        assertEquals("arg", paramName, "Parameter name should be 'arg'")
    }

    @Test
    fun `resolve span for local variable declaration`() {
        val sourcePath = getSourcePath(SAMPLE_FQN)
        val assignInsts = getInstructionsOfType<JIRAssignInst>(SAMPLE_FQN, "localVariableDeclaration")

        val firstAssign = assignInsts.firstOrNull()
        checkNotNull(firstAssign) { "Local variable assignment not found" }

        val location = createIntermediateLocation(firstAssign)
        val span = resolver.computeSpan(sourcePath, location)

        val expectedSpan = parseSpanMarker(sourcePath, "localVar")
        assertSpanMatchesMarker(span, expectedSpan)
    }

    @Test
    fun `resolve span for simple method call`() {
        val sourcePath = getSourcePath(SAMPLE_FQN)
        val callInsts = getInstructionsOfType<JIRAssignInst>(SAMPLE_FQN, "simpleMethodCall")

        val toUpperCaseCall = callInsts.find {
            it.callExpr?.method?.method?.name == "toUpperCase"
        }

        checkNotNull(toUpperCaseCall) { "toUpperCase() call not found" }

        val location = createIntermediateLocation(toUpperCaseCall)
        val span = resolver.computeSpan(sourcePath, location)

        val expectedSpan = parseSpanMarker(sourcePath, "simpleCall")
        assertSpanMatchesMarker(span, expectedSpan)
    }

    @Test
    fun `resolve span for multiple statements on line`() {
        val sourcePath = getSourcePath(SAMPLE_FQN)
        val method = findMethod(SAMPLE_FQN, "multipleStatementsOnLine")

        val multiLineInst = method.instList.firstOrNull()
        checkNotNull(multiLineInst) { "No instructions found in multipleStatementsOnLine method" }

        val location = createIntermediateLocation(multiLineInst, LocationType.Multiple)
        val span = resolver.computeSpan(sourcePath, location)

        val expectedSpan = parseSpanMarker(sourcePath, "multi")
        assertSpanMatchesMarker(span, expectedSpan)
    }

    @Test
    fun `resolve span for method entry`() {
        val sourcePath = getSourcePath(SAMPLE_FQN)
        val method = findMethod(SAMPLE_FQN, "getValue")

        val firstInst = method.instList.firstOrNull()
        checkNotNull(firstInst) { "No instructions found in getValue method" }

        val location = createIntermediateLocation(firstInst, LocationType.RuleMethodEntry)
        val span = resolver.computeSpan(sourcePath, location)

        val expectedSpan = parseSpanMarker(sourcePath, "methodEntry")
        assertSpanMatchesMarker(span, expectedSpan)
    }

    @Test
    fun `resolve span for constructor entry`() {
        val sourcePath = getSourcePath(CONSTRUCTOR_SAMPLE_FQN)
        val constructorClass = findClass(CONSTRUCTOR_SAMPLE_FQN)

        val oneArgConstructor = constructorClass.declaredMethods.find {
            it.isConstructor && it.parameters.size == 1
        }

        checkNotNull(oneArgConstructor) { "One-arg constructor not found" }

        val firstInst = oneArgConstructor.instList.firstOrNull()
        checkNotNull(firstInst) { "No instructions found in constructor" }

        val location = createIntermediateLocation(firstInst, LocationType.RuleMethodEntry)
        val span = resolver.computeSpan(sourcePath, location)

        val expectedSpan = parseSpanMarker(sourcePath, "constructorEntry")
        assertSpanMatchesMarker(span, expectedSpan)
    }

    @Test
    fun `resolve span for static method entry`() {
        val sourcePath = getSourcePath(STATIC_SAMPLE_FQN)
        val method = findMethod(STATIC_SAMPLE_FQN, "staticMethodWithArgs")

        val firstInst = method.instList.firstOrNull()
        checkNotNull(firstInst) { "No instructions found in staticMethodWithArgs" }

        val location = createIntermediateLocation(firstInst, LocationType.RuleMethodEntry)
        val span = resolver.computeSpan(sourcePath, location)

        val expectedSpan = parseSpanMarker(sourcePath, "staticMethodEntry")
        assertSpanMatchesMarker(span, expectedSpan)
    }

    @Test
    fun `resolve span for method exit`() {
        val sourcePath = getSourcePath(SAMPLE_FQN)
        val method = findMethod(SAMPLE_FQN, "getValue")
        val returnInst = method.instList.filterIsInstance<JIRReturnInst>().firstOrNull()

        checkNotNull(returnInst) { "Return instruction not found in getValue" }

        val location = createMethodExitLocation(returnInst)
        val span = resolver.computeSpan(sourcePath, location)

        val expectedSpan = parseSpanMarker(sourcePath, "methodExit")
        assertSpanMatchesMarker(span, expectedSpan)
    }

    @Test
    fun `resolve span for constructor exit`() {
        val sourcePath = getSourcePath(CONSTRUCTOR_SAMPLE_FQN)
        val constructorClass = findClass(CONSTRUCTOR_SAMPLE_FQN)

        val oneArgConstructor = constructorClass.declaredMethods.find {
            it.isConstructor && it.parameters.size == 1
        }

        checkNotNull(oneArgConstructor) { "One-arg constructor not found" }

        val returnInst = oneArgConstructor.instList.filterIsInstance<JIRReturnInst>().firstOrNull()
        checkNotNull(returnInst) { "Return instruction not found in constructor" }

        val location = createMethodExitLocation(returnInst)
        val span = resolver.computeSpan(sourcePath, location)

        val expectedSpan = parseSpanMarker(sourcePath, "constructorExit")
        assertSpanMatchesMarker(span, expectedSpan)
    }

    @Test
    fun `resolve span for static method exit`() {
        val sourcePath = getSourcePath(STATIC_SAMPLE_FQN)
        val method = findMethod(STATIC_SAMPLE_FQN, "staticMethodWithArgs")
        val returnInst = method.instList.filterIsInstance<JIRReturnInst>().firstOrNull()

        checkNotNull(returnInst) { "Return instruction not found in staticMethodWithArgs" }

        val location = createMethodExitLocation(returnInst)
        val span = resolver.computeSpan(sourcePath, location)

        val expectedSpan = parseSpanMarker(sourcePath, "staticMethodExit")
        assertSpanMatchesMarker(span, expectedSpan)
    }

    @Test
    fun `resolve spans for full method - entry, all instructions, and exit`() {
        val sourcePath = getSourcePath(SAMPLE_FQN)
        val method = findMethod(SAMPLE_FQN, "fullMethodTest")
        val instructions = method.instList

        val expectedSpans = mapOf(
            "entry" to findInstruction<JIRAssignInst>(instructions) { it.rhv is JIRCallExpr && (it.rhv as JIRCallExpr).method.method.name == "trim" },
            "local" to findInstruction<JIRAssignInst>(instructions) { it.rhv is JIRCallExpr && (it.rhv as JIRCallExpr).method.method.name == "trim" },
            "fieldWrite" to findInstruction<JIRAssignInst>(instructions) { it.lhv is JIRFieldRef },
            "fieldRead" to findInstruction<JIRAssignInst>(instructions) { it.rhv is JIRFieldRef },
            "call" to findInstruction<JIRAssignInst>(instructions) { it.rhv is JIRCallExpr && (it.rhv as JIRCallExpr).method.method.name == "toUpperCase" },
            "return" to findInstruction<JIRReturnInst>(instructions) { true },
            "exit" to findInstruction<JIRReturnInst>(instructions) { true }
        )

        for ((markerName, inst) in expectedSpans) {
            checkNotNull(inst) { "Instruction for marker 'full\$$markerName' not found" }

            val location = when (markerName) {
                "entry" -> createIntermediateLocation(inst, LocationType.RuleMethodEntry)
                "exit" -> createMethodExitLocation(inst as JIRReturnInst)
                else -> createIntermediateLocation(inst)
            }

            val span = resolver.computeSpan(sourcePath, location)
            val expectedSpan = parseSpanMarker(sourcePath, "full\$$markerName")

            assertSpanMatchesMarker(span, expectedSpan)
        }
    }

    @Test
    fun `resolve span for annotated constructor entry and exit`() {
        val sourcePath = getSourcePath(ANNOTATED_SAMPLE_FQN)
        val clazz = findClass(ANNOTATED_SAMPLE_FQN)

        val constructor = clazz.declaredMethods.find { it.isConstructor && it.parameters.isEmpty() }
        checkNotNull(constructor) { "Annotated no-arg constructor not found" }

        val firstInst = constructor.instList.firstOrNull()
        checkNotNull(firstInst) { "No instructions in constructor" }

        val entryLocation = createIntermediateLocation(firstInst, LocationType.RuleMethodEntry)
        val entrySpan = resolver.computeSpan(sourcePath, entryLocation)
        assertSpanMatchesMarker(entrySpan, parseSpanMarker(sourcePath, "annotatedConstructor"))

        val returnInst = constructor.instList.filterIsInstance<JIRReturnInst>().first()
        val exitLocation = createMethodExitLocation(returnInst)
        val exitSpan = resolver.computeSpan(sourcePath, exitLocation)
        assertSpanMatchesMarker(exitSpan, parseSpanMarker(sourcePath, "annotatedConstructorExit"))
    }

    @Test
    fun `resolve span for constructor with multiple annotations`() {
        val sourcePath = getSourcePath(ANNOTATED_SAMPLE_FQN)
        val clazz = findClass(ANNOTATED_SAMPLE_FQN)

        val constructor = clazz.declaredMethods.find { it.isConstructor && it.parameters.size == 1 }
        checkNotNull(constructor) { "One-arg constructor not found" }

        val firstInst = constructor.instList.firstOrNull()
        checkNotNull(firstInst) { "No instructions in constructor" }

        val entryLocation = createIntermediateLocation(firstInst, LocationType.RuleMethodEntry)
        val entrySpan = resolver.computeSpan(sourcePath, entryLocation)
        assertSpanMatchesMarker(entrySpan, parseSpanMarker(sourcePath, "multiAnnotationConstructor"))

        val returnInst = constructor.instList.filterIsInstance<JIRReturnInst>().first()
        val exitLocation = createMethodExitLocation(returnInst)
        val exitSpan = resolver.computeSpan(sourcePath, exitLocation)
        assertSpanMatchesMarker(exitSpan, parseSpanMarker(sourcePath, "multiAnnotationConstructorExit"))
    }

    @Test
    fun `resolve span for annotated method with type parameters`() {
        val sourcePath = getSourcePath(ANNOTATED_SAMPLE_FQN)
        val method = findMethod(ANNOTATED_SAMPLE_FQN, "transform")

        val firstInst = method.instList.firstOrNull()
        checkNotNull(firstInst) { "No instructions in transform method" }

        val entryLocation = createIntermediateLocation(firstInst, LocationType.RuleMethodEntry)
        val entrySpan = resolver.computeSpan(sourcePath, entryLocation)
        assertSpanMatchesMarker(entrySpan, parseSpanMarker(sourcePath, "annotatedMethod"))

        val returnInst = method.instList.filterIsInstance<JIRReturnInst>().first()
        val exitLocation = createMethodExitLocation(returnInst)
        val exitSpan = resolver.computeSpan(sourcePath, exitLocation)
        assertSpanMatchesMarker(exitSpan, parseSpanMarker(sourcePath, "annotatedMethodExit"))
    }

    @Test
    fun `resolve span for method with bounded type parameter`() {
        val sourcePath = getSourcePath(ANNOTATED_SAMPLE_FQN)
        val method = findMethod(ANNOTATED_SAMPLE_FQN, "process")

        val firstInst = method.instList.firstOrNull()
        checkNotNull(firstInst) { "No instructions in process method" }

        val entryLocation = createIntermediateLocation(firstInst, LocationType.RuleMethodEntry)
        val entrySpan = resolver.computeSpan(sourcePath, entryLocation)
        assertSpanMatchesMarker(entrySpan, parseSpanMarker(sourcePath, "typeParamMethod"))

        val returnInst = method.instList.filterIsInstance<JIRReturnInst>().first()
        val exitLocation = createMethodExitLocation(returnInst)
        val exitSpan = resolver.computeSpan(sourcePath, exitLocation)
        assertSpanMatchesMarker(exitSpan, parseSpanMarker(sourcePath, "typeParamMethodExit"))
    }

    @Test
    fun `resolve span for local variable with annotation`() {
        val sourcePath = getSourcePath(ANNOTATED_SAMPLE_FQN)
        val assignInsts = getInstructionsOfType<JIRAssignInst>(ANNOTATED_SAMPLE_FQN, "localAnnotations")

        val unusedAssign = assignInsts.find {
            val rhv = it.rhv
            rhv is JIRStringConstant && rhv.value == "test"
        }
        checkNotNull(unusedAssign) { "Annotated local variable assignment not found" }

        val location = createIntermediateLocation(unusedAssign)
        val span = resolver.computeSpan(sourcePath, location)
        assertSpanMatchesMarker(span, parseSpanMarker(sourcePath, "annotatedLocal"))
    }

    @Test
    fun `resolve span for local variable with multiple annotations`() {
        val sourcePath = getSourcePath(ANNOTATED_SAMPLE_FQN)
        val assignInsts = getInstructionsOfType<JIRAssignInst>(ANNOTATED_SAMPLE_FQN, "localAnnotations")

        val listAssign = assignInsts.find {
            it.rhv is JIRNullConstant
        }
        checkNotNull(listAssign) { "Multi-annotated local variable assignment not found" }

        val location = createIntermediateLocation(listAssign)
        val span = resolver.computeSpan(sourcePath, location)
        assertSpanMatchesMarker(span, parseSpanMarker(sourcePath, "multiAnnotatedLocal"))
    }

    @Test
    fun `resolve span for method with complex generic signature`() {
        val sourcePath = getSourcePath(ANNOTATED_SAMPLE_FQN)
        val method = findMethod(ANNOTATED_SAMPLE_FQN, "complexMethod")

        val firstInst = method.instList.firstOrNull()
        checkNotNull(firstInst) { "No instructions in complexMethod" }

        val entryLocation = createIntermediateLocation(firstInst, LocationType.RuleMethodEntry)
        val entrySpan = resolver.computeSpan(sourcePath, entryLocation)
        assertSpanMatchesMarker(entrySpan, parseSpanMarker(sourcePath, "complexSignature"))

        val returnInst = method.instList.filterIsInstance<JIRReturnInst>().first()
        val exitLocation = createMethodExitLocation(returnInst)
        val exitSpan = resolver.computeSpan(sourcePath, exitLocation)
        assertSpanMatchesMarker(exitSpan, parseSpanMarker(sourcePath, "complexSignatureExit"))
    }

    @Test
    fun `resolve span for method call with no arguments`() {
        val sourcePath = getSourcePath(SAMPLE_FQN)
        val callInsts = getInstructionsOfType<JIRCallInst>(SAMPLE_FQN, "methodCallNoArgs")

        val noArgsCall = callInsts.find {
            it.callExpr.method.method.name == "noArgsHelper"
        }

        checkNotNull(noArgsCall) { "noArgsHelper() call not found" }

        val location = createIntermediateLocation(noArgsCall)
        val span = resolver.computeSpan(sourcePath, location)

        val expectedSpan = parseSpanMarker(sourcePath, "callNoArgs")
        assertSpanMatchesMarker(span, expectedSpan)
    }

    @Test
    fun `resolve span for method call with one argument`() {
        val sourcePath = getSourcePath(SAMPLE_FQN)
        val callInsts = getInstructionsOfType<JIRCallInst>(SAMPLE_FQN, "methodCallOneArg")

        val oneArgCall = callInsts.find {
            it.callExpr.method.method.name == "oneArgHelper"
        }

        checkNotNull(oneArgCall) { "oneArgHelper() call not found" }

        val location = createIntermediateLocation(oneArgCall)
        val span = resolver.computeSpan(sourcePath, location)

        val expectedSpan = parseSpanMarker(sourcePath, "callOneArg")
        assertSpanMatchesMarker(span, expectedSpan)
    }

    @Test
    fun `resolve span for method call with two arguments`() {
        val sourcePath = getSourcePath(SAMPLE_FQN)
        val callInsts = getInstructionsOfType<JIRCallInst>(SAMPLE_FQN, "methodCallTwoArgs")

        val twoArgsCall = callInsts.find {
            it.callExpr.method.method.name == "twoArgsHelper"
        }

        checkNotNull(twoArgsCall) { "twoArgsHelper() call not found" }

        val location = createIntermediateLocation(twoArgsCall)
        val span = resolver.computeSpan(sourcePath, location)

        val expectedSpan = parseSpanMarker(sourcePath, "callTwoArgs")
        assertSpanMatchesMarker(span, expectedSpan)
    }

    @Test
    fun `resolve span for method call with three arguments`() {
        val sourcePath = getSourcePath(SAMPLE_FQN)
        val callInsts = getInstructionsOfType<JIRCallInst>(SAMPLE_FQN, "methodCallThreeArgs")

        val threeArgsCall = callInsts.find {
            it.callExpr.method.method.name == "threeArgsHelper"
        }

        checkNotNull(threeArgsCall) { "threeArgsHelper() call not found" }

        val location = createIntermediateLocation(threeArgsCall)
        val span = resolver.computeSpan(sourcePath, location)

        val expectedSpan = parseSpanMarker(sourcePath, "callThreeArgs")
        assertSpanMatchesMarker(span, expectedSpan)
    }

    @Test
    fun `resolve span for method call with varargs`() {
        val sourcePath = getSourcePath(SAMPLE_FQN)
        val callInsts = getInstructionsOfType<JIRCallInst>(SAMPLE_FQN, "methodCallVarargs")

        val varargsCall = callInsts.find {
            it.callExpr.method.method.name == "varargsHelper"
        }

        checkNotNull(varargsCall) { "varargsHelper() call not found" }

        val location = createIntermediateLocation(varargsCall)
        val span = resolver.computeSpan(sourcePath, location)

        val expectedSpan = parseSpanMarker(sourcePath, "callVarargs")
        assertSpanMatchesMarker(span, expectedSpan)
    }

    // Tests for method calls on local variable: a.foo(...)

    @Test
    fun `resolve span for local var method call with no arguments`() {
        val sourcePath = getSourcePath(SAMPLE_FQN)
        val callInsts = getInstructionsOfType<JIRAssignInst>(SAMPLE_FQN, "localVarCallNoArgs")

        val call = callInsts.find { it.callExpr?.method?.method?.name == "length" }
        checkNotNull(call) { "length() call not found" }

        val location = createIntermediateLocation(call)
        val span = resolver.computeSpan(sourcePath, location)

        val expectedSpan = parseSpanMarker(sourcePath, "localVarCallNoArgs")
        assertSpanMatchesMarker(span, expectedSpan)
    }

    @Test
    fun `resolve span for local var method call with one argument`() {
        val sourcePath = getSourcePath(SAMPLE_FQN)
        val callInsts = getInstructionsOfType<JIRAssignInst>(SAMPLE_FQN, "localVarCallOneArg")

        val call = callInsts.find { it.callExpr?.method?.method?.name == "charAt" }
        checkNotNull(call) { "charAt() call not found" }

        val location = createIntermediateLocation(call)
        val span = resolver.computeSpan(sourcePath, location)

        val expectedSpan = parseSpanMarker(sourcePath, "localVarCallOneArg")
        assertSpanMatchesMarker(span, expectedSpan)
    }

    @Test
    fun `resolve span for local var method call with two arguments`() {
        val sourcePath = getSourcePath(SAMPLE_FQN)
        val callInsts = getInstructionsOfType<JIRAssignInst>(SAMPLE_FQN, "localVarCallTwoArgs")

        val call = callInsts.find { it.callExpr?.method?.method?.name == "substring" }
        checkNotNull(call) { "substring() call not found" }

        val location = createIntermediateLocation(call)
        val span = resolver.computeSpan(sourcePath, location)

        val expectedSpan = parseSpanMarker(sourcePath, "localVarCallTwoArgs")
        assertSpanMatchesMarker(span, expectedSpan)
    }

    @Test
    fun `resolve span for local var method call with three arguments`() {
        val sourcePath = getSourcePath(SAMPLE_FQN)
        val callInsts = getInstructionsOfType<JIRAssignInst>(SAMPLE_FQN, "localVarCallThreeArgs")

        val call = callInsts.find { it.callExpr?.method?.method?.name == "replace" }
        checkNotNull(call) { "replace() call not found" }

        val location = createIntermediateLocation(call)
        val span = resolver.computeSpan(sourcePath, location)

        val expectedSpan = parseSpanMarker(sourcePath, "localVarCallThreeArgs")
        assertSpanMatchesMarker(span, expectedSpan)
    }

    // Tests for method calls on method result: getX().foo(...)

    @Test
    fun `resolve span for chained method call with no arguments`() {
        val sourcePath = getSourcePath(SAMPLE_FQN)
        val callInsts = getInstructionsOfType<JIRAssignInst>(SAMPLE_FQN, "chainedCallNoArgs")

        val call = callInsts.find { it.callExpr?.method?.method?.name == "length" }
        checkNotNull(call) { "length() call not found" }

        val location = createIntermediateLocation(call)
        val span = resolver.computeSpan(sourcePath, location)

        val expectedSpan = parseSpanMarker(sourcePath, "chainedCallNoArgs")
        assertSpanMatchesMarker(span, expectedSpan)
    }

    @Test
    fun `resolve span for chained method call with one argument`() {
        val sourcePath = getSourcePath(SAMPLE_FQN)
        val callInsts = getInstructionsOfType<JIRAssignInst>(SAMPLE_FQN, "chainedCallOneArg")

        val call = callInsts.find { it.callExpr?.method?.method?.name == "charAt" }
        checkNotNull(call) { "charAt() call not found" }

        val location = createIntermediateLocation(call)
        val span = resolver.computeSpan(sourcePath, location)

        val expectedSpan = parseSpanMarker(sourcePath, "chainedCallOneArg")
        assertSpanMatchesMarker(span, expectedSpan)
    }

    @Test
    fun `resolve span for chained method call with two arguments`() {
        val sourcePath = getSourcePath(SAMPLE_FQN)
        val callInsts = getInstructionsOfType<JIRAssignInst>(SAMPLE_FQN, "chainedCallTwoArgs")

        val call = callInsts.find { it.callExpr?.method?.method?.name == "substring" }
        checkNotNull(call) { "substring() call not found" }

        val location = createIntermediateLocation(call)
        val span = resolver.computeSpan(sourcePath, location)

        val expectedSpan = parseSpanMarker(sourcePath, "chainedCallTwoArgs")
        assertSpanMatchesMarker(span, expectedSpan)
    }

    @Test
    fun `resolve span for chained method call with three arguments`() {
        val sourcePath = getSourcePath(SAMPLE_FQN)
        val callInsts = getInstructionsOfType<JIRAssignInst>(SAMPLE_FQN, "chainedCallThreeArgs")

        val call = callInsts.find { it.callExpr?.method?.method?.name == "replace" }
        checkNotNull(call) { "replace() call not found" }

        val location = createIntermediateLocation(call)
        val span = resolver.computeSpan(sourcePath, location)

        val expectedSpan = parseSpanMarker(sourcePath, "chainedCallThreeArgs")
        assertSpanMatchesMarker(span, expectedSpan)
    }
}

