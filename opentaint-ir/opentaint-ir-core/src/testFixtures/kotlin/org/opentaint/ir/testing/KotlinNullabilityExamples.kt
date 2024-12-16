package org.opentaint.ir.testing

import org.opentaint.ir.testing.usages.NullAnnotationExamples

class KotlinNullabilityExamples {
    class SomeContainer<E>(
        val listOfNotNull: List<E>,
        val listOfNullable: List<E?>,
        val notNullProperty: E,
        val nullableProperty: E?,
    )

    fun simpleGenerics(
        matrixOfNotNull: SomeContainer<SomeContainer<Int>>,
        matrixOfNullable: SomeContainer<SomeContainer<Int?>>,
        containerOfNotNullContainers: SomeContainer<SomeContainer<Int>?>
    ) = Unit

    fun SomeContainer<SomeContainer<Int?>?>.extensionFunction() = Unit

    fun genericsWithProjection(
        covariant: SomeContainer<out String?>,
        contravariant: SomeContainer<in String>,
        star: SomeContainer<*>
    ) = Unit

    fun javaArrays(nullable: IntArray?, notNull: Array<SomeContainer<Int>>) = Unit

    fun <T> typeVariableParameters(notNull: T, nullable: T?) = Unit

    fun <A: List<Int?>, B: List<Int>?> typeVariableDeclarations() = Unit

    lateinit var containerOfNotNull: SomeContainer<String>
    lateinit var containerOfNullable: SomeContainer<String?>

    lateinit var javaContainerOfNotNull: NullAnnotationExamples.SomeContainer<String>
    lateinit var javaContainerOfNullable: NullAnnotationExamples.SomeContainer<String?>

    interface SomeContainerProducerI<P> {
        fun produceContainer(): SomeContainer<P>
    }

    lateinit var someContainerProducer: SomeContainerProducerI<Int?>
}