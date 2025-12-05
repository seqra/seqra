@file:Suppress("ConstPropertyName")

import org.opentaint.common.dep

object Versions {
    const val opentaintUtil = "2025.07.24.e2de8fa"
    const val opentaintConfig = "2025.07.24.d34a262"
}

object Libs {
    val opentaintUtilJvm = dep(
        group = "org.opentaint.utils",
        name = "opentaint-jvm-util",
        version = Versions.opentaintUtil,
    )

    val opentaintRulesJvm = dep(
        group = "org.opentaint.configuration",
        name = "configuration-rules-jvm",
        version = Versions.opentaintConfig,
    )
}
