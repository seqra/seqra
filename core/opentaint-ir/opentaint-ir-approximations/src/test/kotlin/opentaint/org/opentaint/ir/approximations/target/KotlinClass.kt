package opentaint.org.opentaint.ir.approximations.target

import opentaint.java.lang.Integer as IntegerModel
import org.opentaint.ir.approximations.target.ClassForField
import org.opentaint.ir.approximations.target.KotlinClass as TargetKotlinClass
import org.jetbrains.annotations.NotNull

class KotlinClass {
    @NotNull
    private val artificialField: ClassForField = ClassForField()
    private val fieldToReplace: Int = 3
    private val sameApproximation: KotlinClass? = null
    private val anotherApproximation: IntegerModel? = null

    fun replaceBehaviour(value: Int): Int = 42

    fun artificialMethod(): Int = 1 + 2 * 3

    fun useArtificialField(classForField: ClassForField): Int {
        if (classForField == artificialField) {
            return fieldToReplace
        }

        return 0
    }

    fun useSameApproximationTarget(kotlinClass: TargetKotlinClass): Int {
        if (sameApproximation == null) return 0

        if (kotlinClass.methodWithoutApproximation() == sameApproximation.artificialMethod()) {
            return 42
        }

        return 1
    }

    fun useAnotherApproximationTarget(value: Int): Int {
        if (anotherApproximation == null) return 0

        if (anotherApproximation.value == value) {
            return 42
        }

        return 1
    }

    fun useFieldWithoutApproximation(classForField: ClassForField): Int {
        if (classForField == artificialField) {
            return 1
        }

        return 2
    }
}
