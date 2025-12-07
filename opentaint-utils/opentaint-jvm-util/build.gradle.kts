import org.opentaint.common.KotlinDependency
import org.opentaint.common.OpentaintIrDependency

plugins {
    id("kotlin-conventions")
}

dependencies {
    api(project(":common-util"))

    implementation(OpentaintIrDependency.Libs.opentaint_ir_api_jvm)
    implementation(OpentaintIrDependency.Libs.opentaint_ir_core)
    implementation(OpentaintIrDependency.Libs.opentaint_ir_approximations)

    implementation(KotlinDependency.Libs.reflect)
}
