plugins {
    id("opentaint.kotlin-conventions")
    kotlin("plugin.serialization") version Versions.kotlin
}

dependencies {
    api(project(":opentaint-dataflow"))
    implementation(project(":opentaint-util"))
    implementation(project(":opentaint-jvm-dataflow:opentaint-jvm-dataflow-configuration"))

    implementation(Libs.opentaint-ir_api_jvm)
    implementation(Libs.opentaint-ir_core)
    implementation(Libs.opentaint-ir_api_storage)
    implementation(Libs.opentaint-ir_storage)

    implementation(Libs.fastutil)

    implementation(Libs.sarif4k)
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
        }
    }
}
