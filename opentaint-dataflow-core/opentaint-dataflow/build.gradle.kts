import OpentaintConfigurationDependency.opentaintRulesCommon
import OpentaintIrDependency.opentaint_ir_api_common
import OpentaintUtilDependency.opentaintUtilCommon
import org.opentaint.common.KotlinDependency

plugins {
    id("kotlin-conventions")
    kotlinSerialization()
}

dependencies {
    implementation(opentaintUtilCommon)
    implementation(opentaintRulesCommon)

    implementation(KotlinDependency.Libs.kotlinx_coroutines_core)
    implementation(KotlinDependency.Libs.kotlin_logging)

    api(opentaint_ir_api_common)
    api(Libs.sarif4k)

    implementation(KotlinDependency.Libs.kotlinx_collections)

    implementation(Libs.fastutil)
}
