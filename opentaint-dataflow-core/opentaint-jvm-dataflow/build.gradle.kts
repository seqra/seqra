import org.opentaint.common.OpentaintIrDependency
import org.opentaint.common.KotlinDependency

plugins {
    id("kotlin-conventions")
    kotlinSerialization()
}

dependencies {
    api(project(":opentaint-dataflow"))
    implementation("org.opentaint.utils:common-util:2025.07.15.693dc19")
    implementation("org.opentaint.utils:opentaint-jvm-util:2025.07.15.693dc19")
    implementation("org.opentaint.configuration:configuration-rules-jvm:2025.07.15.703f6e5")

    implementation(OpentaintIrDependency.Libs.opentaint-ir_api_jvm)
    implementation(OpentaintIrDependency.Libs.opentaint-ir_core)
    implementation(OpentaintIrDependency.Libs.opentaint-ir_api_storage)
    implementation(OpentaintIrDependency.Libs.opentaint-ir_storage)

    implementation(KotlinDependency.Libs.kotlin_logging)
    implementation(KotlinDependency.Libs.reflect)

    implementation(Libs.fastutil)

    implementation(Libs.sarif4k)
}
