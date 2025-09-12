plugins {
    id("opentaint.kotlin-conventions")
}

dependencies {
    implementation(Libs.opentaint-ir_api_common)
    implementation(Libs.opentaint-ir_taint_configuration)
    implementation(Libs.sarif4k)
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
        }
    }
}
