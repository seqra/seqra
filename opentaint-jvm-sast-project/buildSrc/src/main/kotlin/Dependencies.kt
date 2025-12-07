@file:Suppress("ConstPropertyName")

import org.opentaint.common.dep

object Versions {
    const val opentaintProject = "2025.07.29.e0e1756"
}

object Libs {
    val opentaintProject = dep(
        group = "org.opentaint.project",
        name = "opentaint-project-model",
        version = Versions.opentaintProject
    )
}
