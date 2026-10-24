package org.opentaint.dataflow.configuration.jvm

import java.lang.reflect.Proxy
import org.junit.jupiter.api.Test
import org.opentaint.dataflow.configuration.CommonCondition
import org.opentaint.dataflow.configuration.CommonTaintConfigurationSinkMeta
import org.opentaint.dataflow.configuration.jvm.serialized.ItemInfo
import org.opentaint.dataflow.configuration.mkFalse
import org.opentaint.dataflow.configuration.mkTrue
import org.opentaint.ir.api.common.CommonMethod
import org.opentaint.ir.api.jvm.JIRField
import kotlin.test.*

class RuleSpecializationEqualityTest {
    private val method = proxy<CommonMethod>("method")
    private val field = proxy<JIRField>("field")
    private val condition: Condition = mkTrue()
    private val actions = listOf(AssignMark(TaintMark("T"), Argument(0)))
    private val meta = TaintSinkMeta("message", CommonTaintConfigurationSinkMeta.Severity.Warning, listOf(1))

    private fun rows(): List<TaintConfigurationItem> = listOf(
        TaintEntryPointSource(method, condition, actions, null, "serialized"),
        TaintMethodSource(method, condition, actions, null, "serialized"),
        TaintMethodExitSource(method, condition, actions, null, "serialized"),
        TaintStaticFieldSource(field, condition, actions, null, "serialized"),
        TaintMethodSink(method, condition, actions, "id", meta, null, "serialized"),
        TaintMethodExitSink(method, condition, actions, "id", meta, null, "serialized"),
        TaintMethodEntrySink(method, condition, actions, "id", meta, null, "serialized"),
        TaintPassThrough(method, condition, actions, null, "serialized"),
        TaintCleaner(method, condition, actions, null, "serialized"),
    )

    @Test fun `null and distinct original ordinal labels preserve semantic equality and hash`() {
        for (row in rows()) {
            val first = row.relabelRuleOrdinal(0, 2)
            val second = row.relabelRuleOrdinal(1, 2)
            val name = row.javaClass.simpleName
            assertEquals(row, first, name)
            assertEquals(first, row, name)
            assertEquals(first, second, name)
            assertEquals(second, first, name)
            assertEquals(row.hashCode(), first.hashCode(), name)
            assertEquals(row.hashCode(), second.hashCode(), name)
            assertEquals(1, linkedSetOf(row, first, second).size, name)
        }
    }

    @Test fun `ordered occurrences and original labels remain distinct without affecting row equality`() {
        for (row in rows()) {
            val first = row.relabelRuleOrdinal(0, 2)
            val second = row.relabelRuleOrdinal(1, 2)
            val list = SpecializedRules(listOf(first, second), 2)
            assertEquals(2, list.size)
            assertEquals(2, list.originalRuleCount)
            assertEquals(0, list[0].ruleOrdinal(-1))
            assertEquals(1, list[1].ruleOrdinal(-1))
            assertSame(row, list[0].specialization!!.original)
            assertEquals(listOf(0), list[0].specialization!!.actionOrdinals)
            assertEquals(listOf(0), list[1].specialization!!.actionOrdinals)
            assertNotSame(first.specialization, second.specialization)
        }
    }

    @Test fun `every original constructor field continues to participate in equality`() {
        for (row in rows()) {
            val values = values(row)
            for (index in 0 until values.lastIndex) {
                val changed = values.clone()
                changed[index] = changedValue(values[index])
                val variant = constructor(row).newInstance(*changed) as TaintConfigurationItem
                assertNotEquals(row, variant, "${row.javaClass.simpleName} component ${index + 1}")
                assertNotEquals(row.relabelRuleOrdinal(0, 2), variant.relabelRuleOrdinal(1, 2))
            }
        }
    }

    @Test fun `hash order is the original Kotlin data class field order`() {
        for (row in rows()) {
            val oldFields = values(row).dropLast(1)
            var expected = oldFields.first().hashCode()
            for (value in oldFields.drop(1)) expected = 31 * expected + (value?.hashCode() ?: 0)
            assertEquals(expected, row.hashCode(), row.javaClass.simpleName)
            assertEquals(expected, row.relabelRuleOrdinal(7, 12).hashCode(), row.javaClass.simpleName)
        }
    }

    @Test fun `different rule roles remain unequal`() {
        val rows = rows()
        for (a in rows.indices) for (b in rows.indices) {
            if (a != b) assertNotEquals(rows[a], rows[b])
        }
    }

    private fun constructor(row: TaintConfigurationItem) = row.javaClass.constructors.single {
        it.parameterTypes.last().name != "kotlin.jvm.internal.DefaultConstructorMarker"
    }
    private fun values(row: TaintConfigurationItem): Array<Any?> =
        Array(constructor(row).parameterCount) { row.javaClass.getMethod("component${it + 1}").invoke(row) }

    private fun changedValue(value: Any?): Any? = when (value) {
        is CommonMethod -> proxy<CommonMethod>("different-method")
        is JIRField -> proxy<JIRField>("different-field")
        is CommonCondition<*> -> mkFalse<JirCondition>()
        is List<*> -> listOf(AssignMark(TaintMark("different-mark"), Argument(1)))
        is String -> "$value-different"
        is CommonTaintConfigurationSinkMeta -> meta.copy(message = "different-message")
        null -> object : ItemInfo {}
        else -> error("Uncovered original semantic field: $value")
    }

    private inline fun <reified T> proxy(name: String): T = Proxy.newProxyInstance(
        T::class.java.classLoader, arrayOf(T::class.java)
    ) { proxy, called, args ->
        when (called.name) {
            "equals" -> proxy === args!![0]
            "hashCode" -> name.hashCode()
            "toString" -> name
            else -> error("Unexpected fixture method: ${called.name}")
        }
    } as T
}
