plugins {
    id("opentaint.kotlin-conventions")
}

dependencies {
    implementation(project(":opentaint-util"))
    api(Libs.opentaint-ir_api_common)
    implementation(Libs.opentaint-ir_taint_configuration)
    api(Libs.sarif4k)
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
        }
    }
}
