package org.opentaint.semgrep

import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS
import org.opentaint.semgrep.util.SampleBasedTest
import kotlin.test.Test

@TestInstance(PER_CLASS)
class StrConcatTest : SampleBasedTest(configurationRequired = true) {
    @Test
    fun `test rule with ellipsis string concat`() = runTest<strconcat.RuleWithEllipsisStringConcat>()

    @Test
    fun `test rule with ellipsis concat`() = runTest<strconcat.RuleWithEllipsisConcat>()

    @Test
    @Disabled // TODO: support string concat with concrete string
    fun `test rule with concrete string concat`() = runTest<strconcat.RuleWithConcreteStringConcat>()

    @Test
    fun `test rule with metavar concat`() = runTest<strconcat.RuleWithMetavarConcat>()

    @Test
    fun `test rule with multiple metavar concat`() = runTest<strconcat.RuleWithMultipleMetavarConcat>()

    @AfterAll
    fun close() {
        closeRunner()
    }
}