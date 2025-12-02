import org.opentaint.common.configureDefault
import org.opentaint.common.opentaintRepository

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

configureDefault("opentaint-jvm-sast-project")

opentaintRepository("opentaint-project-model")
opentaintRepository("opentaint-configuration-rules")
opentaintRepository("opentaint-utils")

