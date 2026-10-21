package org.opentaint.semgrep.pattern

import org.opentaint.semgrep.python.pattern.ArgPrefix
import org.opentaint.semgrep.python.pattern.Assign
import org.opentaint.semgrep.python.pattern.Attribute
import org.opentaint.semgrep.python.pattern.BinaryExpr
import org.opentaint.semgrep.python.pattern.Call
import org.opentaint.semgrep.python.pattern.ClassDef
import org.opentaint.semgrep.python.pattern.ConcreteName
import org.opentaint.semgrep.python.pattern.DeepExpr
import org.opentaint.semgrep.python.pattern.Ellipsis
import org.opentaint.semgrep.python.pattern.EllipsisArgPrefix
import org.opentaint.semgrep.python.pattern.EllipsisParam
import org.opentaint.semgrep.python.pattern.EllipsisStmt
import org.opentaint.semgrep.python.pattern.FromImportStmt
import org.opentaint.semgrep.python.pattern.FunctionDef
import org.opentaint.semgrep.python.pattern.Identifier
import org.opentaint.semgrep.python.pattern.IfStmt
import org.opentaint.semgrep.python.pattern.ImportStmt
import org.opentaint.semgrep.python.pattern.KeywordArgument
import org.opentaint.semgrep.python.pattern.Metavar
import org.opentaint.semgrep.python.pattern.MetavarName
import org.opentaint.semgrep.python.pattern.Name
import org.opentaint.semgrep.python.pattern.NamedParam
import org.opentaint.semgrep.python.pattern.NoArgs
import org.opentaint.semgrep.python.pattern.ReturnStmt
import org.opentaint.semgrep.python.pattern.SemgrepPythonPattern
import org.opentaint.semgrep.python.pattern.SemgrepPythonPatternParser
import org.opentaint.semgrep.python.pattern.SemgrepPythonPatternParsingResult
import org.opentaint.semgrep.python.pattern.StringEllipsis
import org.opentaint.semgrep.python.pattern.StringLiteral
import org.opentaint.semgrep.python.pattern.Subscript
import org.opentaint.semgrep.python.pattern.UnaryExpr
import org.opentaint.semgrep.python.pattern.UnsupportedPythonElement
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class SemgrepPythonPatternParserTest {
    private val parser = SemgrepPythonPatternParser()

    private fun parse(pattern: String): SemgrepPythonPattern {
        val r = parser.parseSemgrepPythonPattern(pattern)
        assertTrue(r is SemgrepPythonPatternParsingResult.Ok, "Expected Ok for `$pattern`, got $r")
        return r.pattern
    }

    private fun find(p: SemgrepPythonPattern, pred: (SemgrepPythonPattern) -> Boolean): SemgrepPythonPattern? {
        if (pred(p)) return p
        for (c in p.children) find(c, pred)?.let { return it }
        return null
    }

    @Test fun metavar() {
        val ast = parse("\$X")
        assertEquals(Metavar("\$X"), ast)
    }

    @Test fun ellipsis() {
        val ast = parse("...")
        assertNotNull(find(ast) { it is Ellipsis || it is EllipsisStmt })
    }

    @Test fun callEllipsis() {
        val ast = parse("f(...)")
        val call = find(ast) { it is Call } as? Call
        assertNotNull(call)
        assertTrue(call.args is EllipsisArgPrefix, "Expected EllipsisArgPrefix, got ${call.args}")
        assertTrue(call.hasEllipsis)
    }

    @Test fun attributeCall() {
        val ast = parse("foo.bar(\$X)")
        val call = find(ast) { it is Call } as? Call
        assertNotNull(call)
        val attr = call.fn as? Attribute
        assertNotNull(attr)
        assertEquals(ConcreteName("bar"), attr.name)
        assertTrue(
            attr.obj is Identifier && attr.obj.name == ConcreteName("foo"),
            "Expected obj Identifier(foo), got ${attr.obj}"
        )
        assertNotNull(find(call) { it is Metavar && it.name == "\$X" })
    }

    @Test fun deepEllipsis() {
        val ast = parse("<... \$X ...>")
        assertNotNull(find(ast) { it is DeepExpr })
    }

    @Test fun funcDef() {
        val ast = parse("def \$F(...): ...")
        val fn = find(ast) { it is FunctionDef } as? FunctionDef
        assertNotNull(fn)
        assertEquals(MetavarName("\$F"), fn.name)
        assertTrue(fn.params.any { it is EllipsisParam }, "Expected an EllipsisParam, got ${fn.params}")
    }

    @Test fun classDef() {
        val ast = parse("class \$C(...): ...")
        val cls = find(ast) { it is ClassDef } as? ClassDef
        assertNotNull(cls)
        assertEquals(MetavarName("\$C"), cls.name)
    }

    @Test fun importStmt() {
        val ast = parse("import os")
        assertNotNull(find(ast) { it is ImportStmt })
    }

    @Test fun fromImport() {
        val ast = parse("from os import \$X")
        val imp = find(ast) { it is FromImportStmt } as? FromImportStmt
        assertNotNull(imp)
        assertEquals(listOf<Name>(ConcreteName("os")), imp.module)
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

    @Test fun assignment() {
        val ast = parse("\$X = \$Y + \$Z")
        val assign = find(ast) { it is Assign } as? Assign
        assertNotNull(assign)
        assertNotNull(find(assign) { it is BinaryExpr })
    }

    @Test fun augmentedAssign() {
        val ast = parse("\$X += \$Y")
        val assign = find(ast) { it is Assign } as? Assign
        assertNotNull(assign)
        assertEquals("+=", assign.op)
        assertEquals(listOf<SemgrepPythonPattern>(Metavar("\$X")), assign.targets)
        assertEquals(Metavar("\$Y"), assign.value)
    }

    @Test fun keywordArg() {
        val ast = parse("foo(\$K=\$V)")
        assertNotNull(find(ast) { it is KeywordArgument })
    }

    @Test fun subscript() {
        val ast = parse("\$X[\$Y]")
        assertNotNull(find(ast) { it is Subscript })
    }

    @Test fun comparison() {
        val ast = parse("\$X < \$Y")
        val cmp = find(ast) { it is BinaryExpr && it.op == "<" } as? BinaryExpr
        assertNotNull(cmp)
        assertEquals(Metavar("\$X"), cmp.left)
        assertEquals(Metavar("\$Y"), cmp.right)
    }

    @Test fun boolOp() {
        val ast = parse("\$X and \$Y")
        val bool = find(ast) { it is BinaryExpr && it.op == "and" } as? BinaryExpr
        assertNotNull(bool)
    }

    @Test fun notOp() {
        val ast = parse("not \$X")
        val unary = find(ast) { it is UnaryExpr && it.op == "not" } as? UnaryExpr
        assertNotNull(unary)
        assertEquals(Metavar("\$X"), unary.operand)
    }

    @Test fun annotatedDefaultParam() {
        val ast = parse("def f(\$X: int = \$Y): ...")
        val fn = find(ast) { it is FunctionDef } as? FunctionDef
        assertNotNull(fn)
        val param = fn.params.filterIsInstance<NamedParam>().single()
        assertEquals(MetavarName("\$X"), param.name)
        assertTrue(param.annotation is Identifier, "Expected annotation, got ${param.annotation}")
        assertEquals(Metavar("\$Y"), param.default)
    }

    @Test fun ifReturn() {
        val ast = parse("if \$X:\n    return \$Y")
        assertNotNull(find(ast) { it is IfStmt })
        assertNotNull(find(ast) { it is ReturnStmt })
    }

    @Test fun fstringInterpolationUnsupported() {
        val patterns = listOf(
            "f\"...{\$X}...\"",
            "f\"{a.b(...)}\"",
            "f\"\$S{...}...\"",
        )
        for (p in patterns) {
            val r = parser.parseSemgrepPythonPattern(p)
            val failure = assertIs<SemgrepPythonPatternParsingResult.ParserFailure>(r, "Expected ParserFailure for `$p`")
            assertIs<UnsupportedPythonElement>(failure.exception)
        }
    }

    @Test fun fstringWithoutInterpolation() {
        assertEquals(StringEllipsis, parse("f\"...\""))
    }

    @Test fun anonymousMetavarAndWildcard() {
        val ast = parse("foo(\$_, _)")
        val call = assertIs<Call>(ast)
        assertEquals(ArgPrefix(Metavar("_"), ArgPrefix(Identifier(ConcreteName("_")), NoArgs)), call.args)
    }

    @Test fun softKeywordAsName() {
        val ast = parse("match = \$X")
        assertEquals(Assign("=", listOf(Identifier(ConcreteName("match"))), Metavar("\$X"), null), ast)
    }

    @Test fun ellipsisAfterKeywordArgument() {
        val call = assertIs<Call>(parse("foo(x=\$V, ...)"))
        assertEquals(ArgPrefix(KeywordArgument(ConcreteName("x"), Metavar("\$V")), EllipsisArgPrefix(NoArgs)), call.args)
    }

    @Test fun ellipsisAfterDefaultParameter() {
        val def = assertIs<FunctionDef>(parse("def \$F(..., \$D=[], ...):\n  ..."))
        assertEquals(EllipsisParam, def.params.first())
        assertEquals(EllipsisParam, def.params.last())
    }

    @Test fun ellipsisAssignmentTarget() {
        val ast = parse("... = requests.get(\$URL)")
        val assign = assertIs<Assign>(ast)
        assertEquals(listOf<SemgrepPythonPattern>(Ellipsis), assign.targets)
    }

    @Test fun dictEllipsis() {
        val r = parser.parseSemgrepPythonPattern("{..., \"password\": \$P, ...}")
        assertTrue(r is SemgrepPythonPatternParsingResult.Ok, "Expected Ok, got $r")
    }

    @Test fun modernSyntaxParses() {
        val patterns = listOf(
            "if (\$X := foo()):\n    ...",
            "match \$X:\n    case 1:\n        ...",
            "def \$F[T](\$X: T): ...",
            "with (open(\$P) as \$F, open(\$Q) as \$G):\n    ...",
            "@\$APP.routes[0].get(...)\ndef \$F(...): ...",
        )
        for (p in patterns) parse(p)
    }

    @Test fun structuralSmokeTest() {
        val patterns = listOf(
            "foo.bar(\$X)",
            "\$X = \$Y + \$Z",
            "f(...)",
            "def \$F(...): ...",
            "\$DB.execute(\$Q, ...)",
        )
        for (p in patterns) {
            val ast = parse(p)
            assertEquals(
                null,
                find(ast) { it is SemgrepPythonPattern.Raw },
                "Pattern `$p` produced a Raw fallback in: $ast"
            )
        }
    }
}
