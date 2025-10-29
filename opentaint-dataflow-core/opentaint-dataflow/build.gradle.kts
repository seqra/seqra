plugins {
    id("opentaint.kotlin-conventions")
    kotlin("plugin.serialization") version Versions.kotlin
}

dependencies {
    implementation(project(":opentaint-util"))
    api(Libs.opentaint-ir_api_common)
    implementation(Libs.opentaint-ir_taint_configuration) {
        exclude(Libs.opentaint-irPackage)
    }
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
