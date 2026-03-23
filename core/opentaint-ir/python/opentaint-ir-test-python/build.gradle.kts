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

    // Needed for Tier 3 round-trip tests (ExecuteFunctionRequest/Response)
    testImplementation("com.google.protobuf:protobuf-java:3.25.3")
    testImplementation("io.grpc:grpc-protobuf:1.62.2")
    testImplementation("io.grpc:grpc-stub:1.62.2")
}

tasks.test {
    useJUnitPlatform {
        if (!project.hasProperty("allTiers")) {
            excludeTags("tier1")
        }
    }
    // Each test class spawns a Python subprocess with gRPC server.
    // Single fork prevents port conflicts and resource exhaustion.
    maxParallelForks = 1
    maxHeapSize = "8g"

    // Test logging: show which tests start and pass/fail
    testLogging {
        events("started", "passed", "failed", "skipped")
        showStandardStreams = false
        showExceptions = true
        showCauses = true
        showStackTraces = true
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}
