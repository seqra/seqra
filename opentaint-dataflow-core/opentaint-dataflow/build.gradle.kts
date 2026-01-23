import OpentaintIrDependency.opentaint_ir_api_common
import OpentaintUtilDependency.opentaintUtilCommon
import org.opentaint.common.KotlinDependency

plugins {
    id("kotlin-conventions")
    kotlinSerialization()
}

dependencies {
    implementation(opentaintUtilCommon)
    implementation("org.opentaint.opentaint-configuration-rules:configuration-rules-common")

    implementation(KotlinDependency.Libs.kotlinx_coroutines_core)
    implementation(KotlinDependency.Libs.kotlin_logging)

    api(opentaint_ir_api_common)
    api(Libs.sarif4k)

    implementation(KotlinDependency.Libs.kotlinx_collections)

    implementation(Libs.fastutil)
    implementation(Libs.jdot)
}
