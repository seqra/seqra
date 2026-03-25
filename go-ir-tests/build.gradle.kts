import java.time.Duration

plugins {
    alias(libs.plugins.kotlin.jvm)
}

dependencies {
    implementation(project(":go-ir-api"))
    implementation(project(":go-ir-client"))
    implementation(project(":go-ir-codegen"))
    // Test infra classes (in src/main) need JUnit API at compile time
    implementation(libs.junit.jupiter)
    implementation(libs.assertj.core)
    runtimeOnly(libs.junit.platform.launcher)
}

// Resolve Go server binary path
val goServerBinary = rootProject.projectDir.resolve("go-ssa-server/go-ssa-server").absolutePath

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    systemProperty("goir.server.binary", goServerBinary)
    // Parallel execution: run test classes concurrently
    systemProperty("junit.jupiter.execution.parallel.enabled", "true")
    systemProperty("junit.jupiter.execution.parallel.mode.default", "same_thread")
    systemProperty("junit.jupiter.execution.parallel.mode.classes.default", "concurrent")
    maxParallelForks = (Runtime.getRuntime().availableProcessors() / 2).coerceAtLeast(2)
    // Show test output for debugging
    testLogging {
        events("passed", "skipped", "failed")
    }
}

tasks.test {
    useJUnitPlatform {
        excludeTags("benchmark", "roundtrip", "fuzz")
    }
}

val testSourceSet = sourceSets["test"]

tasks.register<Test>("benchmarkTest") {
    testClassesDirs = testSourceSet.output.classesDirs
    classpath = testSourceSet.runtimeClasspath
    useJUnitPlatform { includeTags("benchmark") }
    timeout.set(Duration.ofMinutes(30))
}

tasks.register<Test>("roundtripTest") {
    testClassesDirs = testSourceSet.output.classesDirs
    classpath = testSourceSet.runtimeClasspath
    useJUnitPlatform { includeTags("roundtrip") }
    timeout.set(Duration.ofMinutes(30))
}

tasks.register<Test>("fuzzTest") {
    testClassesDirs = testSourceSet.output.classesDirs
    classpath = testSourceSet.runtimeClasspath
    useJUnitPlatform { includeTags("fuzz") }
    timeout.set(Duration.ofMinutes(30))
}
