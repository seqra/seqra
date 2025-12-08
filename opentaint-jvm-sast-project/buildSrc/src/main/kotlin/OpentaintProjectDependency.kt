import org.gradle.api.Project
import org.opentaint.common.OpentaintDependency

object OpentaintProjectDependency : OpentaintDependency {

    val Project.opentaintProject: String
        get() = propertyDep(group = "org.opentaint.project", name = "opentaint-project-model")
}
