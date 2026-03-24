import org.opentaint.common.configureDefaultJvm

plugins {
    `java-library`
    `maven-publish`
}

group = "org.opentaint.rules.builtin.test"

repositories {
    mavenCentral()
    mavenLocal()
    maven("https://jitpack.io")
}

configureDefaultJvm()
