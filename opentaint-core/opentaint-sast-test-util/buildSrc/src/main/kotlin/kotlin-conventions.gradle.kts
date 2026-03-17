import org.opentaint.common.configureDefault
import org.opentaint.common.configureDefaultJvm
import org.opentaint.common.configureDefaultPublishing

plugins {
    `java-library`
    `maven-publish`
}

group = "org.opentaint.sast-test-util"

repositories {
    mavenCentral()
    mavenLocal()
    maven("https://jitpack.io")
}

configureDefaultJvm()
configureDefaultPublishing("opentaint-sast-test-util")
