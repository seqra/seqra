import org.opentaint.common.OpentaintIrDependency
import org.opentaint.common.KotlinDependency

plugins {
    id("kotlin-conventions")
    kotlinSerialization()
}

dependencies {
    api(project(":configuration-rules-common"))

    implementation(OpentaintIrDependency.Libs.opentaint-ir_api_jvm)
    implementation(OpentaintIrDependency.Libs.opentaint-ir_core)

    implementation(KotlinDependency.Libs.kotlinx_serialization_core)
    implementation(KotlinDependency.Libs.kaml)
}
