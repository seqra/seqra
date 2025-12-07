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

configureDefault("opentaint-dataflow-core")

opentaintRepository("opentaint-configuration-rules")
opentaintRepository("opentaint-utils")
opentaintRepository("opentaint-ir")
