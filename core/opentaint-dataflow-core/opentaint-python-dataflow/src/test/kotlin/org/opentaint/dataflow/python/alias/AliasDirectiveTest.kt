package org.opentaint.dataflow.python.alias

import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.python.alias.AliasDirective.Kind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class AliasDirectiveTest {
    private fun paths(directive: String): List<Pair<Kind, AliasApInfo>> =
        AliasDirective.parse(directive).byLine.values.single()

    private fun ap(arg: Int, vararg accessors: AliasAccessor) =
        AliasApInfo(AccessPathBase.Argument(arg), accessors.toList())

    @Test
    fun `parses bare argument`() {
        assertEquals(listOf(Kind.PRESENT to ap(0)), paths("# alias: arg0"))
    }

    @Test
    fun `parses chained field and element accessors`() {
        val expected = ap(1, AliasAccessor.Field("box"), AliasAccessor.Array, AliasAccessor.Field("data"))
        assertEquals(listOf(Kind.PRESENT to expected), paths("# alias: arg1.box[].data"))
    }

    @Test
    fun `parses absent and gap markers`() {
        val expected = listOf(
            Kind.PRESENT to ap(0, AliasAccessor.Field("a")),
            Kind.ABSENT to ap(1),
            Kind.GAP to ap(0, AliasAccessor.Field("b")),
        )
        assertEquals(expected, paths("# alias: arg0.a, !arg1, ?arg0.b"))
    }

    @Test
    fun `keys directives by one-based line and reads depth`() {
        val src = """
            # depth: 2
            def f(x):
                # alias: arg0
                alias_sink(x)
        """.trimIndent()
        val parsed = AliasDirective.parse(src)
        assertEquals(2, parsed.depth)
        assertEquals(setOf(3), parsed.byLine.keys)
    }

    @Test
    fun `depth defaults to zero`() {
        assertEquals(0, AliasDirective.parse("# alias: arg0").depth)
    }

    @Test
    fun `rejects malformed paths`() {
        assertFailsWith<IllegalStateException> { paths("# alias: x0") }
        assertFailsWith<IllegalStateException> { paths("# alias: arg0.") }
        assertFailsWith<IllegalStateException> { paths("# alias: arg0@") }
    }
}
