package org.opentaint.semgrep.pattern

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RequireParserTest {
    @Test
    fun `single identifier`() {
        val r = parseRequires("CONCAT")
        assertTrue(r is SemgrepTaintLabel)
        assertEquals("CONCAT", r.label)
    }

    @Test
    fun `and not expression`() {
        val r = parseRequires("TRANSFORMER and not DOMSOURCE")
        assertTrue(r is SemgrepTaintAnd)
        assertTrue(r.left is SemgrepTaintLabel)
        assertEquals("TRANSFORMER", (r.left as SemgrepTaintLabel).label)
        assertTrue(r.right is SemgrepTaintNot)
        assertTrue((r.right as SemgrepTaintNot).child is SemgrepTaintLabel)
        assertEquals("DOMSOURCE", ((r.right as SemgrepTaintNot).child as SemgrepTaintLabel).label)
    }

    @Test
    fun `complex expression with parentheses`() {
        val s = "(REQ and not STRING_CONCAT) or (REQ and STRING_CONCAT and not NOT_CONCAT)"
        val r = parseRequires(s)
        assertTrue(r is SemgrepTaintOr)
        assertTrue(r.left is SemgrepTaintAnd)
        assertTrue(r.right is SemgrepTaintAnd)
    }

    @Test
    fun `nested and not with multiple terms`() {
        val s = "(USER_INPUT and FACTORY) and not (DTD_DISABLED or FSP or ENTITY_RESOLVER or (PARAM_ENT_DISABLED and GEN_ENT_DISABLED))"
        val r = parseRequires(s)
        assertTrue(r is SemgrepTaintAnd)
        assertTrue(r.left is SemgrepTaintAnd)
        assertTrue(r.right is SemgrepTaintNot)
        val not = r.right as SemgrepTaintNot
        assertTrue(not.child is SemgrepTaintOr)
    }

    @Test
    fun `user input example`() {
        val s = "(USER_INPUT and SAXTRANSFORMER) and not FSP"
        val r = parseRequires(s)
        assertTrue(r is SemgrepTaintAnd)
    }
}
