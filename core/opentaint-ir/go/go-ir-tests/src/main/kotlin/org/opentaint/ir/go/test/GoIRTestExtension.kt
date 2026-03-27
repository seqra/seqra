package org.opentaint.ir.go.test

import org.junit.jupiter.api.extension.*

/**
 * JUnit 5 extension that provides a shared [GoIRTestBuilder] per test class.
 * The Go server is started once and reused across all test methods.
 *
 * Usage:
 * ```
 * @ExtendWith(GoIRTestExtension::class)
 * class MyTests {
 *     @Test
 *     fun myTest(builder: GoIRTestBuilder) { ... }
 * }
 * ```
 */
class GoIRTestExtension : BeforeAllCallback, AfterAllCallback, ParameterResolver {
    private lateinit var builder: GoIRTestBuilder

    override fun beforeAll(context: ExtensionContext) {
        builder = GoIRTestBuilder()
        context.getStore(NAMESPACE).put(KEY, builder)
    }

    override fun afterAll(context: ExtensionContext) {
        val b = context.getStore(NAMESPACE).get(KEY) as? GoIRTestBuilder
        b?.close()
    }

    override fun supportsParameter(
        parameterContext: ParameterContext,
        extensionContext: ExtensionContext,
    ): Boolean = parameterContext.parameter.type == GoIRTestBuilder::class.java

    override fun resolveParameter(
        parameterContext: ParameterContext,
        extensionContext: ExtensionContext,
    ): Any = extensionContext.getStore(NAMESPACE).get(KEY) as GoIRTestBuilder

    companion object {
        private val NAMESPACE = ExtensionContext.Namespace.create(GoIRTestExtension::class.java)
        private const val KEY = "builder"
    }
}
