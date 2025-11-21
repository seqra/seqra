plugins {
    id("opentaint.kotlin-conventions")
}

dependencies {
    implementation(Libs.opentaint-ir_api_common)
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
        }
    }
}
