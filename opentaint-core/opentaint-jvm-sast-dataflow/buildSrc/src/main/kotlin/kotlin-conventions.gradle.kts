import org.opentaint.common.configureDefault

plugins {
    kotlin("jvm")
    `java-library`
    `maven-publish`
}

group = "org.opentaint"

repositories {
    mavenCentral()
    mavenLocal()
    maven("https://jitpack.io")
}

configureDefault("opentaint-jvm-sast-dataflow")
