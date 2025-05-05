plugins {
    id("java")
    kotlin("plugin.serialization")
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(project(":opentaint-ir-api-core"))
    implementation(project(":opentaint-ir-api-jvm"))
    implementation(project(":opentaint-ir-core"))
    implementation(testFixtures(project(":opentaint-ir-core")))
    implementation(Libs.kotlinx_serialization_json)

    testImplementation(group = "io.github.microutils", name = "kotlin-logging", version = "1.8.3")
}

tasks.test {
    useJUnitPlatform()
}