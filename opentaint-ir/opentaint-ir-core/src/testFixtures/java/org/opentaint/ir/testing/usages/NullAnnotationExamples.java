package org.opentaint.ir.testing.usages;

import org.opentaint.ir.testing.KotlinNullabilityExamples;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class NullAnnotationExamples {
    String refNullable;
    @NotNull String refNotNull = "dumb init value";
    @Nullable String explicitlyNullable;

    int primitiveValue;

    public static class SomeContainer<E> {
        public List<@NotNull E> listOfNotNull;
        public List<@Nullable E> listOfNullable;
        public SomeContainer<String> containerOfUndefined;

        public @NotNull E notNull;
        public @Nullable E nullable;
        public E undefined;
    }

    String nullableMethod(@Nullable String explicitlyNullableParam, @NotNull String notNullParam, List<@NotNull String> notNullContainer) {
        return null;
    }

    @NotNull String notNullMethod(@Nullable String explicitlyNullableParam, @NotNull String notNullParam) {
        return "dumb return value";
    }

    public @Nullable SomeContainer<? extends @NotNull String> wildcard() {
        return null;
    }

    public SomeContainer<@NotNull String> containerOfNotNull;
    public SomeContainer<@Nullable String> containerOfNullable;
    public SomeContainer<String> containerOfUndefined;

    public KotlinNullabilityExamples.SomeContainer<@NotNull String> ktContainerOfNotNull;
    public KotlinNullabilityExamples.SomeContainer<@Nullable String> ktContainerOfNullable;
    public KotlinNullabilityExamples.SomeContainer<String> ktContainerOfUndefined;
}