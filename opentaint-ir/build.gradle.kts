import org.jetbrains.dokka.gradle.DokkaTaskPartial
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

val kotlinVersion: String by rootProject
val coroutinesVersion: String by rootProject
val junit5Version: String by project
val semVer: String? by project
val includeDokka: String? by project

group = "org.opentaint.ir"

project.version = semVer ?: "1.1-SNAPSHOT"

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
    signing
    `java-test-fixtures`
    kotlin("jvm") version kotlinVersion
    kotlin("plugin.allopen") version kotlinVersion
    id("org.jetbrains.dokka") version "1.7.20"

    id("org.cadixdev.licenser") version "0.6.1"
    jacoco
}

repositories {
    mavenCentral()
}

allprojects {
    group = rootProject.group
    version = rootProject.version

    apply {
        plugin("maven-publish")
        plugin("kotlin")
        plugin("org.jetbrains.kotlin.plugin.allopen")
        plugin("org.cadixdev.licenser")
        plugin("jacoco")
        plugin("signing")
        plugin("org.jetbrains.dokka")
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

        withType(DokkaTaskPartial::class).configureEach {
            dokkaSourceSets.configureEach {
                includes.from("README.md")
            }
        }

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

        jacocoTestReport {
            dependsOn(test) // tests are required to run before generating the report
            reports {
                xml.required.set(true)
                html.required.set(true)
            }
        }

        withType<Test> {
            useJUnitPlatform()
            jvmArgs = listOf("-Xmx2g", "-XX:+HeapDumpOnOutOfMemoryError", "-XX:HeapDumpPath=heapdump.hprof")
            testLogging {
                events("passed", "skipped", "failed")
            }
            finalizedBy(jacocoTestReport) // report is always generated after tests run
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
}

val repoUrl: String? = project.properties["repoUrl"] as? String ?: "https://maven.pkg.github.com/Opentaint/opentaint-ir"

if (!repoUrl.isNullOrEmpty()) {
    configure(
        listOf(
            project(":opentaint-ir-api"),
            project(":opentaint-ir-core"),
            project(":opentaint-ir-analysis"),
            project(":opentaint-ir-approximations"),
        )
    ) {
        tasks {
            val dokkaJavadocJar by creating(Jar::class) {
                dependsOn(dokkaJavadoc)
                from(dokkaJavadoc.flatMap { it.outputDirectory })
                archiveClassifier.set("javadoc")
            }

            val sourcesJar by creating(Jar::class) {
                archiveClassifier.set("sources")
                from(sourceSets.getByName("main").kotlin.srcDirs)
            }

            artifacts {
                archives(sourcesJar)
                if (includeDokka != null) {
                    archives(dokkaJavadocJar)
                }
            }

        }
        publishing {
            publications {
                register<MavenPublication>("jar") {
                    from(components["java"])
                    artifact(tasks.named("sourcesJar"))
                    artifact(tasks.named("dokkaJavadocJar"))

                    groupId = "org.opentaint.ir"
                    artifactId = project.name
                    addPom()
                    signPublication(this@configure)
                }
            }

            repositories {
                maven {
                    name = "repo"
                    url = uri(repoUrl)
                    val actor: String? by project
                    val token: String? by project

                    credentials {
                        username = actor
                        password = token
                    }
                }
            }
        }
    }
}

configure(listOf(rootProject)) {
    tasks {
        dokkaHtmlMultiModule {
            removeChildTasks(
                listOf(
                    project(":opentaint-ir-examples"),
                    project(":opentaint-ir-cli"),
                    project(":opentaint-ir-benchmarks")
                )
            )
        }
    }
}

fun MavenPublication.signPublication(project: Project) = with(project) {
    signing {
        val gpgKey: String? by project
        val gpgPassphrase: String? by project
        val gpgKeyValue = gpgKey?.removeSurrounding("\"")
        val gpgPasswordValue = gpgPassphrase

        if (gpgKeyValue != null && gpgPasswordValue != null) {
            useInMemoryPgpKeys(gpgKeyValue, gpgPasswordValue)

            sign(this@signPublication)
        }
    }
}

fun MavenPublication.addPom() {
    pom {
        packaging = "jar"
        name.set("org.opentaint.ir")
        description.set("analyse JVM bytecode with pleasure")
        issueManagement {
            url.set("https://github.com/Opentaint/opentaint-ir/issues")
        }
        scm {
            connection.set("scm:git:https://github.com/Opentaint/opentaint-ir.git")
            developerConnection.set("scm:git:https://github.com/Opentaint/opentaint-ir.git")
            url.set("https://www.opentaint-ir.org")
        }
        url.set("https://www.opentaint-ir.org")
        licenses {
            license {
                name.set("The Apache License, Version 2.0")
                url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
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
