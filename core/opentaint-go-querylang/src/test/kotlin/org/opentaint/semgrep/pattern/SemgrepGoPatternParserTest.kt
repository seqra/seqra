package org.opentaint.semgrep.pattern

import org.opentaint.semgrep.go.pattern.CallExpr
import org.opentaint.semgrep.go.pattern.ConcreteName
import org.opentaint.semgrep.go.pattern.DeepExpr
import org.opentaint.semgrep.go.pattern.Ellipsis
import org.opentaint.semgrep.go.pattern.EllipsisArgPrefix
import org.opentaint.semgrep.go.pattern.EllipsisStmt
import org.opentaint.semgrep.go.pattern.FuncDecl
import org.opentaint.semgrep.go.pattern.Identifier
import org.opentaint.semgrep.go.pattern.ImportDecl
import org.opentaint.semgrep.go.pattern.Metavar
import org.opentaint.semgrep.go.pattern.MetavarName
import org.opentaint.semgrep.go.pattern.SelectorExpr
import org.opentaint.semgrep.go.pattern.SemgrepGoPattern
import org.opentaint.semgrep.go.pattern.SemgrepGoPatternParser
import org.opentaint.semgrep.go.pattern.SemgrepGoPatternParsingResult
import org.opentaint.semgrep.go.pattern.StringEllipsis
import org.opentaint.semgrep.go.pattern.StringLiteral
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class SemgrepGoPatternParserTest {
    private val parser = SemgrepGoPatternParser()

    private fun parse(pattern: String): SemgrepGoPattern {
        val r = parser.parseSemgrepGoPattern(pattern)
        assertTrue(r is SemgrepGoPatternParsingResult.Ok, "Expected Ok for `$pattern`, got $r")
        return r.pattern
    }

    /** Walks the AST and returns the first pattern matching the predicate, or null. */
    private fun find(p: SemgrepGoPattern, pred: (SemgrepGoPattern) -> Boolean): SemgrepGoPattern? {
        if (pred(p)) return p
        for (c in p.children) {
            find(c, pred)?.let { return it }
        }
        return null
    }

    @Test fun ellipsis() {
        val ast = parse("...")
        assertNotNull(find(ast) { it is EllipsisStmt || it is Ellipsis })
    }

    @Test fun callEllipsis() {
        val ast = parse("f(...)")
        val call = find(ast) { it is CallExpr } as? CallExpr
        assertNotNull(call)
        assertTrue(call.args is EllipsisArgPrefix)
    }

    @Test fun fmtPrintln() {
        val ast = parse("fmt.Println(\$X)")
        val call = find(ast) { it is CallExpr } as? CallExpr
        assertNotNull(call)
        val sel = call.fn as? SelectorExpr
        assertNotNull(sel)
        assertEquals(ConcreteName("Println"), sel.sel)
        // obj is fmt identifier
        val obj = sel.obj
        assertTrue(
            obj is Identifier && obj.name == ConcreteName("fmt"),
            "Expected obj to be Identifier(fmt), got $obj"
        )
        // Args contain $X metavar
        assertNotNull(find(call) { it is Metavar && it.name == "\$X" })
    }

    @Test fun deepEllipsis() {
        val ast = parse("<... \$X ...>")
        assertNotNull(find(ast) { it is DeepExpr })
    }

    @Test fun funcSig() {
        val ast = parse("func \$F(\$X int) int")
        val decl = find(ast) { it is FuncDecl } as? FuncDecl
        assertNotNull(decl)
        assertEquals(MetavarName("\$F"), decl.name)
    }

    @Test fun importStmt() {
        val ast = parse("import \"database/sql\"")
        assertNotNull(find(ast) { it is ImportDecl })
    }

    @Test fun metavarString() {
        val ast = parse("\"\$STR\"")
        val sl = find(ast) { it is StringLiteral && it.content is MetavarName } as? StringLiteral
        assertNotNull(sl)
        assertEquals(MetavarName("\$STR"), sl.content)
    }

    @Test fun ellipsisStringLit() {
        val ast = parse("\"...\"")
        assertNotNull(find(ast) { it is StringEllipsis })
    }

    @Test fun bareEllipsisParam() {
        val ast = parse("func f(...) {}")
        assertNotNull(find(ast) { it is FuncDecl })
    }

    @Test fun metavarEllipsisParam() {
        val ast = parse("func f(\$...ARGS) {}")
        assertNotNull(find(ast) { it is FuncDecl })
    }

    /** Collects every pattern node in the AST (self + descendants). */
    private fun collect(p: SemgrepGoPattern): List<SemgrepGoPattern> =
        listOf(p) + p.children.flatMap { collect(it) }

    private fun metavars(pattern: String): List<Metavar> =
        collect(parse(pattern)).filterIsInstance<Metavar>()

    @Test fun starredMetavarInCallArgument() {
        val y = metavars("Sink(\$*Y)").single { it.name == "\$Y" }
        assertTrue(y.star, "expected \$*Y to be starred")
    }

    /** Star count tolerating a parse failure (a retired/invalid form yields no starred metavar). */
    private fun starCount(pattern: String): Int {
        val r = parser.parseSemgrepGoPattern(pattern)
        return if (r is SemgrepGoPatternParsingResult.Ok)
            collect(r.pattern).filterIsInstance<Metavar>().count { it.star }
        else 0
    }

    @Test fun prefixStarNotSuffixMarksTheMetavar() {
        // The star is a `$*` prefix bound into the metavar token. `$Y * z` stays multiplication,
        // and the retired suffix form `$Y*` is no longer a starred metavar.
        assertEquals(1, starCount("Sink(\$*Y)"), "\$*Y must be a star")
        assertEquals(0, starCount("Sink(\$Y * z)"), "\$Y * z must not be a star")
        assertEquals(0, starCount("Sink(\$Y*)"), "retired suffix \$Y* must not be a star")
    }

    @Test fun plainMetavarIsNotStarred() {
        val y = metavars("Sink(\$Y)").single { it.name == "\$Y" }
        assertTrue(!y.star, "plain \$Y must not be starred")
    }

    @Test fun starredMetavarOnAssignmentLhs() {
        val x = metavars("\$*X = Source()").single { it.name == "\$X" }
        assertTrue(x.star, "expected LHS \$*X to be starred")
    }

    private fun typedMetavars(pattern: String): List<org.opentaint.semgrep.go.pattern.TypedMetavar> =
        collect(parse(pattern)).filterIsInstance<org.opentaint.semgrep.go.pattern.TypedMetavar>()

    @Test fun starredTypedMetavar() {
        // `($*Y : SomeType)` parses to a starred typed metavar carrying its type constraint.
        val tm = typedMetavars("Sink((\$*Y : SomeType))").single { it.name == "\$Y" }
        assertTrue(tm.star, "expected (\$*Y : SomeType) to be a starred typed metavar")
    }

    @Test fun plainTypedMetavarIsNotStarred() {
        // `($Y : SomeType)` stays an unstarred typed metavar (byte-identical to before).
        val tm = typedMetavars("Sink((\$Y : SomeType))").single { it.name == "\$Y" }
        assertTrue(!tm.star, "plain (\$Y : SomeType) must not be starred")
    }

    @Test fun retiredSuffixTypedMetavarIsNotStarred() {
        // The retired suffix forms `($Y* : T)` and the spaced `($Y * : T)` no longer denote a
        // starred typed metavar: the star is now a `$*` prefix, so neither parses as one.
        for (p in listOf("Sink((\$Y* : SomeType))", "Sink((\$Y * : SomeType))")) {
            val r = parser.parseSemgrepGoPattern(p)
            val starred = r is SemgrepGoPatternParsingResult.Ok &&
                typedMetavars(p).any { it.star }
            assertTrue(!starred, "`$p` must not parse as a starred typed metavar; got $r")
        }
    }

    @Test fun starredTypedReceiverParses() {
        // Typed receiver form `($*C : *exec.Cmd).Run()` parses with both star and the type restored.
        // The `*` in `*exec.Cmd` is a pointer type, distinct from the metavar's `$*` star prefix.
        val tm = typedMetavars("(\$*C : *exec.Cmd).Run()").single { it.name == "\$C" }
        assertTrue(tm.star, "expected (\$*C : *exec.Cmd) receiver to be a starred typed metavar")
    }

    @Test fun prefixDerefStillParses() {
        // `*p` is a prefix deref (STAR precedes the operand), not a starred metavar.
        val ast = parse("*p")
        assertEquals(0, collect(ast).filterIsInstance<Metavar>().count { it.star })
    }

    @Test fun binaryMulStillParses() {
        // `a*b` is multiplication; no starred metavars and still a valid parse.
        val ast = parse("a*b")
        assertTrue(ast !is SemgrepGoPattern.Raw)
        assertEquals(0, collect(ast).filterIsInstance<Metavar>().count { it.star })
    }

    @Test fun structuralSmokeTest() {
        // 5 representative patterns -> AST non-Raw
        val patterns = listOf(
            "fmt.Println(\$X)",
            "\$X = \$Y + \$Z",
            "for ... { }",
            "&T{...}",
            "if \$X { return \$Y }",
        )
        for (p in patterns) {
            val ast = parse(p)
            assertTrue(ast !is SemgrepGoPattern.Raw, "Pattern `$p` produced Raw fallback")
        }
    }
}
