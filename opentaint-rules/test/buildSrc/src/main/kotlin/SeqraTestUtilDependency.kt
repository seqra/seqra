import org.gradle.api.Project
import org.seqra.common.SeqraDependency

object SeqraTestUtilDependency : SeqraDependency {
    override val seqraRepository: String = "seqra-sast-test-util"
    override val versionProperty: String = "seqraSastTestUtilVersion"

    val Project.seqraSastTestUtil: String
        get() = propertyDep(group = "org.seqra.sast-test-util", name = "seqra-sast-test-util")
}
