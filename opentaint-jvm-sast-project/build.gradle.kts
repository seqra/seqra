import org.opentaint.common.OpentaintIrDependency
import org.opentaint.common.KotlinDependency

plugins {
    id("kotlin-conventions")
}

dependencies {
    implementation("org.opentaint.project:opentaint-project-model:2025.07.15.27da752")

    implementation(OpentaintIrDependency.Libs.opentaint-ir_api_jvm)
    implementation(OpentaintIrDependency.Libs.opentaint-ir_core)
    implementation(OpentaintIrDependency.Libs.opentaint-ir_approximations)
    implementation(OpentaintIrDependency.Libs.opentaint-ir_api_storage)
    implementation(OpentaintIrDependency.Libs.opentaint-ir_storage)

    implementation(KotlinDependency.Libs.kotlin_logging)
}
