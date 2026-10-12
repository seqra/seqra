package org.opentaint.dataflow.taint

import org.opentaint.dataflow.configuration.CommonCondition
import org.opentaint.dataflow.configuration.isFalse
import org.opentaint.dataflow.configuration.isTrue
import org.opentaint.dataflow.util.Cancellation
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class SignedConditionNormalizerTest {
    private fun atom(name: String): CommonCondition<String> = CommonCondition.Atom(name)
    private fun and(vararg args: CommonCondition<String>) = CommonCondition.And(args.toList())
    private fun or(vararg args: CommonCondition<String>) = CommonCondition.Or(args.toList())
    private fun not(x: CommonCondition<String>) = CommonCondition.Not(x)

    @Test fun `signed complements fold before negative approximation`(): Unit {
        val p = atom("p")
        for (project in listOf(false, true)) {
            assertTrue(SignedConditionNormalizer.normalize(and(p, not(p)), negativeLiteralsAreTrue = project).isFalse())
            assertTrue(SignedConditionNormalizer.normalize(or(p, not(p)), negativeLiteralsAreTrue = project).isTrue())
        }
        assertEquals(p, SignedConditionNormalizer.normalize(and(p, not(atom("q"))), negativeLiteralsAreTrue = true))
        assertEquals(not(p), SignedConditionNormalizer.normalize(not(p)))
    }

    @Test fun `unit propagation proves resolved array contradictions without distribution`(): Unit {
        val root = atom("arg root")
        val element = atom("arg element")
        val expanded = and(or(root, element), not(root), not(element))
        assertTrue(SignedConditionNormalizer.normalize(expanded).isFalse())
        assertTrue(SignedConditionNormalizer.normalize(expanded, negativeLiteralsAreTrue = true).isFalse())
        assertEquals(atom("live"), SignedConditionNormalizer.normalize(or(atom("live"), expanded)))
        assertTrue(SignedConditionNormalizer.normalize(and(or(root, element), not(root))).isFalse().not())
    }

    @Test fun `nonfalse predicates retain their positive read footprint`(): Unit {
        val a = atom("a")
        val b = atom("b")
        for (condition in listOf(or(a, and(a, b)), and(a, or(a, b)))) {
            assertEquals(condition, SignedConditionNormalizer.normalize(condition))
        }
        assertEquals(and(a, b), SignedConditionNormalizer.normalize(and(a, a, b)))
    }

    @Test fun `raw compound negations and complements fold without distributing operators`(): Unit {
        val a = atom("a")
        val b = atom("b")
        assertEquals(or(not(a), b), SignedConditionNormalizer.normalize(not(and(a, not(b)))))
        val compound = or(a, b)
        assertTrue(SignedConditionNormalizer.normalize(and(compound, not(compound))).isFalse())
        val large = CommonCondition.And(List(20) { i -> or(atom("left$i"), atom("right$i")) })
        val folded = SignedConditionNormalizer.normalize(large) as CommonCondition.And
        assertEquals(20, folded.args.size)
        assertTrue(folded.args.all { it is CommonCondition.Or && it.args.size == 2 })
    }

    @Test fun `dual unit propagation proves intrinsic tautologies before negative projection`(): Unit {
        val a = atom("a")
        val b = atom("b")
        val tautology = or(and(a, b), not(a), not(b))
        assertTrue(SignedConditionNormalizer.normalize(tautology).isTrue())
        assertTrue(SignedConditionNormalizer.normalize(or(or(a, b), and(not(a), not(b)))).isTrue())
        assertTrue(SignedConditionNormalizer.normalize(tautology, negativeLiteralsAreTrue = true).isTrue())
        assertEquals(or(and(a, b), not(a)), SignedConditionNormalizer.normalize(or(and(a, b), not(a))))
    }

    @Test fun `normalization preserves truth on independent finite signed assignments`(): Unit {
        val random = Random(10403)
        fun generate(depth: Int): CommonCondition<String> {
            if (depth == 0 || random.nextInt(4) == 0) {
                val leaf = atom(('a'.code + random.nextInt(4)).toChar().toString())
                return if (random.nextBoolean()) leaf else not(leaf)
            }
            val children = List(2 + random.nextInt(2)) { generate(depth - 1) }
            return if (random.nextBoolean()) CommonCondition.And(children) else CommonCondition.Or(children)
        }
        fun evaluate(x: CommonCondition<String>, mask: Int): Boolean = when (x) {
            is CommonCondition.True -> true
            is CommonCondition.Atom -> mask and (1 shl (x.atom.single() - 'a')) != 0
            is CommonCondition.Not -> !evaluate(x.arg, mask)
            is CommonCondition.And -> x.args.all { evaluate(it, mask) }
            is CommonCondition.Or -> x.args.any { evaluate(it, mask) }
        }
        repeat(200) {
            val input = generate(4)
            val output = SignedConditionNormalizer.normalize(input)
            for (mask in 0 until 16) assertEquals(evaluate(input, mask), evaluate(output, mask), "case=$it mask=$mask")
        }
    }

    @Test fun `deep negations normalize iteratively`(): Unit {
        var input: CommonCondition<String> = atom("deep")
        repeat(32768) { input = not(input) }
        assertEquals(atom("deep"), SignedConditionNormalizer.normalize(input))
    }

    @Test fun `owned junctions survive caller mutation and reject reader mutation`(): Unit {
        val supplied = arrayListOf(atom("a"), atom("b"))
        val output = SignedConditionNormalizer.normalize(CommonCondition.And(supplied)) as CommonCondition.And
        supplied.clear()
        assertEquals(listOf(atom("a"), atom("b")), output.args)
        assertFailsWith<UnsupportedOperationException> { (output.args as MutableList).clear() }
    }

    @Test fun `cancellation rejects empty and nonempty normalization and activation restores results`(): Unit {
        val cancellation = Cancellation().also { it.cancel() }
        for (input in listOf<CommonCondition<String>>(CommonCondition.And(emptyList()), and(atom("a"), atom("b")))) {
            assertFailsWith<Cancellation.Cancelled> { SignedConditionNormalizer.normalize(input, cancellation) }
        }
        cancellation.activate()
        assertEquals(and(atom("a"), atom("b")), SignedConditionNormalizer.normalize(and(atom("a"), atom("b")), cancellation))
    }
}
