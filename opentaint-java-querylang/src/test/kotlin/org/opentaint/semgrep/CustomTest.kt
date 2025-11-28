package org.opentaint.semgrep

import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS
import org.opentaint.semgrep.util.SampleBasedTest
import kotlin.test.Test

@TestInstance(PER_CLASS)
class CustomTest : SampleBasedTest(configurationRequired = true) {
    @Test
    fun `test simplified rule`() = runTest<custom.springPathInjection1>()

    @Test
    fun `test simplified rule with File creation`() = runTest<custom.springPathInjection2>()

    @Test
    fun `test origin path injection rule`() = runTest<custom.springPathInjectionOrigin>()

    @Test
    @Disabled // todo: array initializer
    fun `test simplified command injection rule`() = runTest<custom.springCommandInjection1>()

    @Test
    @Disabled // todo: taint array element
    fun `test origin command injection rule`() = runTest<custom.springCommandInjectionOrigin>()

    @Test
    fun `test simple xss rule`() = runTest<custom.springXss>()

    @Test
    @Disabled
    fun `test log injection rule`() = runTest<custom.springLogInjection>()

    @AfterAll
    fun close(){
        closeRunner()
    }
}
