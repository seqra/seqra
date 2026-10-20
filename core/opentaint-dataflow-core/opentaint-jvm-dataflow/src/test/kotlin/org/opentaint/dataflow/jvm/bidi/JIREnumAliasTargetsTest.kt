package org.opentaint.dataflow.jvm.bidi

import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.ap.ifds.ClassStaticAccessor
import org.opentaint.dataflow.ap.ifds.FieldAccessor
import org.opentaint.dataflow.bidi.ap.Direction
import org.opentaint.dataflow.bidi.interp.CallStage
import org.opentaint.dataflow.bidi.interp.RuleView
import org.opentaint.dataflow.bidi.interp.StageKind
import org.opentaint.dataflow.bidi.interp.StaticOperation
import org.opentaint.dataflow.jvm.ap.ifds.JIRCallResolver.MethodResolutionResult
import org.opentaint.dataflow.jvm.ap.ifds.JIRLocalAliasAnalysis
import org.opentaint.dataflow.jvm.ap.ifds.JIRLocalAliasAnalysis.AliasAccessor
import org.opentaint.dataflow.jvm.ap.ifds.JIRLocalAliasAnalysis.AliasApInfo
import org.opentaint.dataflow.jvm.ap.ifds.analysis.forEachAliasPathAtStatement
import org.opentaint.dataflow.jvm.bidi.fixtures.JIRCallTestFixtures
import org.opentaint.dataflow.jvm.bidi.interp.JIRCallPlanBuilder
import org.opentaint.ir.api.jvm.cfg.JIRAssignInst
import org.opentaint.ir.api.jvm.cfg.JIRFieldRef
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class JIREnumAliasTargetsTest : BidiJIRTestUtils() {
    @Test fun `injected call aliases drop only enum constant roots before edge and writer creation`() {
        val fixture = JIRCallTestFixtures(cp)
        val at = fixture.call("staticExternal")
        fun alias(name: String, type: String = OWNER, fieldOwner: String = OWNER,
                  root: String = OWNER, base: AccessPathBase = AccessPathBase.ClassStatic,
                  descendant: Boolean = false) = AliasApInfo(base, buildList {
            add(AliasAccessor.Static(root))
            add(AliasAccessor.Field(fieldOwner, name, type))
            if (descendant) add(AliasAccessor.Field(OWNER, "payload", "java.lang.Object"))
        })
        val offered = listOf(alias("A"), alias("A", descendant = true),
            alias("A", root = "$OWNER\$1"), alias("BY_PROTO", "java.lang.Object"),
            alias("selected"), alias("A", fieldOwner = "missing.Owner"),
            alias("A", type = "missing.Type"), alias("payload", "java.lang.Object"),
            alias("A", base = AccessPathBase.Argument(99)))
        val expected = offered.drop(3).map { it.base to it.accessors }.toSet()
        for (view in RuleView.entries) {
            val plan = JIRCallPlanBuilder(manager, interp.ruleForms(view), checker, interp.typeFilters,
                key(at.location.method), at, fixture.expr(at),
                { _, _, _ -> listOf(MethodResolutionResult.MethodResolutionFailed) }, epResolver, emptyMap(),
                persistedAliases = { _, _ -> offered }).build()
            val aliases = plan.stages.filterIsInstance<CallStage.Edges>().single { it.kind == StageKind.ALIASES }
            val actual = aliases.summary.edges.map { edge ->
                edge.edge.to.base to edge.edge.to.path.map { index ->
                    when (val accessor = manager.accessors.accessor(index)) {
                        is ClassStaticAccessor -> AliasAccessor.Static(accessor.typeName)
                        is FieldAccessor -> AliasAccessor.Field(accessor.className, accessor.fieldName, accessor.fieldType)
                        else -> error("Unexpected alias accessor $accessor")
                    }
                }
            }.toSet()
            assertEquals(expected, actual, "Both FULL and GROUP must filter injected aliases")
            assertEquals(expected.count { it.first == AccessPathBase.ClassStatic },
                aliases.summary.staticOperations.filterIsInstance<StaticOperation.Write>().size)
        }
    }

    @Test fun `statement aliases are filtered but shared analysis and direct static operations remain`() {
        val interpreter = interp(aliasParams = JIRLocalAliasAnalysis.Params(useAliasAnalysis = true))
        val writeMethod = method("aliasFieldWrite", OWNER)
        val write = writeMethod.instList.instructions.filterIsInstance<JIRAssignInst>()
            .single { (it.lhv as? JIRFieldRef)?.field?.name == "payload" }
        val receiver = assertIs<JIRFieldRef>(write.lhv).instance!!
        val receiverBase = org.opentaint.dataflow.jvm.ap.ifds.MethodFlowFunctionUtils.accessPathBase(receiver)!!
        var rawEnumAliasSeen = false
        interpreter.entries[writeMethod].aliasAnalysis!!.forEachAliasPathAtStatement(write, receiverBase) { base, path ->
            if (base == AccessPathBase.ClassStatic && (path.getOrNull(1) as? FieldAccessor)?.fieldName == "A")
                rawEnumAliasSeen = true
        }
        assertTrue(rawEnumAliasSeen, "The shared alias engine must retain the enum alias")
        for (view in RuleView.entries) {
            val summary = interpreter.forms(key(writeMethod)).statement(Direction.FORWARD, write, view)
            assertFalse(summary.edges.any { it.edge.to.base == AccessPathBase.ClassStatic },
                "The new core must remove the constant-rooted alias edge before writer registration")
            assertTrue(summary.staticOperations.isEmpty())

            val readMethod = method("directRead", OWNER)
            val read = readMethod.instList.instructions.filterIsInstance<JIRAssignInst>()
                .first { (it.rhv as? JIRFieldRef)?.field?.name == "A" }
            val readSummary = interpreter.forms(key(readMethod)).statement(Direction.FORWARD, read, view)
            assertEquals(1, readSummary.staticOperations.filterIsInstance<StaticOperation.Read>().size,
                "An explicit enum read stays available")

            val directMethod = method("directWrite", OWNER)
            val direct = directMethod.instList.instructions.filterIsInstance<JIRAssignInst>()
                .single { (it.lhv as? JIRFieldRef)?.field?.name == "selected" }
            val directSummary = interpreter.forms(key(directMethod)).statement(Direction.FORWARD, direct, view)
            assertEquals(1, directSummary.staticOperations.filterIsInstance<StaticOperation.Write>().size,
                "An explicit static assignment stays available")

            val initializer = method("<clinit>", OWNER)
            val initializeConstant = initializer.instList.instructions.filterIsInstance<JIRAssignInst>()
                .single { (it.lhv as? JIRFieldRef)?.field?.name == "A" }
            val initializerSummary = interpreter.forms(key(initializer))
                .statement(Direction.FORWARD, initializeConstant, view)
            assertEquals(1, initializerSummary.staticOperations.filterIsInstance<StaticOperation.Write>().size,
                "The real enum-constant initializer assignment stays available")
        }
    }

    companion object { private const val OWNER = "sample.bidi.BidiEnumAliasSample" }
}
