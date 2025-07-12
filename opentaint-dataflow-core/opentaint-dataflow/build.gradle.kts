import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    id("opentaint.kotlin-conventions")
}

dependencies {
    implementation(Libs.opentaint-ir_api_common)
    implementation(Libs.opentaint-ir_taint_configuration)
    implementation(Libs.sarif4k)
}

tasks.withType<KotlinCompile> {
    kotlinOptions {
        freeCompilerArgs += "-Xcontext-receivers"
    }
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
        }
    }
}
