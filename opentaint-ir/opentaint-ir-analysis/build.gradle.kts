val kotlinVersion: String by rootProject
val coroutinesVersion: String by rootProject

plugins {
    `java-test-fixtures`
}

dependencies {
    api(project(":opentaint-ir-core"))
    api(project(":opentaint-ir-api"))

    testImplementation(testFixtures(project(":opentaint-ir-core")))
    testFixturesImplementation(project(":opentaint-ir-api"))
    testFixturesImplementation("javax.servlet:servlet-api:2.5")
    testImplementation("org.junit.jupiter:junit-jupiter-params:5.9.2")
    testImplementation(files("src/testFixtures/resources/juliet.jar"))
    testImplementation(files("src/testFixtures/resources/pointerbench.jar"))

    implementation("org.jetbrains.kotlinx:kotlinx-cli:0.3.5")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.4.1")
    implementation(group = "io.github.microutils", name = "kotlin-logging", version = "1.8.3")
}