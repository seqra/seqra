import org.jetbrains.dokka.gradle.DokkaTaskPartial
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

val semVer: String? by project
val includeDokka: String? by project

group = "org.opentaint.ir"
version = semVer ?: "1.4-SNAPSHOT"

plugins {
    kotlin("jvm") version Versions.kotlin
    kotlin("plugin.allopen") version Versions.kotlin
    kotlin("plugin.serialization") version Versions.kotlin apply false
    id(Plugins.Dokka)
    id(Plugins.Licenser)
    `java-library`
    `java-test-fixtures`
    `maven-publish`
    signing
    jacoco
}

allprojects {
    group = rootProject.group
    version = rootProject.version

    apply {
        plugin("kotlin")
        plugin("org.jetbrains.kotlin.plugin.allopen")
        plugin(Plugins.Dokka.id)
        plugin(Plugins.Licenser.id)
        plugin("maven-publish")
        plugin("signing")
        plugin("jacoco")
    }

    repositories {
        mavenCentral()
        maven("https://jitpack.io")
        maven("https://s01.oss.sonatype.org/content/repositories/orgopentaintsoot-1004/")
        maven("https://plugins.gradle.org/m2")
        maven("https://www.jetbrains.com/intellij-repository/releases")
        maven("https://cache-redirector.jetbrains.com/maven-central")
    }

    dependencies {
        // Kotlin
        implementation(platform(kotlin("bom")))
        implementation(kotlin("stdlib-jdk8"))

        // JUnit
        testImplementation(platform(Libs.junit_bom))
        testImplementation(Libs.junit_jupiter)

        // Test dependencies
        testRuntimeOnly(Libs.guava)
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

        test {
            useJUnitPlatform {
                excludeTags(Tests.lifecycleTag)
            }
            setup(jacocoTestReport)
        }

        jar {
            manifest {
                attributes["Implementation-Title"] = project.name
                attributes["Implementation-Version"] = archiveVersion
            }
        }

        val lifecycleTest by creating(Test::class) {
            useJUnitPlatform {
                includeTags(Tests.lifecycleTag)
            }
            setup(jacocoTestReport)
        }

        jacocoTestReport {
            classDirectories.setFrom(files(classDirectories.files.map {
                fileTree(it) {
                    excludes.add("org/opentaint/ir/impl/storage/jooq/**")
                }
            }))
            reports {
                xml.required.set(true)
                html.required.set(true)
            }
        }

        withType<DokkaTaskPartial> {
            dokkaSourceSets.configureEach {
                includes.from("README.md")
            }
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

tasks.dokkaHtmlMultiModule {
    removeChildTasks(
        listOf(
            project(":opentaint-ir-examples"),
            project(":opentaint-ir-cli"),
            project(":opentaint-ir-benchmarks")
        )
    )
}

val repoUrl: String? = project.properties["repoUrl"] as? String
    ?: "https://maven.pkg.github.com/Opentaint/opentaint-ir"

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

tasks.wrapper {
    gradleVersion = "8.3"
    distributionType = Wrapper.DistributionType.ALL
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
