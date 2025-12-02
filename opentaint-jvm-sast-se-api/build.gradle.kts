import org.opentaint.common.OpentaintIrDependency
import org.opentaint.common.KotlinDependency

plugins {
    id("kotlin-conventions")
}

dependencies {
    implementation(OpentaintIrDependency.Libs.opentaint-ir_api_jvm)
    implementation(KotlinDependency.Libs.kotlin_logging)
}
