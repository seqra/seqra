@file:Suppress("ConstPropertyName")

import org.seqra.common.dep

object Versions {
    const val clikt = "5.0.0"
    const val logback = "1.3.16" // Latest 1.3.x series requires JDK 8
}

object Libs {
    // https://github.com/qos-ch/logback
    val logback = dep(
        group = "ch.qos.logback",
        name = "logback-classic",
        version = Versions.logback
    )

    // https://github.com/ajalt/clikt
    val clikt = dep(
        group = "com.github.ajalt.clikt",
        name = "clikt",
        version = Versions.clikt
    )
}
