plugins {
    kotlin("jvm")
}

dependencies {
    testImplementation(project(":opentaint-ir-api-python"))
    testImplementation(project(":opentaint-ir-impl-python"))

    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation("org.assertj:assertj-core:3.25.3")
    testImplementation("com.google.code.gson:gson:2.10.1")
}

tasks.test {
    useJUnitPlatform {
        if (!project.hasProperty("allTiers")) {
            excludeTags("tier1")
        }
    }
}
