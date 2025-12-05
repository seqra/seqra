import org.opentaint.common.OpentaintIrDependency
import org.opentaint.common.KotlinDependency

plugins {
    id("kotlin-conventions")
    kotlinSerialization()
}

dependencies {
    implementation(Libs.opentaintUtilCommon)
    implementation(Libs.opentaintRulesCommon)

    implementation(KotlinDependency.Libs.kotlinx_coroutines_core)
    implementation(KotlinDependency.Libs.kotlin_logging)

    api(OpentaintIrDependency.Libs.opentaint-ir_api_common)
    api(Libs.sarif4k)

    implementation(KotlinDependency.Libs.kotlinx_collections)

    implementation(Libs.fastutil)
}
