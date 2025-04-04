package org.opentaint.ir.configuration

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class UtilTest {
    @Test
    fun testExtractAlternatives() {
        val pkgMatcher = NamePatternMatcher("(org\\.opentaint-ir)|.*\\.org\\.company")
        val classNameMatcher = NamePatternMatcher(".*Re(quest|sponse)|ClassName|.*|Class(A)*")

        val classMatcher = ClassMatcher(pkgMatcher, classNameMatcher)
        val alternatives = classMatcher.extractAlternatives()

        require(alternatives.size == 10)

        val extractPackageMatcher = alternatives.count {
            it.pkg is NameExactMatcher && (it.pkg as NameExactMatcher).name == "org.opentaint.ir"
        }
        require(extractPackageMatcher == 5)

        val patternPackageMatcher = alternatives.count {
            it.pkg is NamePatternMatcher && (it.pkg as NamePatternMatcher).pattern == ".*\\.org\\.company"
        }
        require(patternPackageMatcher == 5)

        val requestClassMatcher = alternatives.count {
            it.classNameMatcher is NamePatternMatcher && (it.classNameMatcher as NamePatternMatcher).pattern == ".*Request"
        }
        require(requestClassMatcher == 2)

        val responseClassMatcher = alternatives.count {
            it.classNameMatcher is NamePatternMatcher && (it.classNameMatcher as NamePatternMatcher).pattern == ".*Response"
        }
        require(responseClassMatcher == 2)

        val classNameMatcherValue = alternatives.count {
            it.classNameMatcher is NameExactMatcher && (it.classNameMatcher as NameExactMatcher).name == "ClassName"
        }
        require(classNameMatcherValue == 2)

        val allClassNamesMatcher = alternatives.count {
            it.classNameMatcher is NamePatternMatcher && (it.classNameMatcher as NamePatternMatcher).pattern == ".*"
        }
        require(allClassNamesMatcher == 2)

        val classAPattern = alternatives.count {
            it.classNameMatcher is NamePatternMatcher && (it.classNameMatcher as NamePatternMatcher).pattern == "Class(A)*"
        }
        require(classAPattern == 2)
    }

    @Test
    fun splitOnQuestionMarkWithoutTheMark() {
        val emptyLine = ""
        val simpleLine = "abc"

        assertSame(emptyLine, emptyLine.splitOnQuestionMark().single())
        assertSame(simpleLine, simpleLine.splitOnQuestionMark().single())
    }

    @Test
    fun splitOnQuestionMarkTest() {
        val line = "zxc?(asd(qwe)?(bnm))?"

        val result = line.splitOnQuestionMark()

        assertEquals(6, result.size)

        assertTrue(result.singleOrNull { it == "zxc(asd(qwe)(bnm))" } != null)
        assertTrue(result.singleOrNull { it == "zxc(asd(bnm))" } != null)
        assertTrue(result.singleOrNull { it == "zxc" } != null)
        assertTrue(result.singleOrNull { it == "zx" } != null)
        assertTrue(result.singleOrNull { it == "zx(asd(qwe)(bnm))" } != null)
        assertTrue(result.singleOrNull { it == "zx(asd(bnm))" } != null)
    }

    @Test
    fun incorrectQuestionMarkSequences() {
        val line = "abc)?"
        assertSame(line, line.splitOnQuestionMark().single())
    }

    @Test
    fun questionMarkOnGroup() {
        val line = "[a-z]?"
        assertSame(line, line.splitOnQuestionMark().single())
    }
}