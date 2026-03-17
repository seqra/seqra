import org.gradle.api.Project
import org.opentaint.common.OpentaintDependency

object OpentaintConfigurationDependency : OpentaintDependency {
    val Project.opentaintRulesJvm: String
        get() = propertyDep(group = "org.opentaint.opentaint-configuration-rules", name = "configuration-rules-jvm")
}
