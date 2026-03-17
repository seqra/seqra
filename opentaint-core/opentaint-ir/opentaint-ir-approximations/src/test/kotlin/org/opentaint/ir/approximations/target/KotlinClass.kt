package org.opentaint.ir.approximations.target

class KotlinClass {
    private val fieldToReplace: Int = 42
    private val sameApproximationTarget: KotlinClass? = null
    private val anotherApproximationTarget: Int? = null
    private val fieldWithoutApproximation: ClassForField = ClassForField()

    fun replaceBehaviour(value: Int): Int {
        if (value == fieldToReplace) {
            return -1;
        }

        return -2;
    }

    fun methodWithoutApproximation(): Int = 42

    fun useSameApproximationTarget(kotlinClass: KotlinClass): Int {
        if (kotlinClass == sameApproximationTarget) {
            return 1
        }

        return 0
    }

    fun useAnotherApproximationTarget(value: Int): Int {
        if (value == anotherApproximationTarget) {
            return 1
        }

        return 0
    }

    fun useFieldWithoutApproximation(classForField: ClassForField): Int {
        if (classForField == fieldWithoutApproximation) {
            return 1
        }

        return 0
    }
}

class ClassForField