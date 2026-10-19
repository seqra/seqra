package org.opentaint.dataflow.jvm.bidi

import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.ap.ifds.ClassStaticAccessor
import org.opentaint.dataflow.ap.ifds.FieldAccessor
import org.opentaint.dataflow.bidi.ap.*
import org.opentaint.dataflow.bidi.interp.*
import org.opentaint.dataflow.jvm.BasicTestUtils
import org.opentaint.dataflow.jvm.ap.ifds.JIRLocalAliasAnalysis.AliasAccessor
import org.opentaint.dataflow.jvm.ap.ifds.JIRLocalAliasAnalysis.AliasApInfo
import org.opentaint.dataflow.jvm.bidi.fixtures.JIRCallTestFixtures
import org.opentaint.dataflow.jvm.bidi.fixtures.JIRFormsTestFixtures
import org.opentaint.ir.api.jvm.cfg.*
import kotlin.test.*

/** Alias value edges already exist in the AP lowering. Their F1 publication needs a write descriptor. */
class JIRStaticAliasMetadataTest : BasicTestUtils() {
    @Test fun `local field alias value has a static write descriptor and no alias read`() {
        val v = JIRFormsTestFixtures(cp)
        val original = v.assign("fieldWrite") { it.lhv is JIRFieldRef }
        val field = original.lhv as JIRFieldRef
        val receiver = JIRLocalVar(900, "localReceiver", field.instance!!.type)
        val inst = JIRAssignInst(original.location, field.copy(instance = receiver), original.rhv)
        val prefix = listOf(v.cls, v.field("sField"))
        val seen = ArrayList<AccessPathBase>()
        val forms = v.forms(aliasPaths = { at, base ->
            assertSame(inst, at)
            seen += base
            listOf(STATIC to prefix, AccessPathBase.Argument(99) to emptyList())
        })
        val summary = forms.build(inst)
        val source = v.base(original.rhv as JIRValue)
        val expected = starEdge(source, emptyList(), STATIC, prefix + v.field("f"))
        assertEquals(listOf(v.base(receiver)), seen)
        assertEquals(expected, summary.edges.single { it.edge.to.base == STATIC }.edge,
            "The full existing alias value edge is unchanged")
        val write = assertIs<StaticOperation.Write>(summary.staticOperations.single())
        assertEquals(expected, write.transfer)
        assertEquals(Pattern(expected.to, ExclusionSet.Empty), write.target)
        assertSame(inst, write.id.form.statement)
        assertTrue(summary.staticOperations.none { it is StaticOperation.Read || it is StaticOperation.MarkInput })
        assertEquals(write.id, forms.build(inst).staticOperations.single().id)
        assertFalse(STATIC in summary.touched, "An alias remains weak; it does not become a definite destination")
    }

    @Test fun `call aliases annotate return receiver and argument value mappings at their actual stage`() {
        val v = JIRCallTestFixtures(cp)
        val original = assertIs<JIRAssignInst>(v.call("instanceCall"))
        val call = assertIs<JIRVirtualCallExpr>(v.expr(original))
        val receiver = JIRLocalVar(700, "receiver", call.instance.type)
        val args = call.args.mapIndexed { index, value -> JIRLocalVar(800 + index, "arg$index", value.type) }
        val inst = JIRAssignInst(original.location, JIRLocalVar(900, "result", original.lhv.type),
            call.copy(instance = receiver, args = args))
        val owner = JIRCallTestFixtures.SAMPLE_CLASS
        val path = listOf(AliasAccessor.Static(owner), AliasAccessor.Field(owner, "sField", "java.lang.Object"))
        val prefix = listOf(v.manager.accessors.index(ClassStaticAccessor(owner)),
            v.manager.accessors.index(FieldAccessor(owner, "sField", "java.lang.Object")))
        val plan = v.builder(inst, persistedAliases = { _, at ->
            assertSame(inst, at)
            listOf(AliasApInfo(STATIC, path))
        }).build()
        val aliases = plan.stages.filterIsInstance<CallStage.Edges>().single { it.kind == StageKind.ALIASES }
        assertSame(Guard.MemoryEffect, aliases.guard)
        assertEquals(CallPoint.REWRITTEN, aliases.from)
        assertEquals(CallPoint.AFTER, aliases.to)
        val origins = setOf(AccessPathBase.This, AccessPathBase.Argument(0), AccessPathBase.Argument(1), AccessPathBase.Return)
        val expected = origins.map { starEdge(it, emptyList(), STATIC, prefix) }.toSet()
        assertEquals(expected, aliases.summary.edges.map { it.edge }.toSet())
        val writes = aliases.summary.staticOperations.map { assertIs<StaticOperation.Write>(it) }
        assertEquals(expected, writes.map { it.transfer }.toSet())
        for (write in writes) {
            assertEquals(plan.stages.indexOf(aliases), write.id.stageOrdinal)
            assertSame(inst, write.id.form.statement)
            assertEquals(Pattern(checkNotNull(write.transfer).to, ExclusionSet.Empty), write.target)
        }
        assertTrue(aliases.summary.staticOperations.none { it is StaticOperation.Read || it is StaticOperation.MarkInput })
        assertTrue(plan.stages.filterIsInstance<CallStage.Edges>().filter { it.kind == StageKind.BIND_BACK }
            .flatMap { it.summary.staticOperations }.none { it is StaticOperation.Write },
            "The ordinary S identity binding is not an actual alias write")
    }

    @Test fun `ordinary alias value does not register a static write or read`() {
        val v = JIRFormsTestFixtures(cp)
        val original = v.assign("fieldWrite") { it.lhv is JIRFieldRef }
        val field = original.lhv as JIRFieldRef
        val receiver = JIRLocalVar(900, "localReceiver", field.instance!!.type)
        val inst = JIRAssignInst(original.location, field.copy(instance = receiver), original.rhv)
        val other = AccessPathBase.Argument(99)
        val prefix = listOf(v.field("g"))
        val summary = v.forms(aliasPaths = { _, _ -> listOf(other to prefix) }).build(inst)
        val expected = starEdge(v.base(original.rhv as JIRValue), emptyList(), other, prefix + v.field("f"))
        assertEquals(expected, summary.edges.single { it.edge.to.base == other }.edge)
        assertTrue(summary.edges.none { it.edge.to.base == STATIC })
        assertTrue(summary.staticOperations.isEmpty())
    }
}
