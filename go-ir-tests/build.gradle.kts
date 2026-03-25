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
    // Show test output for debugging
    testLogging {
        events("passed", "skipped", "failed")
        showStandardStreams = true
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
    timeout.set(Duration.ofMinutes(5))
}

tasks.register<Test>("fuzzTest") {
    testClassesDirs = testSourceSet.output.classesDirs
    classpath = testSourceSet.runtimeClasspath
    useJUnitPlatform { includeTags("fuzz") }
    timeout.set(Duration.ofHours(2))
}
