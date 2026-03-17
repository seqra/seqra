package org.opentaint.project

import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.exists

sealed interface JavaToolchain {
    fun path(): Path

    data object DefaultJavaToolchain : JavaToolchain {
        override fun path(): Path = Path(getRunningJvmJavaHome()).toRealPath()
    }

    data class ConcreteJavaToolchain(val javaHome: String) : JavaToolchain {
        override fun path(): Path = Path(javaHome).toRealPath()
    }

    companion object {
        fun getRunningJvmJavaHome(): String {
            val home = System.getProperty("java.home")
            if (!home.endsWith("/jre")) return home

            // note: on some jvm distributions home points to jre instead of home
            val actualHome = home.removeSuffix("/jre")
            if (Path(actualHome).resolve("bin/java").exists()) {
                return actualHome
            }

            return home
        }
    }
}
