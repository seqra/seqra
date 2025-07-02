import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    id("opentaint.kotlin-conventions")
}

dependencies {
    implementation("${Versions.opentaint-irPackage}:opentaint-ir-api-common:${Versions.opentaint-ir}")
    implementation("${Versions.opentaint-irPackage}:opentaint-ir-taint-configuration:${Versions.opentaint-ir}")

    implementation("io.github.detekt.sarif4k", "sarif4k", Versions.sarif4k)

    api("io.github.microutils:kotlin-logging:${Versions.klogging}")
}

tasks.withType<KotlinCompile> {
    kotlinOptions {
        freeCompilerArgs = freeCompilerArgs + listOf(
            "-Xcontext-receivers",
        )
    }
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
        }
    }
}
