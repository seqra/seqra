package org.opentaint.semgrep.pattern.conversion

import org.opentaint.semgrep.pattern.python.SemgrepPythonPattern
import org.opentaint.semgrep.pattern.python.SemgrepPythonPatternParser
import org.opentaint.semgrep.pattern.python.SemgrepPythonPatternParsingResult
import org.opentaint.semgrep.pattern.SemgrepRuleLoadStepTrace
import org.opentaint.semgrep.pattern.SemgrepTraceEntry.Step
import org.opentaint.semgrep.pattern.conversion.SemgrepPatternAction.ClassConstraint
import org.opentaint.semgrep.pattern.conversion.SemgrepPatternAction.MethodCall
import org.opentaint.semgrep.pattern.conversion.SemgrepPatternAction.MethodExit
import org.opentaint.semgrep.pattern.conversion.SemgrepPatternAction.MethodSignature
import org.opentaint.semgrep.pattern.conversion.SemgrepPatternAction.SignatureModifier
import org.opentaint.semgrep.pattern.conversion.SemgrepPatternAction.SignatureModifierValue
import org.opentaint.semgrep.pattern.conversion.SemgrepPatternAction.SignatureName
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PythonPatternToActionListConverterTest {
    private val parser = SemgrepPythonPatternParser()
    private val noopTrace = SemgrepRuleLoadStepTrace(Step.BUILD_PARSE_SEMGREP_RULE)

    private fun parse(src: String): SemgrepPythonPattern {
        val r = parser.parseSemgrepPythonPattern(src)
        check(r is SemgrepPythonPatternParsingResult.Ok) { "parse failed for `$src`: $r" }
        return r.pattern
    }

    private fun convert(src: String): Pair<SemgrepPatternActionList?, Map<String, Int>> {
        val c = PythonPatternToActionListConverter()
        return c.createActionList(parse(src), noopTrace) to c.failedTransformations
    }

    private fun convertOk(src: String): SemgrepPatternActionList {
        val (r, failures) = convert(src)
        assertNotNull(r, "conversion failed for `$src`: $failures")
        return r
    }

    @Test fun instanceMethodCallOnMetavarReceiver() {
        val a = convertOk("\$DB.execute(\$Q, ...)")
        val call = a.actions.single() as MethodCall
        assertEquals(SignatureName.Concrete("execute"), call.methodName)
        assertEquals(IsMetavar(MetavarAtom.create("\$DB")), call.obj)
        assertEquals(null, call.enclosingClassName)
        val params = call.params as ParamConstraint.Partial
        assertEquals(
            listOf(ParamPattern(ParamPosition.Concrete(0), IsMetavar(MetavarAtom.create("\$Q")))),
            params.params,
        )
    }

    @Test fun qualifiedGlobalCallSingleNamespace() {
        val a = convertOk("os.system(\$CMD)")
        val call = a.actions.single() as MethodCall
        assertEquals(SignatureName.Concrete("system"), call.methodName)
        assertEquals(pythonNamed("os"), call.enclosingClassName)
        assertEquals(ParamCondition.True, call.obj)
        assertEquals(
            ParamConstraint.Concrete(listOf(IsMetavar(MetavarAtom.create("\$CMD")))),
            call.params,
        )
    }

    @Test fun qualifiedGlobalCallMultiDot() {
        val a = convertOk("os.path.join(\$X)")
        val call = a.actions.single() as MethodCall
        assertEquals(SignatureName.Concrete("join"), call.methodName)
        assertEquals(pythonNamed("os.path"), call.enclosingClassName)
        assertEquals(ParamCondition.True, call.obj)
    }

    @Test fun qualifiedGlobalCallWithMetavarFunctionName() {
        val a = convertOk("requests.\$METHOD(\$X)")
        val call = a.actions.single() as MethodCall
        assertEquals(SignatureName.MetaVar("\$METHOD"), call.methodName)
        assertEquals(pythonNamed("requests"), call.enclosingClassName)
        assertEquals(ParamCondition.True, call.obj)
    }

    @Test fun instanceCallWithMetavarMethodName() {
        val a = convertOk("\$DB.\$METHOD(\$Q)")
        val call = a.actions.single() as MethodCall
        assertEquals(SignatureName.MetaVar("\$METHOD"), call.methodName)
        assertEquals(IsMetavar(MetavarAtom.create("\$DB")), call.obj)
        assertEquals(null, call.enclosingClassName)
    }

    @Test fun plainCallWithOnlyEllipsis() {
        val a = convertOk("foo(...)")
        val call = a.actions.single() as MethodCall
        assertEquals(SignatureName.Concrete("foo"), call.methodName)
        assertEquals(null, call.obj)
        assertEquals(null, call.enclosingClassName)
        assertEquals(ParamConstraint.Partial(emptyList()), call.params)
    }

    @Test fun bareAttributeReadConvertsToSyntheticCall() {
        val a = convertOk("\$X.attr")
        val call = a.actions.single() as MethodCall
        assertEquals(SignatureName.Concrete(PythonLanguageStrategy.attrReadAuxFnName("attr")), call.methodName)
        assertEquals(IsMetavar(MetavarAtom.create("\$X")), call.obj)
        assertEquals(null, call.enclosingClassName)
        assertEquals(null, call.result)
        assertEquals(ParamConstraint.Concrete(emptyList()), call.params)
    }

    @Test fun attributeReadOnConcreteReceiver() {
        val a = convertOk("flask.request")
        val call = a.actions.single() as MethodCall
        assertEquals(SignatureName.Concrete(PythonLanguageStrategy.attrReadAuxFnName("request")), call.methodName)
        assertEquals(pythonNamed("flask"), call.enclosingClassName)
        assertEquals(ParamCondition.True, call.obj)
    }

    @Test fun attributeReadBindsAssignResult() {
        val a = convertOk("\$Y = \$X.attr")
        val call = a.actions.single() as MethodCall
        assertEquals(SignatureName.Concrete(PythonLanguageStrategy.attrReadAuxFnName("attr")), call.methodName)
        assertEquals(IsMetavar(MetavarAtom.create("\$X")), call.obj)
        assertEquals(IsMetavar(MetavarAtom.create("\$Y")), call.result)
    }

    @Test fun attributeStoreIsUnsupported() {
        val (r, failures) = convert("\$X.attr = source()")
        assertNull(r)
        assertTrue(failures.keys.any { it.contains("Assignment_target_not_metavar") }, "got $failures")
    }

    @Test fun subscriptStoreIsUnsupported() {
        val (r, failures) = convert("\$X[\$Y] = source()")
        assertNull(r)
        assertTrue(failures.keys.any { it.contains("Assignment_target_not_metavar") }, "got $failures")
    }

    private fun ParamCondition?.fieldModifierChain(): String? {
        val conds = when (this) {
            is ParamCondition.And -> conditions
            null -> emptyList()
            else -> listOf(this)
        }
        val mod = conds.filterIsInstance<ParamCondition.ParamModifier>().singleOrNull() ?: return null
        val value = mod.modifier.value as? SignatureModifierValue.StringValue ?: return null
        return if (value.paramName == PythonLanguageStrategy.FIELD_AUX_MODIFIER) value.value else null
    }

    @Test fun subscriptReadOnCallReceiverAddsElementModifier() {
        val a = convertOk("source()[0]")
        val call = a.actions.single() as MethodCall
        assertEquals(SignatureName.Concrete("source"), call.methodName)
        assertEquals(PythonLanguageStrategy.INDEX_AUX_FIELD_NAME, call.result.fieldModifierChain())
    }

    @Test fun subscriptReadOnBareMetavarUnsupported() {
        val (r, failures) = convert("\$X[0]")
        assertNull(r)
        assertTrue(failures.keys.any { it.contains("Subscript_obj_not_defined") }, "got $failures")
    }

    @Test fun nestedSubscriptChainsElementModifiers() {
        val a = convertOk("source()[0][1]")
        val call = a.actions.single() as MethodCall
        assertEquals(
            PythonLanguageStrategy.joinFieldNames(
                PythonLanguageStrategy.INDEX_AUX_FIELD_NAME,
                PythonLanguageStrategy.INDEX_AUX_FIELD_NAME,
            ),
            call.result.fieldModifierChain(),
        )
    }

    @Test fun metavarToMetavarAssignFailsGracefully() {
        val (r, failures) = convert("\$X = \$Y")
        assertNull(r)
        assertTrue(failures.isNotEmpty(), "expected a recorded failure reason, got $failures")
    }

    @Test fun augmentedAssignFails() {
        val (r, failures) = convert("\$X += \$Y")
        assertNull(r)
        assertTrue(failures.keys.any { it.contains("Assignment_op_+=") }, "got $failures")
    }

    @Test fun assignBindsResult() {
        val a = convertOk("\$X = source()")
        val call = a.actions.single() as MethodCall
        assertEquals(SignatureName.Concrete("source"), call.methodName)
        assertEquals(IsMetavar(MetavarAtom.create("\$X")), call.result)
    }

    @Test fun annotatedAssignAddsTypeConstraint() {
        val a = convertOk("\$X: int = source()")
        val call = a.actions.single() as MethodCall
        val result = call.result as ParamCondition.And
        assertTrue(IsMetavar(MetavarAtom.create("\$X")) in result.conditions)
        assertTrue(ParamCondition.TypeIs(pythonNamed("int")) in result.conditions)
    }

    @Test fun nestedCallArgumentLinearizes() {
        val a = convertOk("outer(inner(\$X))")
        assertEquals(2, a.actions.size)
        val inner = a.actions[0] as MethodCall
        val outer = a.actions[1] as MethodCall
        assertEquals(SignatureName.Concrete("inner"), inner.methodName)
        assertEquals(SignatureName.Concrete("outer"), outer.methodName)
        val innerResult = inner.result as IsMetavar
        assertEquals(ParamConstraint.Concrete(listOf(innerResult)), outer.params)
    }

    @Test fun bareEllipsisHasBothFlags() {
        val a = convertOk("...")
        assertTrue(a.actions.isEmpty())
        assertTrue(a.hasEllipsisInTheBeginning)
        assertTrue(a.hasEllipsisInTheEnd)
    }

    @Test fun sequenceWithEllipsisSeparator() {
        val a = convertOk("\$DB.execute(\$Q, ...)\n...\n\$DB.close()")
        assertEquals(2, a.actions.size)
        assertEquals(SignatureName.Concrete("execute"), (a.actions[0] as MethodCall).methodName)
        assertEquals(SignatureName.Concrete("close"), (a.actions[1] as MethodCall).methodName)
        assertEquals(false, a.hasEllipsisInTheBeginning)
        assertEquals(false, a.hasEllipsisInTheEnd)
    }

    @Test fun keywordArgument() {
        val a = convertOk("foo(x=\$V)")
        val call = a.actions.single() as MethodCall
        val params = call.params as ParamConstraint.Partial
        assertEquals(
            listOf(ParamPattern(ParamPosition.Named(PythonLanguageStrategy.kwargClassifier("x")), IsMetavar(MetavarAtom.create("\$V")))),
            params.params,
        )
    }

    @Test fun funcDefEmitsSignature() {
        val a = convertOk("def \$F(...): ...")
        val sig = a.actions.single() as MethodSignature
        assertEquals(SignatureName.MetaVar("\$F"), sig.methodName)
        assertEquals(ParamConstraint.Partial(emptyList()), sig.params)
        assertNull(sig.enclosingClassMetavar)
        assertTrue(a.hasEllipsisInTheEnd)
    }

    @Test fun funcDefWithAnnotatedParamAndBody() {
        val a = convertOk("def \$F(\$X: str): sink(\$X)")
        val sig = a.actions[0] as MethodSignature
        assertEquals(
            listOf(
                ParamPattern(ParamPosition.Concrete(0), IsMetavar(MetavarAtom.create("\$X"))),
                ParamPattern(ParamPosition.Concrete(0), ParamCondition.TypeIs(pythonNamed("str"))),
            ),
            sig.params.params,
        )
        val call = a.actions[1] as MethodCall
        assertEquals(SignatureName.Concrete("sink"), call.methodName)
    }

    @Test fun returnEmitsMethodExit() {
        val a = convertOk("def \$F(...): return \$X")
        val exit = a.actions.last() as MethodExit
        assertEquals(listOf(IsMetavar(MetavarAtom.create("\$X"))), exit.retVals)
    }

    @Test fun withStmtBindsAsTarget() {
        val a = convertOk("with open(\$F) as \$X:\n    ...")
        val call = a.actions.single() as MethodCall
        assertEquals(SignatureName.Concrete("open"), call.methodName)
        assertEquals(ParamConstraint.Concrete(listOf(IsMetavar(MetavarAtom.create("\$F")))), call.params)
        assertEquals(IsMetavar(MetavarAtom.create("\$X")), call.result)
        assertTrue(a.hasEllipsisInTheEnd)
    }

    @Test fun classDefWithMethodPatchesEnclosingClass() {
        val a = convertOk("class \$C(flask.views.View):\n    def \$F(...): ...")
        val sig = a.actions.first() as MethodSignature
        assertEquals("\$C", sig.enclosingClassMetavar)
        assertEquals(
            listOf(ClassConstraint.SuperType(pythonNamed("flask.views.View"))),
            sig.enclosingClassConstraints,
        )
        assertEquals(SignatureName.MetaVar("\$F"), sig.methodName)
    }

    @Test fun classDefWithEllipsisBodyEmitsAnySignature() {
        val a = convertOk("class \$C(...): ...")
        val sig = a.actions.single() as MethodSignature
        assertEquals(SignatureName.AnyName, sig.methodName)
        assertEquals("\$C", sig.enclosingClassMetavar)
        assertTrue(a.hasEllipsisInTheEnd)
    }

    @Test fun decoratedFuncDefHasModifier() {
        val a = convertOk("@app.route(\"/\")\ndef \$F(...): ...")
        val sig = a.actions.first() as MethodSignature
        assertEquals(
            listOf(SignatureModifier(pythonNamed("app.route"), SignatureModifierValue.StringValue("value", "/"))),
            sig.modifiers,
        )
    }

    @Test fun unsupportedReturnsNullAndRecordsReason() {
        val (result, failures) = convert("if \$X:\n    return \$Y")
        assertNull(result)
        assertTrue(failures.isNotEmpty(), "expected a recorded failure reason, got $failures")
    }

    @Test fun starArgumentFails() {
        val (r, failures) = convert("f(*\$ARGS)")
        assertNull(r)
        assertTrue(failures.keys.any { it.contains("Call_star_argument") }, "got $failures")
    }
}
