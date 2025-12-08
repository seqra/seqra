import org.gradle.api.Project
import org.opentaint.common.OpentaintDependency

object OpentaintConfigurationDependency : OpentaintDependency {
    override val opentaintRepository: String = "opentaint-configuration-rules"
    override val versionProperty: String = "opentaintConfigVersion"

    val Project.opentaintRulesJvm: String
        get() = propertyDep(group = "org.opentaint.configuration", name = "configuration-rules-jvm")
}
