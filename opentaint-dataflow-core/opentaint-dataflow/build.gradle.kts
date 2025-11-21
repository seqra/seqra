plugins {
    id("opentaint.kotlin-conventions")
    kotlin("plugin.serialization") version Versions.kotlin
}

dependencies {
    implementation(project(":opentaint-util"))
    implementation(project(":opentaint-dataflow:opentaint-dataflow-configuration"))

    api(Libs.opentaint-ir_api_common)
    api(Libs.sarif4k)

    implementation(Libs.kotlinx_collections)

    implementation(Libs.fastutil)
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
        }
    }
}
