import org.seqra.common.configureDefaultJvm

plugins {
    `java-library`
    `maven-publish`
}

group = "org.seqra.rules.builtin.test"

repositories {
    mavenCentral()
    mavenLocal()
    maven("https://jitpack.io")
}

configureDefaultJvm()
