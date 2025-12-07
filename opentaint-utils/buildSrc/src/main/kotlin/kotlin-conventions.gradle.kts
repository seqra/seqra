import org.opentaint.common.configureDefault
import org.opentaint.common.opentaintRepository

plugins {
    kotlin("jvm")
    `java-library`
    `maven-publish`
}

group = "org.opentaint.utils"

repositories {
    mavenCentral()
    mavenLocal()
    maven("https://jitpack.io")
}

configureDefault("opentaint-utils")

opentaintRepository("opentaint-ir")
