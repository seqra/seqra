plugins {
    id("opentaint.kotlin-conventions")
    kotlin("plugin.serialization") version Versions.kotlin
}

dependencies {
    api(project(":opentaint-dataflow:opentaint-dataflow-configuration"))

    implementation(Libs.opentaint-ir_api_jvm)
    implementation(Libs.opentaint-ir_core)

    implementation(Libs.kotlinx_serialization_core)
    implementation(Libs.kaml)
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
        }
    }
}
