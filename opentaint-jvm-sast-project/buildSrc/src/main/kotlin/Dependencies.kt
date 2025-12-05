@file:Suppress("ConstPropertyName")

import org.opentaint.common.dep

object Versions {
    const val opentaintProject = "2025.07.24.f65b6cc"
}

object Libs {
    val opentaintProject = dep(
        group = "org.opentaint.project",
        name = "opentaint-project-model",
        version = Versions.opentaintProject
    )
}
