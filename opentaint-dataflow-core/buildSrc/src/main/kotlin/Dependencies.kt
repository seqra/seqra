@file:Suppress("ConstPropertyName")

import org.opentaint.common.dep

object Versions {
    const val sarif4k = "0.5.0"
    const val fastutil = "8.5.13"
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
}
