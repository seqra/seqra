import org.opentaint.common.KotlinDependency

plugins {
    id("kotlin-conventions")
}

dependencies {
    implementation(OpentaintIrDependency.Libs.opentaint_ir_api_jvm)
    implementation(KotlinDependency.Libs.kotlin_logging)
}
