import org.gradle.api.Project
import org.opentaint.common.OpentaintDependency

object OpentaintIrDependency : OpentaintDependency {

    val Project.opentaint_ir_core
        get() = propertyDep(
            group = "org.opentaint.ir",
            name = "opentaint-ir-core"
        )

    val Project.opentaint_ir_api_common
        get() = propertyDep(
            group = "org.opentaint.ir",
            name = "opentaint-ir-api-common"
        )

    val Project.opentaint_ir_api_jvm
        get() = propertyDep(
            group = "org.opentaint.ir",
            name = "opentaint-ir-api-jvm"
        )

    val Project.opentaint_ir_api_storage
        get() = propertyDep(
            group = "org.opentaint.ir",
            name = "opentaint-ir-api-storage"
        )

    val Project.opentaint_ir_storage
        get() = propertyDep(
            group = "org.opentaint.ir",
            name = "opentaint-ir-storage"
        )

    val Project.opentaint_ir_approximations
        get() = propertyDep(
            group = "org.opentaint.ir",
            name = "opentaint-ir-approximations"
        )

    val Project.opentaint_ir_api_python
        get() = propertyDep(
            group = "org.opentaint.ir.python",
            name = "opentaint-ir-api-python"
        )

    val Project.opentaint_ir_core_python
        get() = propertyDep(
            group = "org.opentaint.ir.python",
            name = "opentaint-ir-impl-python"
        )
}
