group 'org.opentaint'

if (project.hasProperty('semVer')) {
    project.version = project.semVer
} else {
    project.version = '1.0-SNAPSHOT'
}

buildscript {
    repositories {
        repositories {
            mavenCentral()
            maven { url "https://plugins.gradle.org/m2/" }
        }
    }

    dependencies {
        classpath group: 'org.jetbrains.kotlin', name: 'kotlin-gradle-plugin', version: kotlin_version
        classpath group: 'org.jetbrains.kotlin', name: 'kotlin-allopen', version: kotlin_version
        classpath group: 'org.jetbrains.kotlin', name: 'kotlin-serialization', version: kotlin_version
        classpath group: 'org.jetbrains.kotlinx', name: 'kotlinx-benchmark-plugin', version: '0.4.4'

    }
}

subprojects {

    group = rootProject.group
    version = rootProject.version

    apply plugin: 'base'
    apply plugin: 'java'
    apply plugin: 'kotlin'
    apply plugin: 'maven-publish'
    apply plugin: 'org.jetbrains.kotlin.plugin.allopen'
    apply plugin: 'org.jetbrains.kotlin.plugin.serialization'
    apply plugin: 'org.jetbrains.kotlinx.benchmark'


    publishing {
        publications {
            jar(MavenPublication) {
                from components.java
                groupId 'org.opentaint'
                artifactId project.name
            }
        }
    }

    repositories {
        mavenCentral()
        maven { url 'https://jitpack.io' }
    }

    publishing {
        repositories {
            maven {
                name = "GitHubPackages"
                url = uri("https://maven.pkg.github.com/Opentaint/java-compilation-database")
                credentials {
                    username = project.findProperty("gprUser") ?: System.getenv("USERNAME")
                    password = project.findProperty("gprKey") ?: System.getenv("TOKEN")
                }
            }
        }
        publications {
            gpr(MavenPublication) {
                from components.java
                groupId 'org.opentaint'
                artifactId project.name
            }
        }
    }

    dependencies {
        testImplementation(platform('org.junit:junit-bom:5.8.2'))
        testImplementation group: 'org.junit.jupiter', name: 'junit-jupiter'
        testImplementation group: 'com.google.guava', name: 'guava', version: '31.1-jre'
        testImplementation group: 'org.jetbrains.kotlinx', name: 'kotlinx-benchmark-runtime', version: '0.4.4'
    }

    allOpen {
        annotation("org.openjdk.jmh.annotations.State")
    }

    compileKotlin {
        kotlinOptions {
            jvmTarget = JavaVersion.VERSION_1_8
            languageVersion = "1.4"
            apiVersion = "1.4"
            freeCompilerArgs += ["-Xallow-result-return-type", "-Xinline-classes"]
            allWarningsAsErrors = false
        }
    }

    compileTestKotlin {
        kotlinOptions {
            jvmTarget = JavaVersion.VERSION_1_8
            languageVersion = "1.4"
            apiVersion = "1.4"
            freeCompilerArgs += ["-Xallow-result-return-type", "-Xinline-classes"]
            allWarningsAsErrors = false
        }
    }

    test {
        useJUnitPlatform()
        jvmArgs = ['-Xmx2g', '-XX:+HeapDumpOnOutOfMemoryError', '-XX:HeapDumpPath=heapdump.hprof']
        testLogging {
            events "passed", "skipped", "failed"
        }
    }

    java {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }


// Configure benchmark
    benchmark {
        configurations {
            main { // main configuration is created automatically, but you can change its defaults
                warmups = 3 // number of warmup iterations
                iterations = 5 // number of iterations
            }
        }

        // Setup configurations
        targets {
            // This one matches sourceSet name above
            register("test") {
                jmhVersion = "1.21"
            }
        }
    }
}
