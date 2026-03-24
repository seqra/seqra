import OpentaintIrDependency.opentaint_ir_api_python
import OpentaintIrDependency.opentaint_ir_api_storage
import OpentaintIrDependency.opentaint_ir_core_python
import OpentaintIrDependency.opentaint_ir_storage
import OpentaintUtilDependency.opentaintUtilCommon
import org.opentaint.common.KotlinDependency

plugins {
    id("kotlin-conventions")
    kotlinSerialization()
}

dependencies {
    api(project(":opentaint-dataflow"))
    implementation(opentaintUtilCommon)
    implementation("org.opentaint.opentaint-configuration-rules:configuration-rules-jvm")

    implementation(opentaint_ir_api_python)
    implementation(opentaint_ir_core_python)
    implementation(opentaint_ir_api_storage)
    implementation(opentaint_ir_storage)

    implementation(KotlinDependency.Libs.kotlin_logging)
    implementation(KotlinDependency.Libs.reflect)

    implementation(Libs.fastutil)

    implementation(Libs.sarif4k)
}
