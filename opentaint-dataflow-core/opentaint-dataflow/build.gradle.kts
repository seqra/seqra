import org.opentaint.common.OpentaintIrDependency
import org.opentaint.common.KotlinDependency

plugins {
    id("kotlin-conventions")
    kotlinSerialization()
}

dependencies {
    implementation("org.opentaint.utils:common-util:2025.07.15.693dc19")
    implementation("org.opentaint.configuration:configuration-rules-common:2025.07.15.703f6e5")

    implementation(KotlinDependency.Libs.kotlinx_coroutines_core)
    implementation(KotlinDependency.Libs.kotlin_logging)

    api(OpentaintIrDependency.Libs.opentaint-ir_api_common)
    api(Libs.sarif4k)

    implementation(KotlinDependency.Libs.kotlinx_collections)

    implementation(Libs.fastutil)
}
