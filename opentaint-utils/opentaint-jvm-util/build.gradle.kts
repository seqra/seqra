import org.opentaint.common.KotlinDependency
import OpentaintIrDependency.opentaint_ir_api_jvm
import OpentaintIrDependency.opentaint_ir_approximations
import OpentaintIrDependency.opentaint_ir_core

plugins {
    id("kotlin-conventions")
}

dependencies {
    api(project(":common-util"))

    implementation(opentaint_ir_api_jvm)
    implementation(opentaint_ir_core)
    implementation(opentaint_ir_approximations)

    implementation(KotlinDependency.Libs.reflect)
}
