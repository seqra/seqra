import org.gradle.api.Project
import org.opentaint.common.OpentaintDependency

object OpentaintUtilDependency : OpentaintDependency {

    val Project.opentaintUtilJvm: String
        get() = propertyDep(group = "org.opentaint.utils", name = "opentaint-jvm-util")

    val Project.opentaintUtilCli: String
            get() = propertyDep(group = "org.opentaint.utils", name = "cli-util")
}
