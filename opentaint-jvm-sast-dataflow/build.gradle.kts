import OpentaintUtilDependency.opentaintUtilJvm
import org.opentaint.common.KotlinDependency
import OpentaintIrDependency.opentaint_ir_api_jvm
import OpentaintIrDependency.opentaint_ir_api_storage
import OpentaintIrDependency.opentaint_ir_core
import OpentaintIrDependency.opentaint_ir_storage
import OpentaintIrDependency.opentaint_ir_approximations

plugins {
    id("kotlin-conventions")
}

dependencies {
    implementation("org.opentaint.opentaint-dataflow-core:opentaint-jvm-dataflow")
    implementation("org.opentaint.opentaint-configuration-rules:configuration-rules-jvm")
    implementation(opentaintUtilJvm)

    implementation(opentaint_ir_api_jvm)
    implementation(opentaint_ir_core)
    implementation(opentaint_ir_approximations)
    implementation(opentaint_ir_api_storage)
    implementation(opentaint_ir_storage)

    implementation(KotlinDependency.Libs.kotlin_logging)
}

val approximationsConfig by configurations.creating

dependencies {
    approximationsConfig(project("dataflow-approximations"))
}

tasks.withType<ProcessResources> {
    from(approximationsConfig.elements.map { files -> files.map { zipTree(it) } }) {
        include("**/*.class")
        into("opentaint-dataflow-approximations")
    }
}
