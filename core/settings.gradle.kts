import kotlin.io.path.Path
import kotlin.io.path.div
import kotlin.io.path.exists

rootProject.name = "opentaint-jvm-sast"

include("opentaint-java-querylang")
include("opentaint-java-querylang:samples")
include("samples")

fun DependencySubstitutions.substituteProjects(group: String, projects: List<String>) {
    for (projectName in projects) {
        substitute(module("$group:$projectName")).using(project(":$projectName"))
    }
}

includeBuild("opentaint-configuration-rules") {
    dependencySubstitution {
        substituteProjects("org.opentaint.opentaint-configuration-rules", listOf("configuration-rules-common", "configuration-rules-jvm"))
    }
}

includeBuild("opentaint-dataflow-core") {
    dependencySubstitution {
        substituteProjects("org.opentaint.opentaint-dataflow-core", listOf("opentaint-dataflow", "opentaint-jvm-dataflow", "opentaint-python-dataflow"))
    }
}

includeBuild("opentaint-ir") {
    dependencySubstitution {
        val modules = listOf(
            "opentaint-ir-api-common",
            "opentaint-ir-api-jvm",
            "opentaint-ir-api-storage",
            "opentaint-ir-approximations",
            "opentaint-ir-core",
            "opentaint-ir-storage",
        )
        substituteProjects("org.opentaint.ir", modules)

        val pythonModules = listOf(
            "opentaint-ir-api-python",
            "opentaint-ir-impl-python"
        )

        for (module in pythonModules) {
            substitute(module("org.opentaint.ir.python:$module")).using(project(":python:$module"))
        }

        val goModules = listOf(
            "go-ir-api",
            "go-ir-client"
        )

        for (module in goModules) {
            substitute(module("org.opentaint.ir.go:$module")).using(project(":go:$module"))
        }
    }
}

includeBuild("opentaint-jvm-sast-dataflow") {
    dependencySubstitution {
        substitute(module("org.opentaint.sast:dataflow")).using(project(":"))
    }
}

includeBuild("opentaint-jvm-sast-project") {
    dependencySubstitution {
        substitute(module("org.opentaint.sast:project")).using(project(":"))
    }
}

includeBuild("opentaint-project-model") {
    dependencySubstitution {
        substitute(module("org.opentaint.project:opentaint-project-model")).using(project(":"))
    }
}

includeBuild("opentaint-utils") {
    dependencySubstitution {
        val modules = listOf(
            "cli-util",
            "common-util",
            "opentaint-jvm-util",
        )
        substituteProjects("org.opentaint.utils", modules)
    }
}

includeBuild("opentaint-config") {
    dependencySubstitution {
        substitute(module("org.opentaint.config:opentaint-config")).using(project(":"))
    }
}

includeBuild("opentaint-sast-test-util") {
    dependencySubstitution {
        substitute(module("org.opentaint.sast-test-util:opentaint-sast-test-util")).using(project(":"))
    }
}

includeBuild("opentaint-jvm-sast-se-api") {
    dependencySubstitution {
        substitute(module("org.opentaint.sast.se:api")).using(project(":"))
    }
}

includeBuild("opentaint-jvm-autobuilder")

if (Path("opentaint-jvm-sast-se").div("settings.gradle.kts").exists()) {
    includeBuild("opentaint-jvm-sast-se")
}
