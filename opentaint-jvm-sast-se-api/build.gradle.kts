import org.opentaint.common.KotlinDependency
import OpentaintIrDependency.opentaint_ir_api_jvm

plugins {
    id("kotlin-conventions")
}

dependencies {
    implementation(opentaint_ir_api_jvm)
    implementation(KotlinDependency.Libs.kotlin_logging)
}
