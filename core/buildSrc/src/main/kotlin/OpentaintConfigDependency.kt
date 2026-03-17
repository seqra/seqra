import org.gradle.api.Project
import org.opentaint.common.OpentaintDependency

object OpentaintConfigDependency : OpentaintDependency {
    val Project.opentaintConfig: String
        get() = propertyDep(group = "org.opentaint.config", name = "opentaint-config")
}
