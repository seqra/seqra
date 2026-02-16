import org.opentaint.common.configureDefault

plugins {
    kotlin("jvm")
    `java-library`
    `maven-publish`
}

group = "org.opentaint.config"

repositories {
    mavenCentral()
    mavenLocal()
    maven("https://jitpack.io")
}

configureDefault("opentaint-config")
