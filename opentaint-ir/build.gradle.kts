group 'org.opentaint.opentaint'

apply plugin: 'java'

if (project.hasProperty('semVer')) {
    project.version = project.semVer
} else {
    project.version = '1.0-SNAPSHOT'
}

buildscript {
    repositories {
        maven {
            url "http://10.198.126.224:8081/repository/opentaint-uber/"
            allowInsecureProtocol true
        }
    }

    dependencies {
        classpath group: 'org.jetbrains.kotlin', name: 'kotlin-gradle-plugin', version: kotlin_version
    }
}

apply plugin: 'base'
apply plugin: 'java'
apply plugin: 'kotlin'
apply plugin: 'maven-publish'

dependencies {
    implementation group: 'io.github.microutils', name: 'kotlin-logging', version: "1.8.3"
    implementation group: 'org.jetbrains.kotlinx', name: 'kotlinx-coroutines-core', version: coroutines_version
    implementation group: 'org.jetbrains.kotlinx', name: 'kotlinx-collections-immutable-jvm', version: collections_version
    implementation group: 'org.jetbrains.kotlin', name: 'kotlin-stdlib-jdk8', version: kotlin_version
    implementation group: 'org.jetbrains.kotlin', name: 'kotlin-reflect', version: kotlin_version
    implementation group: 'org.ow2.asm', name: 'asm', version: asm_version
    implementation group: 'org.ow2.asm', name: 'asm-tree', version: asm_version

    testImplementation(platform('org.junit:junit-bom:5.8.2'))
    testImplementation group: 'org.junit.jupiter', name: 'junit-jupiter'
    testImplementation group: 'com.google.guava', name: 'guava', version: '31.1-jre'
}

compileKotlin {
    kotlinOptions {
        jvmTarget = JavaVersion.VERSION_1_8
        freeCompilerArgs += ["-Xallow-result-return-type", "-Xinline-classes"]
        allWarningsAsErrors = false
    }
}

compileTestKotlin {
    kotlinOptions {
        jvmTarget = JavaVersion.VERSION_1_8
        freeCompilerArgs += ["-Xallow-result-return-type", "-Xinline-classes"]
        allWarningsAsErrors = false
    }
}

test {
    useJUnitPlatform()
    testLogging {
        events "passed", "skipped", "failed"
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}

publishing {
    publications {
        jar(MavenPublication) {
            from components.java
            groupId 'org.opentaint.ir'
            artifactId project.name
        }
    }
    repositories {
        maven {
            name = 'InternalNexusRepository'
            url "http://10.198.126.224:8081/repository/opentaint-external/"
            credentials(PasswordCredentials)
        }
    }
}

repositories {
    maven {
        url "http://10.198.126.224:8081/repository/opentaint-uber/"
        allowInsecureProtocol true
    }
}
