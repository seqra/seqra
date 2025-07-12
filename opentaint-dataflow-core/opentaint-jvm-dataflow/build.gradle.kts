import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    id("opentaint.kotlin-conventions")
}

val samples by sourceSets.creating {
    java {
        srcDir("src/samples/java")
    }
}

dependencies {
    api(project(":opentaint-dataflow"))

    implementation(Libs.opentaint-ir_api_common)
    implementation(Libs.opentaint-ir_api_jvm)
    implementation(Libs.opentaint-ir_core)
    implementation(Libs.opentaint-ir_taint_configuration)

    implementation(Libs.sarif4k)

    testImplementation(Libs.mockk)
    testImplementation(Libs.junit_jupiter_params)

    testImplementation(samples.output)
    testImplementation(files("src/test/resources/pointerbench.jar"))
    testImplementation("joda-time:joda-time:2.12.5")
    testImplementation(Libs.juliet_support)
    for (cweNum in listOf(89, 476, 563, 690)) {
        testImplementation(Libs.juliet_cwe(cweNum))
    }
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
