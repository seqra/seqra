package org.opentaint.dataflow.jvm.bidi

import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.ap.ifds.ClassStaticAccessor
import org.opentaint.dataflow.ap.ifds.FieldAccessor
import org.opentaint.dataflow.bidi.ap.Direction
import org.opentaint.dataflow.bidi.interp.CallStage
import org.opentaint.dataflow.bidi.interp.StageKind
import org.opentaint.dataflow.jvm.ap.ifds.JIRLocalAliasAnalysis
import org.opentaint.dataflow.jvm.ap.ifds.MethodFlowFunctionUtils
import org.opentaint.dataflow.jvm.ap.ifds.analysis.aliasesPersistedThroughCall
import org.opentaint.dataflow.jvm.ap.ifds.analysis.apAccessor
import org.opentaint.dataflow.jvm.bidi.interp.JIRStaticMetadataCollector
import org.opentaint.dataflow.jvm.bidi.fixtures.JIRRuleTestFixtures
import org.opentaint.dataflow.configuration.jvm.Argument
import org.opentaint.dataflow.configuration.jvm.ClassStatic
import org.opentaint.dataflow.configuration.jvm.CopyAllMarks
import org.opentaint.dataflow.configuration.jvm.CopyMark
import org.opentaint.dataflow.configuration.jvm.TaintMark
import org.opentaint.dataflow.configuration.jvm.AssignMark
import org.opentaint.dataflow.bidi.ap.CollectingOut
import org.opentaint.dataflow.bidi.ap.ApMode
import org.opentaint.dataflow.bidi.ap.Reach
import org.opentaint.dataflow.bidi.ap.Layer
import org.opentaint.dataflow.bidi.ap.TaintTree
import org.opentaint.ir.api.jvm.cfg.JIRAssignInst
import org.opentaint.ir.api.jvm.cfg.JIRFieldRef
import org.opentaint.ir.api.jvm.ext.cfg.callExpr
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.assertFalse

/** The static endpoints are produced by the real alias analysis and runtime call-plan builder. */
class JIRStaticDomainProducerTest : BidiJIRTestUtils() {
    @Test
    fun `selected JVM pass rules reject open class-point prefix rewrites before compilation`(): Unit {
        val v = JIRRuleTestFixtures(cp)
        val cls = ClassStatic("sample.bidi.BidiStaticMetadataSample\$Processes")
        for ((from, to) in listOf(Argument(0) to cls, cls to Argument(0))) {
            assertFalse(v.forms().valid(v.pass(listOf(CopyAllMarks(from, to)))))
            assertFalse(v.forms().valid(v.pass(listOf(CopyMark(TaintMark("T"), from, to)))))
        }
    }

    @Test
    fun `static statement alias suffix keeps the collected first class and field`(): Unit {
        val m = method("aliasWrite", "sample.bidi.BidiStaticMetadataSample")
        val write = m.instList.instructions.filterIsInstance<JIRAssignInst>().single { it.lhv is JIRFieldRef }
        val runtime = interp(aliasParams = JIRLocalAliasAnalysis.Params(useAliasAnalysis = true))
        val summary = runtime.forms(key(m)).statement(Direction.FORWARD, write)
        val expected = listOf(ClassStaticAccessor("sample.bidi.BidiStaticMetadataSample\$Boxes"),
            FieldAccessor("sample.bidi.BidiStaticMetadataSample\$Boxes", "box", "sample.bidi.BidiStaticMetadataSample\$Box"))
        val outputs = summary.edges.filter { it.edge.to.base == AccessPathBase.ClassStatic }
            .map { it.edge.to.path.map(manager.accessors::accessor) }.filter { it.size > 2 }
        assertTrue(outputs.isNotEmpty(), "Actual local-write alias must produce a static path: ${summary.edges}")
        val metadata = JIRStaticMetadataCollector(runtime).collect(key(m))
        assertTrue(outputs.all { it.take(2) == expected && it.take(2) in metadata.accesses })
    }

    @Test
    fun `class point source GEN has no input residual field`(): Unit {
        val v = JIRRuleTestFixtures(cp)
        val className = "sample.bidi.BidiStaticMetadataSample\$Processes"
        val rule = v.source(listOf(AssignMark(TaintMark("T"), ClassStatic(className))))
        val forms = v.forms()
        assertTrue(forms.valid(rule))
        val action = forms.markTarget(rule, rule.actionsAfter.single())
        val input = Reach.of(Layer.NORMAL)
        val out = CollectingOut()
        v.ops.applyEdge(input, v.manager.zero, org.opentaint.dataflow.bidi.ap.PathEdge(
            org.opentaint.dataflow.bidi.interp.ZERO_FACT, action, org.opentaint.dataflow.bidi.ap.ExclusionSet.Empty),
            statementEdge = true, ApMode(true, Direction.FORWARD, 1, org.opentaint.dataflow.bidi.ap.MarkMode.FULL), out)
        val facts = assertIs<TaintTree>(out.results.single())
        assertEquals(listOf(ClassStaticAccessor(className)), v.ops.leaves(facts).single().fact.path.map(v.manager.accessors::accessor))
    }

    @Test
    fun `nested enum persisted alias and actual call plan use the declaring binary owner`(): Unit =
        checkProducer("nestedAlias", "sample.bidi.BidiStaticMetadataSample\$Processes", "WEASYPRINT")

    @Test
    fun `inherited static persisted alias and actual call plan use the declaring owner`(): Unit =
        checkProducer("inheritedAlias", "sample.bidi.BidiStaticMetadataSample\$Parent", "inherited")

    private fun checkProducer(name: String, declaring: String, fieldName: String) {
        val method = method(name, "sample.bidi.BidiStaticMetadataSample")
        val load = method.instList.instructions.filterIsInstance<JIRAssignInst>()
            .single { (it.rhv as? JIRFieldRef)?.field?.name == fieldName }
        val ref = load.rhv as JIRFieldRef
        assertEquals(declaring, ref.field.field.enclosingClass.name, "Actual resolved field owner")
        val direct = assertIs<MethodFlowFunctionUtils.StaticRefAccess>(MethodFlowFunctionUtils.mkAccess(ref))
        val expected = listOf(ClassStaticAccessor(declaring),
            FieldAccessor(declaring, fieldName, ref.field.field.type.typeName))
        assertEquals(expected, listOf(direct.classStaticAccessor, direct.accessor))
        val runtime = interp(aliasParams = JIRLocalAliasAnalysis.Params(useAliasAnalysis = true))
        val key = key(method)
        val aliases = assertNotNull(runtime.entries[key].aliasAnalysis)
        val call = method.instList.instructions.single { it.callExpr?.method?.name == "touch" }
        val local = assertIs<AccessPathBase.LocalVar>(MethodFlowFunctionUtils.accessPathBase(load.lhv))
        val held = aliases.aliasesPersistedThroughCall(local, call)
            .filter { it.base == AccessPathBase.ClassStatic }.map { it.accessors.map { a -> a.apAccessor() } }
        assertTrue(held.isNotEmpty(), "Real AA must produce a static path at the call: $held")
        assertEquals(listOf(expected), held, "Typed reference owner=${ref.field.enclosingType.typeName}")
        val metadata = JIRStaticMetadataCollector(runtime).collect(key)
        assertTrue(expected in metadata.accesses)
        val plan = runtime.forms(key).call(Direction.FORWARD, call, assertNotNull(call.callExpr))
        val outputs = plan.stages.filterIsInstance<CallStage.Edges>()
            .filter { it.kind == StageKind.ALIASES }.flatMap { it.summary.edges }
            .filter { it.edge.to.base == AccessPathBase.ClassStatic }
            .map { it.edge.to.path.map(manager.accessors::accessor) }
        assertTrue(expected in outputs, "Actual ALIASES output endpoints=$outputs")
        assertTrue(outputs.all { it in metadata.accesses }, "Every produced static alias is in the frozen metadata domain")
    }
}
