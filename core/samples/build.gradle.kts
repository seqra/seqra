import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    java
    kotlin("jvm")
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(kotlin("stdlib"))
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
}

tasks {
    withType<JavaCompile> {
        sourceCompatibility = JavaVersion.VERSION_1_8.toString()
        targetCompatibility = JavaVersion.VERSION_1_8.toString()
        options.compilerArgs.add("-g")
    }

    withType<KotlinCompile> {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_1_8)
            allWarningsAsErrors.set(false)
            freeCompilerArgs.set(listOf("-Xsuppress-version-warnings"))
        }
    }
}

val pythonSamplesSourceSet = sourceSets.create("pythonSamples") {
    resources.setSrcDirs(listOf("src/main/python"))
}

val pythonSamples by configurations.creating {
    isCanBeConsumed = false
    isCanBeResolved = false
}

val goSamplesSourceSet = sourceSets.create("goSamples") {
    resources.setSrcDirs(listOf("src/main/go"))
}

val goSamples by configurations.creating {
    isCanBeConsumed = false
    isCanBeResolved = false
}

tasks.jar {
    from(sourceSets.main.get().allSource) {
        include("**/*.java")
        include("**/*.kt")
    }

    from(pythonSamplesSourceSet.resources) {
        include("**/*.py")
        exclude("ant-benchmark/**")
    }

    from(goSamplesSourceSet.resources) {
        include("**/*.go")
    }
}

// Separate JAR for Ant benchmark Python samples
val antBenchmarkJar = tasks.register<Jar>("antBenchmarkJar") {
    archiveBaseName.set("ant-benchmark-samples")
    from(pythonSamplesSourceSet.resources) {
        include("ant-benchmark/**/*.py")
        include("ant-benchmark/benchmark-metadata.csv")
    }
}

tasks.withType<Jar> {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}
