import org.opentaint.common.OpentaintIrDependency
import org.opentaint.common.KotlinDependency

plugins {
    id("kotlin-conventions")
    kotlinSerialization()
}

dependencies {
    api(project(":opentaint-dataflow"))
    implementation(Libs.opentaintUtilCommon)
    implementation(Libs.opentaintUtilJvm)
    implementation(Libs.opentaintRulesJvm)

    implementation(OpentaintIrDependency.Libs.opentaint-ir_api_jvm)
    implementation(OpentaintIrDependency.Libs.opentaint-ir_core)
    implementation(OpentaintIrDependency.Libs.opentaint-ir_api_storage)
    implementation(OpentaintIrDependency.Libs.opentaint-ir_storage)

    implementation(KotlinDependency.Libs.kotlin_logging)
    implementation(KotlinDependency.Libs.reflect)

    implementation(Libs.fastutil)

    implementation(Libs.sarif4k)
}
