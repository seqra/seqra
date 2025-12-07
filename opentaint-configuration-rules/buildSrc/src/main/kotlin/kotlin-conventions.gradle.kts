import org.opentaint.common.configureDefault
import org.opentaint.common.opentaintRepository

plugins {
    kotlin("jvm")
    `java-library`
    `maven-publish`
}

group = "org.opentaint.configuration"

repositories {
    mavenCentral()
    mavenLocal()
    maven("https://jitpack.io")
}

configureDefault("opentaint-configuration-rules")

opentaintRepository("opentaint-ir")
