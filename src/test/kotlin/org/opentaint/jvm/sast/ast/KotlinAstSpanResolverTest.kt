package org.opentaint.jvm.sast.ast

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.opentaint.ir.api.jvm.cfg.JIRArrayAccess
import org.opentaint.ir.api.jvm.cfg.JIRAssignInst
import org.opentaint.ir.api.jvm.cfg.JIRCallExpr
import org.opentaint.ir.api.jvm.cfg.JIRCallInst
import org.opentaint.ir.api.jvm.cfg.JIRFieldRef
import org.opentaint.ir.api.jvm.cfg.JIRInst
import org.opentaint.ir.api.jvm.cfg.JIRReturnInst
import org.opentaint.ir.api.jvm.cfg.JIRLocal
import org.opentaint.ir.api.jvm.cfg.JIRStringConstant
import org.opentaint.ir.api.jvm.ext.cfg.callExpr
import org.opentaint.jvm.sast.sarif.InstructionInfo
import org.opentaint.jvm.sast.sarif.IntermediateLocation
import org.opentaint.jvm.sast.sarif.LocationType

class KotlinAstSpanResolverTest : BasicTestUtils() {

    override val sourceFileExtension = "kt"

    private lateinit var resolver: KotlinAstSpanResolver

    @BeforeAll
    fun initResolver() {
        resolver = KotlinAstSpanResolver(traits)
    }

    companion object {
        private const val SAMPLE_PACKAGE = "test.samples"
        private const val SAMPLE_FQN = "$SAMPLE_PACKAGE.KotlinSpanResolverSample"
        private const val CONSTRUCTOR_SAMPLE_FQN = "$SAMPLE_PACKAGE.KotlinConstructorSample"
        private const val STATIC_SAMPLE_FQN = "$SAMPLE_PACKAGE.KotlinStaticMethodSample"
        private const val ANNOTATED_SAMPLE_FQN = "$SAMPLE_PACKAGE.KotlinAnnotatedSample"
        private const val DATA_CLASS_FQN = "$SAMPLE_PACKAGE.DataClassWithTypeParams"

        private const val SUSPEND_SAMPLE_FQN = "$SAMPLE_PACKAGE.SuspendSample"
        private const val EXTENSION_SAMPLE_FQN = "$SAMPLE_PACKAGE.ExtensionSample"
        private const val OPERATOR_SAMPLE_FQN = "$SAMPLE_PACKAGE.OperatorSample"
        private const val MULTI_TYPE_PARAM_FQN = "$SAMPLE_PACKAGE.MultipleTypeParamSample"
        private const val SINGLETON_SAMPLE_FQN = "$SAMPLE_PACKAGE.SingletonSample"
        private const val COMPANION_SAMPLE_FQN = "$SAMPLE_PACKAGE.CompanionSample"
        private const val INLINE_CALL_FILE = "$SAMPLE_PACKAGE.KotlinAnnotatedSampleKt"
        private const val DATA_CLASS_SIMPLE_FQN = "$SAMPLE_PACKAGE.DataClassSimple"
        private const val DATA_CLASS_WITH_INIT_FQN = "$SAMPLE_PACKAGE.DataClassWithInit"
        private const val DATA_CLASS_ANNOTATED_FQN = "$SAMPLE_PACKAGE.DataClassAnnotated"
        private const val DATA_CLASS_WITH_DEFAULTS_FQN = "$SAMPLE_PACKAGE.DataClassWithDefaults"
        private const val EXPRESSION_BODY_SAMPLE_FQN = "$SAMPLE_PACKAGE.ExpressionBodySample"
        private const val INLINE_LAMBDA_FQN = "$SAMPLE_PACKAGE.KotlinInlineLambdaSample"
    }

    @Test
    fun `resolve span for assignment with method call`() {
        val sourcePath = getSourcePath(SAMPLE_FQN)
        val assignInsts = getInstructionsOfType<JIRAssignInst>(SAMPLE_FQN, "assignmentWithCall")

        val getValueAssign = assignInsts.find { inst ->
            val rhv = inst.rhv
            rhv is JIRCallExpr && rhv.method.method.name == "getFieldValue"
        }

        checkNotNull(getValueAssign) { "Assignment with getFieldValue() call not found" }

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
    fun `resolve span for constructor declaration`() {
        val sourcePath = getSourcePath(CONSTRUCTOR_SAMPLE_FQN)
        val constructorClass = findClass(CONSTRUCTOR_SAMPLE_FQN)

        val noArgConstructor = constructorClass.declaredMethods.find {
            it.isConstructor && it.parameters.isEmpty()
        }

        checkNotNull(noArgConstructor) { "No-arg constructor not found" }

        val firstInst = noArgConstructor.flowGraph().instructions.firstOrNull()
        checkNotNull(firstInst) { "No instructions found in constructor" }

        val location = createIntermediateLocation(firstInst, LocationType.RuleMethodEntry)
        val span = resolver.computeSpan(sourcePath, location)

        val expectedSpan = parseSpanMarker(sourcePath, "noArgConstructor")
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
        val method = findMethod(SAMPLE_FQN, "getFieldValue")

        val firstInst = method.instList.firstOrNull()
        checkNotNull(firstInst) { "No instructions found in getFieldValue method" }

        val location = createIntermediateLocation(firstInst, LocationType.RuleMethodEntry)
        val span = resolver.computeSpan(sourcePath, location)

        val expectedSpan = parseSpanMarker(sourcePath, "methodEntry")
        assertSpanMatchesMarker(span, expectedSpan)
    }

    @Test
    fun `resolve span for method exit`() {
        val sourcePath = getSourcePath(SAMPLE_FQN)
        val method = findMethod(SAMPLE_FQN, "getFieldValue")
        val returnInst = method.instList.filterIsInstance<JIRReturnInst>().firstOrNull()

        checkNotNull(returnInst) { "Return instruction not found in getFieldValue" }

        val location = createMethodExitLocation(returnInst)
        val span = resolver.computeSpan(sourcePath, location)

        val expectedSpan = parseSpanMarker(sourcePath, "methodExit")
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
        val sourcePath = getAnnotatedSourcePath()
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
        val sourcePath = getAnnotatedSourcePath()
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
        val sourcePath = getAnnotatedSourcePath()
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
        val sourcePath = getAnnotatedSourcePath()
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
        val sourcePath = getAnnotatedSourcePath()
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
    fun `resolve span for method with complex generic signature`() {
        val sourcePath = getAnnotatedSourcePath()
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
    fun `resolve span for data class method entry and exit`() {
        val sourcePath = getAnnotatedSourcePath()
        val method = findMethod(DATA_CLASS_FQN, "combine")

        val firstInst = method.instList.firstOrNull()
        checkNotNull(firstInst) { "No instructions in combine method" }

        val entryLocation = createIntermediateLocation(firstInst, LocationType.RuleMethodEntry)
        val entrySpan = resolver.computeSpan(sourcePath, entryLocation)
        assertSpanMatchesMarker(entrySpan, parseSpanMarker(sourcePath, "dataClassMethod"))

        val returnInst = method.instList.filterIsInstance<JIRReturnInst>().first()
        val exitLocation = createMethodExitLocation(returnInst)
        val exitSpan = resolver.computeSpan(sourcePath, exitLocation)
        assertSpanMatchesMarker(exitSpan, parseSpanMarker(sourcePath, "dataClassMethodExit"))
    }

    @Test
    fun `resolve span for inline function call site - before call`() {
        val sourcePath = getAnnotatedSourcePath()
        val method = findMethod(INLINE_CALL_FILE, "callInlineFunction")
        val assignInsts = method.instList.filterIsInstance<JIRAssignInst>()

        val inputAssign = assignInsts.find {
            val rhv = it.rhv
            rhv is JIRStringConstant && rhv.value == "test"
        }
        checkNotNull(inputAssign) { "Input assignment not found" }

        val location = createIntermediateLocation(inputAssign)
        val span = resolver.computeSpan(sourcePath, location)
        assertSpanMatchesMarker(span, parseSpanMarker(sourcePath, "inlineCallBefore"))
    }

    @Test
    fun `resolve span for inline function call site - after call`() {
        val sourcePath = getAnnotatedSourcePath()
        val method = findMethod(INLINE_CALL_FILE, "callInlineFunction")
        val returnInst = method.instList.filterIsInstance<JIRReturnInst>().firstOrNull()

        checkNotNull(returnInst) { "Return instruction not found" }

        val location = createIntermediateLocation(returnInst)
        val span = resolver.computeSpan(sourcePath, location)
        assertSpanMatchesMarker(span, parseSpanMarker(sourcePath, "inlineCallAfter"))
    }

    @Test
    fun `resolve span for suspend function entry and exit`() {
        val sourcePath = getAnnotatedSourcePath()
        val method = findMethod(SUSPEND_SAMPLE_FQN, "suspendFunction")

        val firstInst = method.instList.firstOrNull()
        checkNotNull(firstInst) { "No instructions in suspendFunction" }

        val entryLocation = createIntermediateLocation(firstInst, LocationType.RuleMethodEntry)
        val entrySpan = resolver.computeSpan(sourcePath, entryLocation)
        assertSpanMatchesMarker(entrySpan, parseSpanMarker(sourcePath, "suspendMethod"))

        val returnInst = method.instList.filterIsInstance<JIRReturnInst>().first()
        val exitLocation = createMethodExitLocation(returnInst)
        val exitSpan = resolver.computeSpan(sourcePath, exitLocation)
        assertSpanMatchesMarker(exitSpan, parseSpanMarker(sourcePath, "suspendMethodExit"))
    }

    @Test
    fun `resolve span for extension function entry and exit`() {
        val sourcePath = getAnnotatedSourcePath()
        val method = findMethod(EXTENSION_SAMPLE_FQN, "myExtension")

        val firstInst = method.instList.firstOrNull()
        checkNotNull(firstInst) { "No instructions in myExtension" }

        val entryLocation = createIntermediateLocation(firstInst, LocationType.RuleMethodEntry)
        val entrySpan = resolver.computeSpan(sourcePath, entryLocation)
        assertSpanMatchesMarker(entrySpan, parseSpanMarker(sourcePath, "extensionMethod"))

        val returnInst = method.instList.filterIsInstance<JIRReturnInst>().first()
        val exitLocation = createMethodExitLocation(returnInst)
        val exitSpan = resolver.computeSpan(sourcePath, exitLocation)
        assertSpanMatchesMarker(exitSpan, parseSpanMarker(sourcePath, "extensionMethodExit"))
    }

    @Test
    fun `resolve span for extension function call`() {
        val sourcePath = getAnnotatedSourcePath()
        val method = findMethod(EXTENSION_SAMPLE_FQN, "useExtension")
        val assignInsts = method.instList.filterIsInstance<JIRAssignInst>()

        val extensionCall = assignInsts.find {
            it.callExpr?.method?.method?.name == "myExtension"
        }
        checkNotNull(extensionCall) { "Extension function call not found" }

        val location = createIntermediateLocation(extensionCall)
        val span = resolver.computeSpan(sourcePath, location)
        assertSpanMatchesMarker(span, parseSpanMarker(sourcePath, "extensionCall"))
    }

    @Test
    fun `resolve span for operator function entry and exit`() {
        val sourcePath = getAnnotatedSourcePath()
        val method = findMethod(OPERATOR_SAMPLE_FQN, "plus")

        val firstInst = method.instList.firstOrNull()
        checkNotNull(firstInst) { "No instructions in plus operator" }

        val entryLocation = createIntermediateLocation(firstInst, LocationType.RuleMethodEntry)
        val entrySpan = resolver.computeSpan(sourcePath, entryLocation)
        assertSpanMatchesMarker(entrySpan, parseSpanMarker(sourcePath, "operatorMethod"))

        val returnInst = method.instList.filterIsInstance<JIRReturnInst>().first()
        val exitLocation = createMethodExitLocation(returnInst)
        val exitSpan = resolver.computeSpan(sourcePath, exitLocation)
        assertSpanMatchesMarker(exitSpan, parseSpanMarker(sourcePath, "operatorMethodExit"))
    }

    @Test
    fun `resolve span for operator function call`() {
        val sourcePath = getAnnotatedSourcePath()
        val method = findMethod(OPERATOR_SAMPLE_FQN, "useOperator")
        val assignInsts = method.instList.filterIsInstance<JIRAssignInst>()

        val plusCall = assignInsts.find {
            it.callExpr?.method?.method?.name == "plus"
        }
        checkNotNull(plusCall) { "Plus operator call not found" }

        val location = createIntermediateLocation(plusCall)
        val span = resolver.computeSpan(sourcePath, location)
        assertSpanMatchesMarker(span, parseSpanMarker(sourcePath, "operatorCall"))
    }

    @Test
    fun `resolve span for class with multiple type parameters constructor`() {
        val sourcePath = getAnnotatedSourcePath()
        val clazz = findClass(MULTI_TYPE_PARAM_FQN)

        val constructor = clazz.declaredMethods.find { it.isConstructor && it.parameters.size == 3 }
        checkNotNull(constructor) { "Three-arg constructor not found" }

        val firstInst = constructor.instList.firstOrNull()
        checkNotNull(firstInst) { "No instructions in constructor" }

        val entryLocation = createIntermediateLocation(firstInst, LocationType.RuleMethodEntry)
        val entrySpan = resolver.computeSpan(sourcePath, entryLocation)
        assertSpanMatchesMarker(entrySpan, parseSpanMarker(sourcePath, "multiTypeParamConstructor"))

        val returnInst = constructor.instList.filterIsInstance<JIRReturnInst>().first()
        val exitLocation = createMethodExitLocation(returnInst)
        val exitSpan = resolver.computeSpan(sourcePath, exitLocation)
        assertSpanMatchesMarker(exitSpan, parseSpanMarker(sourcePath, "multiTypeParamConstructorExit"))
    }

    @Test
    fun `resolve span for method with nested type parameter bounds`() {
        val sourcePath = getAnnotatedSourcePath()
        val method = findMethod(MULTI_TYPE_PARAM_FQN, "transform")

        val firstInst = method.instList.firstOrNull()
        checkNotNull(firstInst) { "No instructions in transform method" }

        val entryLocation = createIntermediateLocation(firstInst, LocationType.RuleMethodEntry)
        val entrySpan = resolver.computeSpan(sourcePath, entryLocation)
        assertSpanMatchesMarker(entrySpan, parseSpanMarker(sourcePath, "multiTypeParamMethod"))

        val returnInst = method.instList.filterIsInstance<JIRReturnInst>().first()
        val exitLocation = createMethodExitLocation(returnInst)
        val exitSpan = resolver.computeSpan(sourcePath, exitLocation)
        assertSpanMatchesMarker(exitSpan, parseSpanMarker(sourcePath, "multiTypeParamMethodExit"))
    }

    @Test
    fun `resolve span for singleton object method entry and exit`() {
        val sourcePath = getAnnotatedSourcePath()
        val method = findMethod(SINGLETON_SAMPLE_FQN, "increment")

        val firstInst = method.instList.firstOrNull()
        checkNotNull(firstInst) { "No instructions in increment method" }

        val entryLocation = createIntermediateLocation(firstInst, LocationType.RuleMethodEntry)
        val entrySpan = resolver.computeSpan(sourcePath, entryLocation)
        assertSpanMatchesMarker(entrySpan, parseSpanMarker(sourcePath, "objectMethod"))

        val returnInst = method.instList.filterIsInstance<JIRReturnInst>().first()
        val exitLocation = createMethodExitLocation(returnInst)
        val exitSpan = resolver.computeSpan(sourcePath, exitLocation)
        assertSpanMatchesMarker(exitSpan, parseSpanMarker(sourcePath, "objectMethodExit"))
    }

    @Test
    fun `resolve span for companion object method entry and exit`() {
        val sourcePath = getAnnotatedSourcePath()
        val companionFqn = "$COMPANION_SAMPLE_FQN\$Companion"
        val method = findMethod(companionFqn, "getShared")

        val firstInst = method.instList.firstOrNull()
        checkNotNull(firstInst) { "No instructions in getShared method" }

        val entryLocation = createIntermediateLocation(firstInst, LocationType.RuleMethodEntry)
        val entrySpan = resolver.computeSpan(sourcePath, entryLocation)
        assertSpanMatchesMarker(entrySpan, parseSpanMarker(sourcePath, "companionMethod"))

        val returnInst = method.instList.filterIsInstance<JIRReturnInst>().first()
        val exitLocation = createMethodExitLocation(returnInst)
        val exitSpan = resolver.computeSpan(sourcePath, exitLocation)
        assertSpanMatchesMarker(exitSpan, parseSpanMarker(sourcePath, "companionMethodExit"))
    }

    @Test
    fun `resolve span for data class primary constructor entry`() {
        val sourcePath = getAnnotatedSourcePath()
        val clazz = findClass(DATA_CLASS_SIMPLE_FQN)

        val constructor = clazz.declaredMethods.find { it.isConstructor }
        checkNotNull(constructor) { "Data class constructor not found" }

        val firstInst = constructor.instList.firstOrNull()
        checkNotNull(firstInst) { "No instructions in data class constructor" }

        val entryLocation = createIntermediateLocation(firstInst, LocationType.RuleMethodEntry)
        val entrySpan = resolver.computeSpan(sourcePath, entryLocation)
        assertSpanMatchesMarker(entrySpan, parseSpanMarker(sourcePath, "dataClassEntry"))
    }

    @Test
    fun `resolve span for data class with init block entry and exit`() {
        val sourcePath = getAnnotatedSourcePath()
        val clazz = findClass(DATA_CLASS_WITH_INIT_FQN)

        val constructor = clazz.declaredMethods.find { it.isConstructor }
        checkNotNull(constructor) { "Data class constructor not found" }

        val firstInst = constructor.instList.firstOrNull()
        checkNotNull(firstInst) { "No instructions in data class constructor" }

        val entryLocation = createIntermediateLocation(firstInst, LocationType.RuleMethodEntry)
        val entrySpan = resolver.computeSpan(sourcePath, entryLocation)
        assertSpanMatchesMarker(entrySpan, parseSpanMarker(sourcePath, "dataClassWithInitEntry"))
    }

    @Test
    fun `resolve span for data class field initializer`() {
        val sourcePath = getAnnotatedSourcePath()
        val assignInsts = getInstructionsOfType<JIRAssignInst>(DATA_CLASS_WITH_INIT_FQN, "<init>")

        val fieldInit = assignInsts.find { inst ->
            inst.lhv is JIRFieldRef && (inst.lhv as JIRFieldRef).field.name == "nameUppercase"
        }
        checkNotNull(fieldInit) { "Field initializer assignment not found" }

        val location = createIntermediateLocation(fieldInit)
        val span = resolver.computeSpan(sourcePath, location)
        assertSpanMatchesMarker(span, parseSpanMarker(sourcePath, "dataClassFieldInit"))
    }

    @Test
    fun `resolve span for data class init block`() {
        val sourcePath = getAnnotatedSourcePath()
        val callInsts = getInstructionsOfType<JIRCallInst>(DATA_CLASS_WITH_INIT_FQN, "<init>")

        val printlnCall = callInsts.find { inst ->
            inst.callExpr.method.method.name == "println"
        }
        checkNotNull(printlnCall) { "println call in init block not found" }

        val location = createIntermediateLocation(printlnCall)
        val span = resolver.computeSpan(sourcePath, location)
        assertSpanMatchesMarker(span, parseSpanMarker(sourcePath, "dataClassInitBlock"))
    }

    @Test
    fun `resolve span for data class with type parameter annotations`() {
        val sourcePath = getAnnotatedSourcePath()
        val clazz = findClass(DATA_CLASS_ANNOTATED_FQN)

        val constructor = clazz.declaredMethods.find { it.isConstructor }
        checkNotNull(constructor) { "Data class constructor not found" }

        val firstInst = constructor.instList.firstOrNull()
        checkNotNull(firstInst) { "No instructions in data class constructor" }

        val entryLocation = createIntermediateLocation(firstInst, LocationType.RuleMethodEntry)
        val entrySpan = resolver.computeSpan(sourcePath, entryLocation)
        assertSpanMatchesMarker(entrySpan, parseSpanMarker(sourcePath, "dataClassAnnotatedEntry"))
    }

    @Test
    fun `resolve span for suspend generic function entry and exit`() {
        val sourcePath = getAnnotatedSourcePath()
        val method = findMethod(SUSPEND_SAMPLE_FQN, "suspendGenericFunction")

        val firstInst = method.instList.firstOrNull()
        checkNotNull(firstInst) { "No instructions in suspendGenericFunction" }

        val entryLocation = createIntermediateLocation(firstInst, LocationType.RuleMethodEntry)
        val entrySpan = resolver.computeSpan(sourcePath, entryLocation)
        assertSpanMatchesMarker(entrySpan, parseSpanMarker(sourcePath, "suspendGenericMethod"))

        val returnInst = method.instList.filterIsInstance<JIRReturnInst>().first()
        val exitLocation = createMethodExitLocation(returnInst)
        val exitSpan = resolver.computeSpan(sourcePath, exitLocation)
        assertSpanMatchesMarker(exitSpan, parseSpanMarker(sourcePath, "suspendGenericMethodExit"))
    }

    @Test
    fun `resolve span for suspend function call`() {
        val sourcePath = getAnnotatedSourcePath()
        val method = findMethod(SUSPEND_SAMPLE_FQN, "useSuspend")
        val assignInsts = method.instList.filterIsInstance<JIRAssignInst>()

        val suspendCall = assignInsts.find {
            it.callExpr?.method?.method?.name == "suspendFunction"
        }
        checkNotNull(suspendCall) { "Suspend function call not found" }

        val location = createIntermediateLocation(suspendCall)
        val span = resolver.computeSpan(sourcePath, location)
        assertSpanMatchesMarker(span, parseSpanMarker(sourcePath, "suspendCall"))
    }

    @Test
    fun `resolve span for inline function entry and exit`() {
        val sourcePath = getAnnotatedSourcePath()
        val method = findMethod(INLINE_CALL_FILE, "inlineFunctionSample")

        val firstInst = method.instList.firstOrNull()
        checkNotNull(firstInst) { "No instructions in inlineFunctionSample" }

        val entryLocation = createIntermediateLocation(firstInst, LocationType.RuleMethodEntry)
        val entrySpan = resolver.computeSpan(sourcePath, entryLocation)
        assertSpanMatchesMarker(entrySpan, parseSpanMarker(sourcePath, "inlineFunctionEntry"))

        val returnInst = method.instList.filterIsInstance<JIRReturnInst>().first()
        val exitLocation = createMethodExitLocation(returnInst)
        val exitSpan = resolver.computeSpan(sourcePath, exitLocation)
        assertSpanMatchesMarker(exitSpan, parseSpanMarker(sourcePath, "inlineFunctionExit"))
    }

    @Test
    fun `resolve span for inline function with lambda - result assignment`() {
        val sourcePath = getAnnotatedSourcePath()
        val method = findMethod(INLINE_CALL_FILE, "callInlineWithLambda")
        val assignInsts = method.instList.filterIsInstance<JIRAssignInst>()

        val resultAssign = assignInsts.find {
            val lhv = it.lhv
            lhv is JIRLocal && lhv.name.contains("result")
        }
        checkNotNull(resultAssign) { "Result assignment in inline with lambda not found" }

        val location = createIntermediateLocation(resultAssign)
        val span = resolver.computeSpan(sourcePath, location)
        assertSpanMatchesMarker(span, parseSpanMarker(sourcePath, "lambdaInlineCall"))
    }

    @Test
    fun `resolve span for reified inline function entry and exit`() {
        val sourcePath = getAnnotatedSourcePath()
        val method = findMethod(INLINE_CALL_FILE, "reifiedInlineFunction")

        val firstInst = method.instList.firstOrNull()
        checkNotNull(firstInst) { "No instructions in reifiedInlineFunction" }

        val entryLocation = createIntermediateLocation(firstInst, LocationType.RuleMethodEntry)
        val entrySpan = resolver.computeSpan(sourcePath, entryLocation)
        assertSpanMatchesMarker(entrySpan, parseSpanMarker(sourcePath, "reifiedInlineEntry"))

        val returnInst = method.instList.filterIsInstance<JIRReturnInst>().first()
        val exitLocation = createMethodExitLocation(returnInst)
        val exitSpan = resolver.computeSpan(sourcePath, exitLocation)
        assertSpanMatchesMarker(exitSpan, parseSpanMarker(sourcePath, "reifiedInlineExit"))
    }

    @Test
    fun `resolve span for reified inline function - result assignment`() {
        val sourcePath = getAnnotatedSourcePath()
        val method = findMethod(INLINE_CALL_FILE, "callReifiedInline")
        val assignInsts = method.instList.filterIsInstance<JIRAssignInst>()

        val resultAssign = assignInsts.find {
            val lhv = it.lhv
            lhv is JIRLocal && lhv.name.contains("result")
        }
        checkNotNull(resultAssign) { "Result assignment in reified inline not found" }

        val location = createIntermediateLocation(resultAssign)
        val span = resolver.computeSpan(sourcePath, location)
        assertSpanMatchesMarker(span, parseSpanMarker(sourcePath, "reifiedInlineCall"))
    }

    @Test
    fun `data class primary constructor - field assign instruction for name field`() {
        val sourcePath = getAnnotatedSourcePath()
        val clazz = findClass(DATA_CLASS_SIMPLE_FQN)
        val constructor = clazz.declaredMethods.find { it.isConstructor }
        checkNotNull(constructor) { "Data class constructor not found" }

        val assignInsts = constructor.instList.filterIsInstance<JIRAssignInst>()
        val nameFieldAssign = assignInsts.find { inst ->
            inst.lhv is JIRFieldRef && (inst.lhv as JIRFieldRef).field.name == "name"
        }
        checkNotNull(nameFieldAssign) { "Name field assignment not found" }

        val location = createIntermediateLocation(nameFieldAssign)
        val span = resolver.computeSpan(sourcePath, location)
        assertSpanMatchesMarker(span, parseSpanMarker(sourcePath, "dataClassPrimaryFieldName"))
    }

    @Test
    fun `data class primary constructor - field assign instruction for value field`() {
        val sourcePath = getAnnotatedSourcePath()
        val clazz = findClass(DATA_CLASS_SIMPLE_FQN)
        val constructor = clazz.declaredMethods.find { it.isConstructor }
        checkNotNull(constructor) { "Data class constructor not found" }

        val assignInsts = constructor.instList.filterIsInstance<JIRAssignInst>()
        val valueFieldAssign = assignInsts.find { inst ->
            inst.lhv is JIRFieldRef && (inst.lhv as JIRFieldRef).field.name == "value"
        }
        checkNotNull(valueFieldAssign) { "Value field assignment not found" }

        val location = createIntermediateLocation(valueFieldAssign)
        val span = resolver.computeSpan(sourcePath, location)
        assertSpanMatchesMarker(span, parseSpanMarker(sourcePath, "dataClassPrimaryFieldValue"))
    }

    @Test
    fun `suspend function body - toUpperCase call instruction`() {
        val sourcePath = getAnnotatedSourcePath()
        val method = findMethod(SUSPEND_SAMPLE_FQN, "suspendFunction")
        val assignInsts = method.instList.filterIsInstance<JIRAssignInst>()

        val uppercaseCall = assignInsts.find { inst ->
            inst.callExpr?.method?.method?.name == "toUpperCase"
        }
        checkNotNull(uppercaseCall) { "toUpperCase() call not found in suspend function body" }

        val location = createIntermediateLocation(uppercaseCall)
        val span = resolver.computeSpan(sourcePath, location)
        assertSpanMatchesMarker(span, parseSpanMarker(sourcePath, "suspendBodyInst"))
    }

    @Test
    fun `inline function - valueOf call from inlined body returns null`() {
        val sourcePath = getAnnotatedSourcePath()
        val method = findMethod(INLINE_CALL_FILE, "callInlineFunction")
        val assignInsts = method.instList.filterIsInstance<JIRAssignInst>()

        val valueOfCall = assignInsts.find { inst ->
            inst.callExpr?.method?.method?.name == "valueOf"
        }
        checkNotNull(valueOfCall) { "valueOf() call from inlined body not found" }

        val location = createIntermediateLocation(valueOfCall)
        val span = resolver.computeSpan(sourcePath, location)
        assertNull(span, "Inlined function body instructions should return null span (line doesn't exist in caller)")
    }

    @Test
    fun `resolve spans for complex suspend function - entry and exit`() {
        val sourcePath = getAnnotatedSourcePath()
        val method = findMethod(SUSPEND_SAMPLE_FQN, "complexSuspendFunction")

        val firstInst = method.instList.firstOrNull()
        checkNotNull(firstInst) { "No instructions in complexSuspendFunction" }

        val entryLocation = createIntermediateLocation(firstInst, LocationType.RuleMethodEntry)
        val entrySpan = resolver.computeSpan(sourcePath, entryLocation)
        assertSpanMatchesMarker(entrySpan, parseSpanMarker(sourcePath, "complexSuspendEntry"))

        val returnInst = method.instList.filterIsInstance<JIRReturnInst>().first()
        val exitLocation = createMethodExitLocation(returnInst)
        val exitSpan = resolver.computeSpan(sourcePath, exitLocation)
        assertSpanMatchesMarker(exitSpan, parseSpanMarker(sourcePath, "complexSuspendExit"))
    }

    @Test
    fun `resolve spans for complex method with loops - entry and exit`() {
        val sourcePath = getAnnotatedSourcePath()
        val method = findMethod(SUSPEND_SAMPLE_FQN, "complexMethodWithLoops")

        val firstInst = method.instList.firstOrNull()
        checkNotNull(firstInst) { "No instructions in complexMethodWithLoops" }

        val entryLocation = createIntermediateLocation(firstInst, LocationType.RuleMethodEntry)
        val entrySpan = resolver.computeSpan(sourcePath, entryLocation)
        assertSpanMatchesMarker(entrySpan, parseSpanMarker(sourcePath, "complexMethodEntry"))

        val returnInst = method.instList.filterIsInstance<JIRReturnInst>().first()
        val exitLocation = createMethodExitLocation(returnInst)
        val exitSpan = resolver.computeSpan(sourcePath, exitLocation)
        assertSpanMatchesMarker(exitSpan, parseSpanMarker(sourcePath, "complexMethodExit"))
    }

    @Test
    fun `resolve spans for complex method with loops - for loop call`() {
        val sourcePath = getAnnotatedSourcePath()
        val method = findMethod(SUSPEND_SAMPLE_FQN, "complexMethodWithLoops")
        val assignInsts = method.instList.filterIsInstance<JIRAssignInst>()

        val uppercaseCall = assignInsts.find { inst ->
            inst.callExpr?.method?.method?.name == "toUpperCase"
        }
        checkNotNull(uppercaseCall) { "uppercase() call inside for loop not found" }

        val location = createIntermediateLocation(uppercaseCall)
        val span = resolver.computeSpan(sourcePath, location)
        assertSpanMatchesMarker(span, parseSpanMarker(sourcePath, "complexLoopCall"))
    }

    @Test
    fun `resolve spans for complex method with loops - while loop get`() {
        val sourcePath = getAnnotatedSourcePath()
        val method = findMethod(SUSPEND_SAMPLE_FQN, "complexMethodWithLoops")
        val assignInsts = method.instList.filterIsInstance<JIRAssignInst>()

        val getCall = assignInsts.find { inst ->
            inst.callExpr?.method?.method?.name == "get"
        }
        checkNotNull(getCall) { "get() call inside while loop not found" }

        val location = createIntermediateLocation(getCall)
        val span = resolver.computeSpan(sourcePath, location)
        assertSpanMatchesMarker(span, parseSpanMarker(sourcePath, "complexWhileGet"))
    }

    @Test
    fun `resolve spans for complex method with loops - return statement`() {
        val sourcePath = getAnnotatedSourcePath()
        val method = findMethod(SUSPEND_SAMPLE_FQN, "complexMethodWithLoops")
        val returnInsts = method.instList.filterIsInstance<JIRReturnInst>()

        check(returnInsts.isNotEmpty()) { "Return instruction not found" }

        val returnInst = returnInsts.first()
        val location = createIntermediateLocation(returnInst)
        val span = resolver.computeSpan(sourcePath, location)
        assertSpanMatchesMarker(span, parseSpanMarker(sourcePath, "complexReturn"))
    }

    @Test
    fun `resolve spans for complex method with loops - full method test`() {
        val sourcePath = getAnnotatedSourcePath()
        val method = findMethod(SUSPEND_SAMPLE_FQN, "complexMethodWithLoops")
        val instructions = method.instList

        val expectedSpans = mutableMapOf<String, JIRInst?>()
        expectedSpans["complexLoopCall"] = findInstruction<JIRAssignInst>(instructions) {
            it.callExpr?.method?.method?.name == "toUpperCase"
        }
        expectedSpans["complexWhileGet"] = findInstruction<JIRAssignInst>(instructions) {
            it.callExpr?.method?.method?.name == "get"
        }
        expectedSpans["complexReturn"] = findInstruction<JIRReturnInst>(instructions) { true }
        expectedSpans["complexMethodExit"] = findInstruction<JIRReturnInst>(instructions) { true }
        expectedSpans["complexMethodEntry"] = method.instList.firstOrNull()

        for ((markerName, inst) in expectedSpans) {
            checkNotNull(inst) { "Instruction for marker '$markerName' not found" }

            val location = when (markerName) {
                "complexMethodEntry" -> createIntermediateLocation(inst, LocationType.RuleMethodEntry)
                "complexMethodExit" -> createMethodExitLocation(inst as JIRReturnInst)
                else -> createIntermediateLocation(inst)
            }

            val span = resolver.computeSpan(sourcePath, location)
            val expectedSpan = parseSpanMarker(sourcePath, markerName)

            assertSpanMatchesMarker(span, expectedSpan)
        }
    }

    @Test
    fun `expression body function - entry span for multiline expression body`() {
        val sourcePath = getAnnotatedSourcePath()
        val method = findMethod(EXPRESSION_BODY_SAMPLE_FQN, "processWithParams")

        val firstInst = method.instList.firstOrNull()
        checkNotNull(firstInst) { "No instructions in processWithParams" }

        val entryLocation = createIntermediateLocation(firstInst, LocationType.RuleMethodEntry)
        val entrySpan = resolver.computeSpan(sourcePath, entryLocation)
        assertSpanMatchesMarker(entrySpan, parseSpanMarker(sourcePath, "expressionBodyEntry"))
    }

    @Test
    fun `expression body function - call instruction in multiline expression body`() {
        val sourcePath = getAnnotatedSourcePath()
        val method = findMethod(EXPRESSION_BODY_SAMPLE_FQN, "processWithParams")
        val assignInsts = method.instList.filterIsInstance<JIRAssignInst>()

        val processCall = assignInsts.find { inst ->
            inst.callExpr?.method?.method?.name == "process"
        }
        checkNotNull(processCall) { "process() call not found in expression body" }

        val location = createIntermediateLocation(processCall)
        val span = resolver.computeSpan(sourcePath, location)
        assertSpanMatchesMarker(span, parseSpanMarker(sourcePath, "expressionBodyCall"))
    }

    @Test
    fun `resolve span for expression body method exit`() {
        val sourcePath = getAnnotatedSourcePath()
        val method = findMethod(EXPRESSION_BODY_SAMPLE_FQN, "processWithParamsExit")

        val returnInst = method.instList.filterIsInstance<JIRReturnInst>().firstOrNull()

        checkNotNull(returnInst) { "Return instruction not found in processWithParamsExit" }

        val location = createMethodExitLocation(returnInst)
        val span = resolver.computeSpan(sourcePath, location)

        val expectedSpan = parseSpanMarker(sourcePath, "expressionBodyExit")
        assertSpanMatchesMarker(span, expectedSpan)
    }

    @Test
    fun `expression body function - simple single line expression`() {
        val sourcePath = getAnnotatedSourcePath()
        val method = findMethod(EXPRESSION_BODY_SAMPLE_FQN, "simpleExpression")

        val firstInst = method.instList.firstOrNull()
        checkNotNull(firstInst) { "No instructions in simpleExpression" }

        val entryLocation = createIntermediateLocation(firstInst, LocationType.RuleMethodEntry)
        val entrySpan = resolver.computeSpan(sourcePath, entryLocation)
        assertSpanMatchesMarker(entrySpan, parseSpanMarker(sourcePath, "simpleExpressionBodyEntry"))
    }

    @Test
    fun `data class with defaults - primary constructor entry`() {
        val sourcePath = getAnnotatedSourcePath()
        val clazz = findClass(DATA_CLASS_WITH_DEFAULTS_FQN)

        val primaryConstructor = clazz.declaredMethods.find {
            it.isConstructor && it.parameters.size == 5
        }
        checkNotNull(primaryConstructor) { "Primary constructor with all params not found" }

        val firstInst = primaryConstructor.instList.firstOrNull()
        checkNotNull(firstInst) { "No instructions in primary constructor" }

        val entryLocation = createIntermediateLocation(firstInst, LocationType.RuleMethodEntry)
        val entrySpan = resolver.computeSpan(sourcePath, entryLocation)
        assertNotNull(entrySpan, "Entry span should not be null for primary constructor")
    }

    @Test
    fun `data class with defaults - synthetic constructor with default mask`() {
        val sourcePath = getAnnotatedSourcePath()
        val clazz = findClass(DATA_CLASS_WITH_DEFAULTS_FQN)

        val syntheticConstructor = clazz.declaredMethods.find {
            it.isConstructor && it.parameters.size > 5
        }
        checkNotNull(syntheticConstructor) { "Synthetic constructor with default mask not found" }

        val firstInst = syntheticConstructor.instList.firstOrNull()
        checkNotNull(firstInst) { "No instructions in synthetic constructor" }

        val entryLocation = createIntermediateLocation(firstInst, LocationType.RuleMethodEntry)
        val entrySpan = resolver.computeSpan(sourcePath, entryLocation)
        assertNotNull(entrySpan, "Entry span should not be null for synthetic constructor")
    }

    @Test
    fun `data class with defaults - field with default value initializer`() {
        val sourcePath = getAnnotatedSourcePath()
        val clazz = findClass(DATA_CLASS_WITH_DEFAULTS_FQN)

        val primaryConstructor = clazz.declaredMethods.find {
            it.isConstructor && it.parameters.size == 5
        }
        checkNotNull(primaryConstructor) { "Primary constructor not found" }

        val assignInsts = primaryConstructor.instList.filterIsInstance<JIRAssignInst>()
        val timestampAssign = assignInsts.find { inst ->
            inst.lhv is JIRFieldRef && (inst.lhv as JIRFieldRef).field.name == "timestamp"
        }
        checkNotNull(timestampAssign) { "timestamp field assignment not found" }

        val location = createIntermediateLocation(timestampAssign)
        val span = resolver.computeSpan(sourcePath, location)
        assertSpanMatchesMarker(span, parseSpanMarker(sourcePath, "dataClassDefaultsTimestamp"))
    }

    @Test
    fun `data class with defaults - full constructor test with all instructions`() {
        val sourcePath = getAnnotatedSourcePath()
        val clazz = findClass(DATA_CLASS_WITH_DEFAULTS_FQN)

        val primaryConstructor = clazz.declaredMethods.find {
            it.isConstructor && it.parameters.size == 5
        }
        checkNotNull(primaryConstructor) { "Primary constructor not found" }

        val instructions = primaryConstructor.instList
        val entryInst = instructions.firstOrNull()
        checkNotNull(entryInst) { "No instructions in constructor" }

        val entryLocation = createIntermediateLocation(entryInst, LocationType.RuleMethodEntry)
        val entrySpan = resolver.computeSpan(sourcePath, entryLocation)
        assertNotNull(entrySpan, "Entry span should not be null")

        val returnInst = instructions.filterIsInstance<JIRReturnInst>().firstOrNull()
        checkNotNull(returnInst) { "Return instruction not found in constructor" }

        val exitLocation = createMethodExitLocation(returnInst)
        val exitSpan = resolver.computeSpan(sourcePath, exitLocation)
        assertNotNull(exitSpan, "Exit span should not be null")

        val fieldAssignments = instructions.filterIsInstance<JIRAssignInst>().filter {
            it.lhv is JIRFieldRef
        }
        for (assign in fieldAssignments) {
            val fieldName = (assign.lhv as JIRFieldRef).field.name
            val location = createIntermediateLocation(assign)
            val span = resolver.computeSpan(sourcePath, location)
            assertNotNull(span, "Span for field '$fieldName' assignment should not be null")
        }
    }

    @Test
    @Disabled
    fun `data class with defaults - self init test`() {
        val sourcePath = getAnnotatedSourcePath()
        val clazz = findClass(DATA_CLASS_WITH_DEFAULTS_FQN)

        val defaultCtor = clazz.declaredMethods.find {
            it.isConstructor && it.parameters.size == 7
        }
        checkNotNull(defaultCtor) { "Default constructor not found" }

        val primaryCtorCall = findInstruction<JIRCallInst>(defaultCtor.instList) {
            it.callExpr.method.method.isConstructor
        }
        checkNotNull(primaryCtorCall) { "Primary ctor call not found" }

        val location = createIntermediateLocation(primaryCtorCall)
        val span = resolver.computeSpan(sourcePath, location)

        TODO("Span is incorrect: $span")
    }

    @Test
    fun `inline function body entry - resolves to function declaration span via broadest span fallback`() {
        val sourcePath = getSourcePath(INLINE_LAMBDA_FQN)
        val method = findMethod(INLINE_LAMBDA_FQN, "inlineLambdaFlow")
        val firstInst = method.instList.firstOrNull()
        checkNotNull(firstInst) { "No instructions in inlineLambdaFlow" }

        val buildItMethod = findMethod(INLINE_LAMBDA_FQN, "buildIt")
        val buildItFirstInst = buildItMethod.instList.firstOrNull()
        checkNotNull(buildItFirstInst) { "No instructions in buildIt" }
        val buildItLine = buildItFirstInst.lineNumber

        val info = InstructionInfo(
            fullyQualified = "${method.enclosingClass.name}.${method.name}",
            machineName = method.name,
            lineNumber = buildItLine
        )
        val location = IntermediateLocation(firstInst, info, "unknown", null, LocationType.Simple, null, null)
        val span = resolver.computeSpan(sourcePath, location)

        val expectedSpan = parseSpanMarker(sourcePath, "i2")
        assertSpanMatchesMarker(span, expectedSpan)
    }

    private fun getAnnotatedSourcePath() = sourcesDir.resolve("test/samples/KotlinAnnotatedSample.kt")
}
