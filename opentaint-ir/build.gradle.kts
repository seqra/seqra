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
        classpath group: 'org.jetbrains.kotlinx', name: 'kotlinx-benchmark-plugin', version: '0.4.4'
    }
}

apply plugin: 'base'
apply plugin: 'java'
apply plugin: 'kotlin'
apply plugin: 'maven-publish'
apply plugin: 'org.jetbrains.kotlin.plugin.allopen'
apply plugin: 'org.jetbrains.kotlinx.benchmark'

dependencies {
    implementation group: 'io.github.microutils', name: 'kotlin-logging', version: "1.8.3"
    implementation group: 'org.jetbrains.kotlinx', name: 'kotlinx-coroutines-core', version: coroutines_version
    implementation group: 'org.jetbrains.kotlinx', name: 'kotlinx-collections-immutable-jvm', version: collections_version
    implementation group: 'org.jetbrains.kotlin', name: 'kotlin-stdlib-jdk8', version: kotlin_version
    implementation group: 'org.jetbrains.kotlin', name: 'kotlin-reflect', version: kotlin_version
    implementation group: 'org.ow2.asm', name: 'asm', version: asm_version
    implementation group: 'org.ow2.asm', name: 'asm-tree', version: asm_version
    implementation group: 'org.jetbrains.xodus', name: 'xodus-entity-store', version: xodus_version
    implementation group: 'com.fasterxml.jackson.core', name: 'jackson-databind', version: '2.13.2'
    implementation group: 'com.fasterxml.jackson.module', name: 'jackson-module-kotlin', version: '2.13.2'

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

repositories {
    repositories {
        mavenCentral()
    }
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