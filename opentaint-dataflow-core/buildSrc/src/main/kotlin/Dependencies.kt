@file:Suppress("ConstPropertyName")

import org.opentaint.common.dep

object Versions {
    const val sarif4k = "0.5.0"
    const val fastutil = "8.5.13"
    const val opentaintUtil = "2025.07.29.6c2bc6d"
    const val opentaintConfig = "2025.07.29.6659284"
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

    val opentaintUtilCommon = dep(
        group = "org.opentaint.utils",
        name = "common-util",
        version = Versions.opentaintUtil,
    )

    val opentaintUtilJvm = dep(
        group = "org.opentaint.utils",
        name = "opentaint-jvm-util",
        version = Versions.opentaintUtil,
    )

    val opentaintRulesCommon = dep(
        group = "org.opentaint.configuration",
        name = "configuration-rules-common",
        version = Versions.opentaintConfig,
    )

    val opentaintRulesJvm = dep(
        group = "org.opentaint.configuration",
        name = "configuration-rules-jvm",
        version = Versions.opentaintConfig,
    )
}
