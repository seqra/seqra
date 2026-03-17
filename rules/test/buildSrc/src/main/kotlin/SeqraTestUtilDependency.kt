import org.gradle.api.Project
import org.opentaint.common.OpentaintDependency

object OpenTaintTestUtilDependency : OpentaintDependency {
    val Project.seqraSastTestUtil: String
        get() = propertyDep(group = "org.opentaint.sast-test-util", name = "opentaint-sast-test-util")
}
