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
}

tasks.test {
    useJUnitPlatform()
}