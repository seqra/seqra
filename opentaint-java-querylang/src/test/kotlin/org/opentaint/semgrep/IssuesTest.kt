package org.opentaint.semgrep

import issues.issue83
import issues.issue83aux
import issues.issue84
import issues.issue85
import issues.issue86
import issues.issue87
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS
import org.opentaint.semgrep.util.SampleBasedTest
import kotlin.test.Test

@TestInstance(PER_CLASS)
class IssuesTest : SampleBasedTest() {
    @Test
    fun `issue 69`() = runTest<issues.issue69>()

    @Test // todo: variable assign
    @Disabled
    fun `issue 70`() = runTest<issues.issue70>()

    @Test
    fun `issue 71`() = runTest<issues.issue71>()

    @Test
    fun `issue 74`() = runTest<issues.issue74>()

    @Test
    fun `issue 75`() = runTest<issues.issue75>()

    @Test
    fun `issue 76`() = runTest<issues.issue76>()

    @Test
    fun `issue 77`() = runTest<issues.issue77>()

    @Test
    fun `issue 78`() = runTest<issues.issue78>()

    @Test
    fun `issue 83 aux`() = runTest<issue83aux>()

    @Test
    @Disabled // todo: sink on string concatenation
    fun `issue 83`() = runTest<issue83>()

    @Test
    @Disabled // todo: array element accessor
    fun `issue 84`() = runTest<issue84>()

    @Test
    fun `issue 85`() = runTest<issue85>()

    @Test
    @Disabled // todo: loop assign vars
    fun `issue 86`() = runTest<issue86>()

    @Test
    @Disabled // todo: rule isn't finding anything
    fun `issue 87`() = runTest<issue87>()

    @AfterAll
    fun close() {
        closeRunner()
    }
}
