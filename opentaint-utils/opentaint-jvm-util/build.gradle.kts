import org.opentaint.common.Opentaint-IRDependency
import org.opentaint.common.KotlinDependency

plugins {
    id("kotlin-conventions")
}

dependencies {
    api(project(":common-util"))

    implementation(Opentaint-IRDependency.Libs.opentaint-ir_api_jvm)
    implementation(Opentaint-IRDependency.Libs.opentaint-ir_core)
    implementation(Opentaint-IRDependency.Libs.opentaint-ir_approximations)

    implementation(KotlinDependency.Libs.reflect)
}
