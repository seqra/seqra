import org.gradle.api.Project
import org.opentaint.common.OpentaintDependency

object OpentaintEngineApiDependency : OpentaintDependency {
    override val opentaintRepository: String = "opentaint-jvm-engine-api"
    override val versionProperty: String = "opentaintEngineApiVersion"

    val Project.opentaint_engine_api: String
        get() = propertyDep(group = "org.opentaint.jvm.engine.api", name = "opentaint-jvm-engine-api")
}
