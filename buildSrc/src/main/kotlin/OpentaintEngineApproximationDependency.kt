import org.gradle.api.Project
import org.opentaint.common.OpentaintDependency

object OpentaintEngineApproximationDependency : OpentaintDependency {
    override val opentaintRepository: String = "opentaint-java-approximations"
    override val versionProperty: String = "opentaintEngineApproximationsVersion"

    val Project.opentaint_engine_approximations: String
        get() = propertyDep(group = "org.opentaint.engine.jvm.approximations", name = "approximations")
}
