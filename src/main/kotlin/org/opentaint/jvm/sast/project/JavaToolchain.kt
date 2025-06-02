package org.opentaint.jvm.sast.project

sealed interface JavaToolchain {
    object DefaultJavaToolchain : JavaToolchain
    data class ConcreteJavaToolchain(val javaHome: String) : JavaToolchain
}
