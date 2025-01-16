import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

val kotlinVersion: String by rootProject
val coroutinesVersion: String by rootProject
val junit5Version: String by project
val semVer: String? by project

group = "org.opentaint.ir"
val versionSuffix = when(val branch = System.getenv("BRANCH_NAME")) {
    null, "" ->  ""
    else -> "-$branch"
}

project.version = semVer ?: "1.0$versionSuffix-SNAPSHOT"

buildscript {
    repositories {
        mavenCentral()
        maven(url = "https://plugins.gradle.org/m2/")
    }
}

plugins {
    val kotlinVersion = "1.7.21"

    `java-library`
    `maven-publish`
    `java-test-fixtures`
    kotlin("jvm") version kotlinVersion
    kotlin("plugin.allopen") version kotlinVersion
    id("org.cadixdev.licenser") version "0.6.1"
}

repositories {
    mavenCentral()
}

subprojects {
    group = rootProject.group
    version = rootProject.version

    apply {
        plugin("maven-publish")
        plugin("kotlin")
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

        val sourcesJar by creating(Jar::class) {
            archiveClassifier.set("sources")
            from(sourceSets.getByName("main").kotlin.srcDirs)
        }

        artifacts {
            archives(sourcesJar)
        }
    }

    allOpen {
        annotation("org.openjdk.jmh.annotations.State")
    }

    license {
        include("**/*.kt")
        include("**/*.java")
        header(rootProject.file("docs/copyright/COPYRIGHT_HEADER.txt"))
    }

    publishing {
        publications {
            register<MavenPublication>("jar") {
                from(components["java"])
                artifact(tasks.named("sourcesJar"))

                groupId = "org.opentaint.ir"
                artifactId = project.name

                pom {
                    packaging = "jar"
                    name.set("org.opentaint.ir")
                    description.set("fast and effective way to access and analyze java bytecode")
                    url.set("https://github.com/Opentaint/opentaint-ir")
                    scm {
                        url.set("https://github.com/Opentaint/opentaint-ir.git")
                    }
                    issueManagement {
                        url.set("https://github.com/Opentaint/opentaint-ir/issues")
                    }
                    licenses {
                        license {
                            name.set("Apache 2.0")
                            url.set("https://www.apache.org/licenses/LICENSE-2.0")
                        }
                    }
                }
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
