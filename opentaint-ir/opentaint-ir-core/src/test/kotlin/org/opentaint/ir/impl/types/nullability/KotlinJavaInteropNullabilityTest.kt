
package org.opentaint.ir.impl.types.nullability

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.opentaint.ir.api.JIRClassType
import org.opentaint.ir.impl.KotlinNullabilityExamples
import org.opentaint.ir.impl.types.BaseTypesTest
import org.opentaint.ir.impl.usages.NullAnnotationExamples

class KotlinJavaInteropNullabilityTest : BaseTypesTest() {

    @Test
    fun `Test nullability after substitution of Kotlin T with type of undefined nullability Java`() = runBlocking {
        val clazz = findType<NullAnnotationExamples>()
        val containerOfUndefined = clazz.declaredFields.single { it.name == "ktContainerOfUndefined" }

        val containerOfUndefinedFieldsNullability = (containerOfUndefined.fieldType as JIRClassType)
            .fields
            .sortedBy { it.name }
            .map { it.fieldType.nullabilityTree }

        // E -> String
        val expectedNullability = listOf(
            // List<E>
            buildTree(false) {
                +buildTree(null)
            },

            // List<E?>
            buildTree(false) {
                +buildTree(true)
            },

            // E
            buildTree(null),

            // E?
            buildTree(true)
        )

        assertEquals(expectedNullability, containerOfUndefinedFieldsNullability)
    }

    @Test
    @Disabled("Type annotations are not supported")
    fun `Test nullability after substitution of Kotlin T with notNull type Java`() = runBlocking {
        val clazz = findType<NullAnnotationExamples>()
        val containerOfNotNull = clazz.declaredFields.single { it.name == "ktContainerOfNotNull" }

        val containerOfNotNullFields = (containerOfNotNull.fieldType as JIRClassType)
            .fields
            .sortedBy { it.name }
            .map { it.fieldType.nullabilityTree }

        // E -> @NotNull String
        val expectedNullability = listOf(
            // List<E>
            buildTree(false) {
                +buildTree(false)
            },

            // List<E?>
            buildTree(false) {
                +buildTree(true)
            },

            // E
            buildTree(false),

            // E?
            buildTree(true)
        )

        assertEquals(expectedNullability, containerOfNotNullFields)
    }

    @Test
    @Disabled("Type annotations are not supported")
    fun `Test nullability after substitution of Kotlin T with nullable type Java`() = runBlocking {
        val clazz = findType<NullAnnotationExamples>()
        val containerOfNotNull = clazz.declaredFields.single { it.name == "ktContainerOfNullable" }

        val containerOfNotNullFields = (containerOfNotNull.fieldType as JIRClassType)
            .fields
            .sortedBy { it.name }
            .map { it.fieldType.nullabilityTree }

        // E -> @Nullable String
        val expectedNullability = listOf(
            // List<E>
            buildTree(false) {
                +buildTree(true)
            },

            // List<E?>
            buildTree(false) {
                +buildTree(true)
            },

            // E
            buildTree(true),

            // E?
            buildTree(true)
        )

        assertEquals(expectedNullability, containerOfNotNullFields)
    }

    @Test
    @Disabled("Type annotations are not supported")
    fun `Test nullability after substitution of Java T with nullable type Kotlin`() = runBlocking {
        val clazz = findType<KotlinNullabilityExamples>()
        val containerOfNullable = clazz.declaredFields.single { it.name == "javaContainerOfNullable" }

        val containerOfNullableFields = (containerOfNullable.fieldType as JIRClassType)
            .fields
            .sortedBy { it.name }
            .map { it.fieldType.nullabilityTree }

        // E -> String?
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

        assertEquals(expectedNullability, containerOfNullableFields)
    }

    @Test
    @Disabled("Type annotations are not supported")
    fun `Test nullability after substitution of Java T with notNull type Kotlin`() = runBlocking {
        val clazz = findType<KotlinNullabilityExamples>()
        val containerOfNotNull = clazz.declaredFields.single { it.name == "javaContainerOfNotNull" }

        val containerOfNotNullFields = (containerOfNotNull.fieldType as JIRClassType)
            .fields
            .sortedBy { it.name }
            .map { it.fieldType.nullabilityTree }

        // E -> String
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

        assertEquals(expectedNullability, containerOfNotNullFields)
    }
}