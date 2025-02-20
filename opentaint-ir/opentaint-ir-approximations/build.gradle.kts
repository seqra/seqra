plugins {
    id("java")
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(project(":opentaint-ir-api"))
    implementation(project(":opentaint-ir-core"))
    implementation(testFixtures(project(":opentaint-ir-core")))

    testImplementation(group = "io.github.microutils", name = "kotlin-logging", version = "1.8.3")
}

tasks.test {
    useJUnitPlatform()
}