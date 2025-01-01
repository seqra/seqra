import kotlinx.benchmark.gradle.JvmBenchmarkTarget
import kotlinx.benchmark.gradle.benchmark
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

val kotlinVersion: String by rootProject
val coroutinesVersion: String by rootProject
val junit5Version: String by project
val semVer: String? by project

group = "org.opentaint.ir"
project.version = semVer ?: "1.0-SNAPSHOT"

buildscript {
    repositories {
        mavenCentral()
        maven(url = "https://plugins.gradle.org/m2/")
    }
}

plugins {
    val kotlinVersion = "1.7.20"

    `java-library`
    `maven-publish`
    `java-test-fixtures`
    kotlin("jvm") version kotlinVersion
    kotlin("plugin.allopen") version kotlinVersion
    kotlin("plugin.serialization") version kotlinVersion
    id("org.jetbrains.kotlinx.benchmark") version "0.4.4"
    id("org.cadixdev.licenser") version "0.6.1"

}

subprojects {
    group = rootProject.group
    version = rootProject.version

    apply {
        plugin("maven-publish")
        plugin("kotlin")
        plugin("org.jetbrains.kotlinx.benchmark")
        plugin("org.jetbrains.kotlin.plugin.serialization")
        plugin("org.jetbrains.kotlin.plugin.allopen")
        plugin("org.cadixdev.licenser")
    }

    repositories {
        mavenCentral()
        maven("https://s01.oss.sonatype.org/content/repositories/orgopentaintsoot-1004/")
        maven("https://plugins.gradle.org/m2")
        maven("https://www.jetbrains.com/intellij-repository/releases")
        maven("https://cache-redirector.jetbrains.com/maven-central")
    }

    dependencies {
        implementation(group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-core", version = coroutinesVersion)

        implementation(group = "org.jetbrains.kotlin", name = "kotlin-stdlib-jdk8", version = kotlinVersion)
        implementation(group = "org.jetbrains.kotlin", name = "kotlin-reflect", version = kotlinVersion)

        testImplementation("org.junit.jupiter:junit-jupiter") {
            version {
                strictly(junit5Version)
            }
        }
        testImplementation(group = "com.google.guava", name = "guava", version = "31.1-jre")
        testImplementation(group = "org.jetbrains.kotlinx", name = "kotlinx-benchmark-runtime", version = "0.4.4")
    }

    tasks {
        withType<JavaCompile> {
            sourceCompatibility = "1.8"
            targetCompatibility = "1.8"
            options.encoding = "UTF-8"
            options.compilerArgs = options.compilerArgs + "-Xlint:all"
        }
        withType<KotlinCompile> {
            kotlinOptions {
                jvmTarget = "1.8"
                freeCompilerArgs = freeCompilerArgs + listOf(
                    "-Xallow-result-return-type",
                    "-Xsam-conversions=class",
                    "-Xcontext-receivers",
                    "-Xjvm-default=all"
                )
                allWarningsAsErrors = false
            }
        }
        compileTestKotlin {
            kotlinOptions {
                jvmTarget = "1.8"
                freeCompilerArgs = freeCompilerArgs + listOf(
                    "-Xallow-result-return-type",
                    "-Xsam-conversions=class",
                    "-Xcontext-receivers"
                )
                allWarningsAsErrors = false
            }
        }
        withType<Test> {
            useJUnitPlatform()
            jvmArgs = listOf("-Xmx2g", "-XX:+HeapDumpOnOutOfMemoryError", "-XX:HeapDumpPath=heapdump.hprof")
            testLogging {
                events("passed", "skipped", "failed")
            }
        }

    }

    allOpen {
        annotation("org.openjdk.jmh.annotations.State")
    }

    benchmark {
        configurations {
            named("main") { // main configuration is created automatically, but you can change its defaults
                warmups = 3 // number of warmup iterations
                iterations = 5 // number of iterations
            }
        }

        // Setup configurations
        targets {
            // This one matches sourceSet name above
            register("test") {
                this as JvmBenchmarkTarget
                jmhVersion = "1.21"
            }
        }
    }

    license {
        include("**/*.kt")
        include("**/*.java")
        header(rootProject.file("docs/copyright/COPYRIGHT_HEADER.txt"))
    }

    publishing {
        publications {
            create<MavenPublication>("jar") {
                from(components["java"])
                groupId = "org.opentaint.ir"
                artifactId = project.name
            }
        }
    }

}

configure(
    listOf(
        project(":opentaint-ir-api"),
        project(":opentaint-ir-core"),
        project(":opentaint-ir-analysis"),
    )
) {
    publishing {
        repositories {
            maven {
                name = "GitHubPackages"
                url = uri("https://maven.pkg.github.com/Opentaint/opentaint-ir")
                credentials {
                    username = System.getenv("GITHUB_ACTOR")
                    password = System.getenv("GITHUB_TOKEN")
                }
            }
        }
    }
}

val aggregatedTasks = listOf("check", "build", "test", "lifecycleTest")
for (taskName in aggregatedTasks) {
    val subprojectTasks = subprojects.map { ":${it.name}:$taskName" }
    if (tasks.findByName(taskName) != null) {
        tasks.named(taskName) {
            dependsOn(subprojectTasks)
        }
    } else {
        tasks.register(taskName) {
            dependsOn(subprojectTasks)
        }
    }
}
