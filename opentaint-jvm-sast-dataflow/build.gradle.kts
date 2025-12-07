import org.opentaint.common.KotlinDependency

plugins {
    id("kotlin-conventions")
}

dependencies {
    implementation("org.opentaint.opentaint-dataflow-core:opentaint-jvm-dataflow")
    implementation(Libs.opentaintRulesJvm)
    implementation(Libs.opentaintUtilJvm)

    implementation(OpentaintIrDependency.Libs.opentaint_ir_api_jvm)
    implementation(OpentaintIrDependency.Libs.opentaint_ir_core)
    implementation(OpentaintIrDependency.Libs.opentaint_ir_approximations)
    implementation(OpentaintIrDependency.Libs.opentaint_ir_api_storage)
    implementation(OpentaintIrDependency.Libs.opentaint_ir_storage)

    implementation(KotlinDependency.Libs.kotlin_logging)
}
