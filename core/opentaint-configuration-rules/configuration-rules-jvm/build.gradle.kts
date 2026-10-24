import OpentaintIrDependency.opentaint_ir_api_jvm
import OpentaintIrDependency.opentaint_ir_core
import org.opentaint.common.KotlinDependency
import org.opentaint.common.JunitDependencies

plugins {
    id("kotlin-conventions")
    kotlinSerialization()
}

dependencies {
    testImplementation(JunitDependencies.Libs.junit_jupiter_api)
    testRuntimeOnly(JunitDependencies.Libs.junit_jupiter_engine)

    api(project(":configuration-rules-common"))

    implementation(opentaint_ir_api_jvm)
    implementation(opentaint_ir_core)

    implementation(KotlinDependency.Libs.kotlinx_serialization_core)
    implementation(KotlinDependency.Libs.kaml)
}
