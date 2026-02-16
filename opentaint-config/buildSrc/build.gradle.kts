plugins {
    `kotlin-dsl`
}

val kotlinVersion = "2.1.0"

repositories {
    mavenCentral()
    gradlePluginPortal()
}

dependencies {
    implementation("org.opentaint:opentaint-common-build")
    implementation("org.jetbrains.kotlin:kotlin-gradle-plugin:$kotlinVersion")
}
