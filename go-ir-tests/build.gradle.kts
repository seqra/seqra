import java.time.Duration

plugins {
    alias(libs.plugins.kotlin.jvm)
}

dependencies {
    implementation(project(":go-ir-api"))
    implementation(project(":go-ir-client"))
    implementation(project(":go-ir-codegen"))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.assertj.core)
}

tasks.test {
    useJUnitPlatform {
        excludeTags("benchmark", "roundtrip", "fuzz")
    }
}

tasks.register<Test>("benchmarkTest") {
    useJUnitPlatform { includeTags("benchmark") }
    timeout.set(Duration.ofMinutes(30))
}

tasks.register<Test>("roundtripTest") {
    useJUnitPlatform { includeTags("roundtrip") }
    timeout.set(Duration.ofMinutes(5))
}

tasks.register<Test>("fuzzTest") {
    useJUnitPlatform { includeTags("fuzz") }
    timeout.set(Duration.ofHours(2))
}
