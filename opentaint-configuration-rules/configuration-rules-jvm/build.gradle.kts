import OpentaintIrDependency.opentaint_ir_api_jvm
import OpentaintIrDependency.opentaint_ir_core
import org.opentaint.common.KotlinDependency

plugins {
    id("kotlin-conventions")
    kotlinSerialization()
}

dependencies {
    api(project(":configuration-rules-common"))

    implementation(opentaint_ir_api_jvm)
    implementation(opentaint_ir_core)

    implementation(KotlinDependency.Libs.kotlinx_serialization_core)
    implementation(KotlinDependency.Libs.kaml)
}
