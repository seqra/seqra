import org.gradle.api.Project
import org.opentaint.common.OpentaintDependency

object OpentaintConfigDependency : OpentaintDependency {
    val Project.opentaintJavaConfig: String
        get() = propertyDep(group = "org.opentaint.config", name = "java-config")

    val Project.opentaintGoConfig: String
        get() = propertyDep(group = "org.opentaint.config", name = "go-config")

    val Project.opentaintPythonConfig: String
        get() = propertyDep(group = "org.opentaint.config", name = "python-config")
}
