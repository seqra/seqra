import org.gradle.api.Project
import org.opentaint.common.OpentaintDependency

object OpentaintTestUtilDependency : OpentaintDependency {
    val Project.opentaintSastTestUtil: String
        get() = propertyDep(group = "org.opentaint.sast-test-util", name = "opentaint-sast-test-util")
}
