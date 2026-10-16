package org.opentaint.dataflow.bidi

import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.ap.ifds.ClassStaticAccessor
import org.opentaint.dataflow.ap.ifds.FieldAccessor
import org.opentaint.dataflow.ap.ifds.MethodContext
import org.opentaint.dataflow.bidi.ap.*
import org.opentaint.dataflow.bidi.engine.*
import org.opentaint.dataflow.bidi.fixtures.StoreFixtures
import org.opentaint.dataflow.bidi.store.CallerRef
import kotlin.test.*

class StaticDomainDiagnosticTest {
    private val f = StoreFixtures()
    private val ap = f.manager
    private val cls = ap.accessors.index(ClassStaticAccessor("example.MissingStatic"))
    private val field = ap.accessors.index(FieldAccessor("example.MissingStatic", "payload", "java.lang.String"))
    private val origin = ap.initial(AccessPathBase.Argument(0), null, Tail.STAR, ExclusionSet.Empty, MarkSlot.STAR)
    private val ref = CallerRef(f.key("caller-context", 3), origin, Layer.DEMAND, f.inst(17))

    @Test
    fun `class guard names full owner origin and headers at the unchanged failure point`() {
        val t = f.mark("excluded-T")
        val e = f.field("excludedField")
        val input = f.fact(STATIC, listOf(cls, field), Tail.STAR,
            MarkSlot.Star(ap.markSetOf(t)), ExclusionSet.of(e))
        val profile = Run1StaticProfiles.build(ap, emptyMap(), mapOf(f.commonMethod to Run1StaticLocalProfile.EMPTY))[f.commonMethod]
        val answers = arrayListOf<InitialAp>()
        val emitter = Run1Abstraction(f.ops, profile, answers::add, { emptyList() }, { _, _ -> }, owner = f.key("callee-context", 29))
        val failure = assertFailsWith<IllegalStateException> { emitter.emit(ref, input) }
        val message = assertNotNull(failure.message)
        for (part in listOf("Static input class is absent", "STATIC_DOMAIN_MISS", "example.MissingStatic",
            "#payload:java.lang.String", "callee=", "entry=29", "callee-context", "caller=", "entry=3",
            "caller-context", "call=17", "callerLayer=DEMAND", "inputKind=FLOW", "inputLayer=NORMAL",
            "excludedField", "excluded-T", "domainClasses=0", "domainFields=0", "fieldCount=1")) {
            assertTrue(part in message, "$part missing from $message")
        }
        assertEquals(listOf(ap.initial(STATIC, null, Tail.STAR, ExclusionSet.Empty, MarkSlot.STAR)), answers,
            "The existing unconditional policy start still precedes the guard")
    }

    @Test
    fun `field miss retains the class and exact missing field`() {
        val footprint = Run1StaticFootprint(emptySet(), classUniverse = setOf(cls))
        val input = assertIs<TaintTree>(f.fact(STATIC, listOf(cls, field)))
        val message = staticDomainMissMessage(ap, f.method, CallerRef(f.method, ap.zero, Layer.NORMAL, f.inst(2)),
            input, footprint, cls, assertNotNull(input.root.child(cls)), field)
        assertTrue(message.startsWith("Static input field is absent"))
        assertTrue("missingField=$field:" in message)
        assertTrue("#payload:java.lang.String" in message)
        assertTrue("inputKind=TAINT" in message)
        assertTrue("domainClasses=1;domainFields=0" in message)
    }

    @Test
    fun `large Unicode symbols and field cohorts have explicit bounded text`() {
        val large = "\uD83D\uDE00".repeat(10000)
        var root = FlowAlgebra.leafNode(FlowLeaf.NONE)
        repeat(100) { index ->
            val id = ap.accessors.index(FieldAccessor(large, "f$index$large", large))
            root = FlowAlgebra.mergeAdd(root, FlowAlgebra.prepend(ap, ap.path(listOf(cls, id)), FlowAlgebra.LEAF_NODE))
        }
        val input = ap.flowTree(STATIC, Layer.NORMAL, ExclusionSet.Empty, MarkSet.EMPTY, root)
        val message = staticDomainMissMessage(ap, f.method, ref, input, Run1StaticFootprint(emptySet()),
            cls, assertNotNull(root.child(cls)))
        assertTrue(message.toByteArray(Charsets.UTF_8).size <= 33000)
        assertTrue("fieldCount=100" in message)
        assertTrue("omittedFields=68" in message)
        assertTrue("diagnosticOmittedUtf16Units=" in message)
        assertFalse("diagnosticOmittedUtf16Units=0;" in message)
        assertFalse('\n' in message)
        assertEquals(100, root.child(cls)?.childData?.size, "The borrowed tree is unchanged")
    }

    @Test
    fun `valid input never formats a diagnostic`() {
        val hostile = object : MethodContext { override fun toString(): String = error("diagnostic must be lazy") }
        val owner = MethodKey(hostile, f.inst(4))
        val profile = Run1StaticProfiles.build(ap, emptyMap(), mapOf(f.commonMethod to
            Run1StaticLocalProfile(accesses = listOf(listOf(cls, field)))))[f.commonMethod]
        val input = f.fact(STATIC, listOf(cls, field))
        val emitter = Run1Abstraction(f.ops, profile, {}, { emptyList() }, { _, _ -> }, owner = owner)
        emitter.emit(CallerRef(f.method, ap.zero, Layer.NORMAL, f.inst(1)), input)
    }

    @Test
    fun `decode failure preserves the original guard category`() {
        val input = assertIs<FlowTree>(f.fact(STATIC, listOf(cls, field), Tail.STAR, MarkSlot.STAR))
        val message = staticDomainMissMessage(ap, f.method, ref, input, Run1StaticFootprint(emptySet()),
            Int.MAX_VALUE, assertNotNull(input.root.child(cls)))
        assertTrue(message.startsWith("Static input class is absent"))
        assertTrue("classId=${Int.MAX_VALUE}" in message)
        assertTrue("diagnosticUnavailable=" in message)
    }
}
