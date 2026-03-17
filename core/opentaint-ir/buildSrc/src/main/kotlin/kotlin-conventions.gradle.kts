import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.opentaint.common.configureDefault

plugins {
    kotlin("jvm")
    `java-library`
    `maven-publish`
    `java-test-fixtures`
}

group = "org.opentaint.ir"

repositories {
    mavenCentral()
    mavenLocal()
    maven("https://jitpack.io")
}

configureDefault("opentaint-ir")

tasks {
    withType<JavaCompile> {
        sourceCompatibility = "1.8"
        targetCompatibility = "1.8"
        options.encoding = "UTF-8"
        options.compilerArgs.add("-Xlint:all")
    }

    compileKotlin {
        compilerOptions {
            jvmTarget = JvmTarget.JVM_1_8
            freeCompilerArgs.add("-Xjvm-default=all")
        }
    }

    compileTestJava {
        targetCompatibility = runtimeJavaVersion()
    }

    compileTestFixturesJava {
        targetCompatibility = "1.8"
        options.compilerArgs.remove("-Werror")
    }

    compileTestKotlin {
        compilerOptions {
            jvmTarget = JvmTarget.fromTarget(runtimeJavaVersion())
        }
    }

    compileTestFixturesKotlin {
        compilerOptions {
            jvmTarget = JvmTarget.JVM_1_8
        }
    }

    test {
        useJUnitPlatform {
            excludeTags(Tests.lifecycleTag)
        }

        setupTest()
    }

    create("lifecycleTest", Test::class) {
        useJUnitPlatform {
            includeTags(Tests.lifecycleTag)
        }

        setupTest()
    }

    jar {
        manifest {
            attributes["Implementation-Title"] = project.name
            attributes["Implementation-Version"] = archiveVersion
        }
    }
}

// Exclude test fixtures from publication, as we use it only internally
plugins.withId("org.gradle.java-test-fixtures") {
    val component = components["java"] as AdhocComponentWithVariants
    component.withVariantsFromConfiguration(configurations["testFixturesApiElements"]) { skip() }
    component.withVariantsFromConfiguration(configurations["testFixturesRuntimeElements"]) { skip() }
}
