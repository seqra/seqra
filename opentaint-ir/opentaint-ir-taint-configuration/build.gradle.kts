plugins {
    kotlin("plugin.serialization")
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(project(":opentaint-ir-api-jvm"))
    implementation(project(":opentaint-ir-core"))
    implementation(testFixtures(project(":opentaint-ir-core")))

    implementation(Libs.kotlinx_serialization_core)
    implementation(Libs.kotlinx_serialization_json) // for local tests only

    testImplementation(testFixtures(project(":opentaint-ir-storage")))
    testImplementation(Libs.kotlin_logging)
}

tasks.test {
    useJUnitPlatform()
}
