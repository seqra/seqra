@file:Suppress("ConstPropertyName")

import org.opentaint.common.dep

object Versions {
    const val sarif4k = "0.5.0"
    const val fastutil = "8.5.13"
    const val jdot = "1.0"
}

object Libs {
    // https://github.com/detekt/sarif4k
    val sarif4k = dep(
        group = "io.github.detekt.sarif4k",
        name = "sarif4k",
        version = Versions.sarif4k
    )

    val fastutil = dep(
        group = "it.unimi.dsi",
        name = "fastutil-core",
        version = Versions.fastutil,
    )

    val jdot = dep(
        group = "info.leadinglight",
        name = "jdot",
        version = Versions.jdot
    )
}
