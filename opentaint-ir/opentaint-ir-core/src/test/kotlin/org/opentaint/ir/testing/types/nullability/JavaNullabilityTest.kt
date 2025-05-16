package org.opentaint.ir.testing.types.nullability

import kotlinx.coroutines.runBlocking
import org.opentaint.ir.api.jvm.JIRClassType
import org.opentaint.ir.testing.types.BaseTypesTest
import org.opentaint.ir.testing.types.NullAnnotationExamples
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class JavaNullabilityTest : BaseTypesTest() {

    @Test
    fun `nullability for simple types Java`() = runBlocking {
        val clazz = findType<NullAnnotationExamples>()
        val params = clazz.declaredMethods.single { it.name == "nullableMethod" }.parameters
        val actualNullability = params.map { it.type.nullabilityTree }
        val expectedNullability = listOf(
            // @Nullable String
            buildTree(true),

            // @NotNull String
            buildTree(false),

            // SomeContainer<@NotNull String>
            buildTree(null) {
                +buildTree(false)
            }
        )

        assertEquals(expectedNullability, actualNullability)
    }

    @Test
    fun `nullability on wildcards Java`() = runBlocking {
        val clazz = findType<NullAnnotationExamples>()
        val returnType = clazz.declaredMethods.single { it.name == "wildcard" }.returnType
        val actualNullability = returnType.nullabilityTree
        val expectedNullability =
            buildTree(true) {
                +buildTree(false)
            }

        assertEquals(expectedNullability, actualNullability)
    }

    @Test
    fun `nullability after substitution with NotNull type or type of undefined nullability Java`() = runBlocking {
        val clazz = findType<NullAnnotationExamples>()
        val containerOfUndefined = clazz.declaredFields.single { it.name == "containerOfUndefined" }
        val containerOfNotNull = clazz.declaredFields.single { it.name == "containerOfNotNull" }

        val containerOfUndefinedFieldsNullability = (containerOfUndefined.type as JIRClassType)
            .fields
            .sortedBy { it.name }
            .map { it.type.nullabilityTree }

        val containerOfNotNullFieldsNullability = (containerOfNotNull.type as JIRClassType)
            .fields
            .sortedBy { it.name }
            .map { it.type.nullabilityTree }

        // E -> String or E -> @NotNull String
        val expectedNullability = listOf(
            // List<@NotNull E>
            buildTree(null) {
                +buildTree(false)
            },

            // List<@Nullable E>
            buildTree(null) {
                +buildTree(true)
            },

            // List<E>
            buildTree(null) {
                +buildTree(null)
            },

            // @NotNull E, @Nullable E, E
            buildTree(false), buildTree(true), buildTree(null)
        )

        assertEquals(expectedNullability, containerOfNotNullFieldsNullability)
        assertEquals(expectedNullability, containerOfUndefinedFieldsNullability)
    }

    @Test
    fun `nullability after substitution with nullable type Java`() = runBlocking {
        val clazz = findType<NullAnnotationExamples>()
        val containerOfNullable = clazz.declaredFields.single { it.name == "containerOfNullable" }

        val containerOfNullableFieldsNullability = (containerOfNullable.type as JIRClassType)
            .fields
            .sortedBy { it.name }
            .map { it.type.nullabilityTree }

        // E -> @Nullable String
        val expectedNullability = listOf(
            // List<@NotNull E>
            buildTree(null) {
                +buildTree(false)
            },

            // List<@Nullable E>
            buildTree(null) {
                +buildTree(true)
            },

            // List<E>
            buildTree(null) {
                +buildTree(true)
            },

            // @NotNull E, @Nullable E, E
            buildTree(false), buildTree(true), buildTree(true)
        )

        assertEquals(expectedNullability, containerOfNullableFieldsNullability)
    }
}
