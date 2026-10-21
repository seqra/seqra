import OpentaintIrDependency.opentaint_ir_api_python
import OpentaintIrDependency.opentaint_ir_api_storage
import OpentaintIrDependency.opentaint_ir_core_python
import OpentaintIrDependency.opentaint_ir_storage
import OpentaintUtilDependency.opentaintUtilCommon
import org.opentaint.common.KotlinDependency
import org.opentaint.common.ensurePirEnvInitialized
import org.opentaint.common.pirEnvironment

plugins {
    id("kotlin-conventions")
    kotlinSerialization()
}

dependencies {
    api(project(":opentaint-dataflow"))
    implementation(opentaintUtilCommon)

    api("org.opentaint.opentaint-configuration-rules:configuration-rules-python")

    implementation(opentaint_ir_api_python)
    implementation(opentaint_ir_core_python)
    implementation(opentaint_ir_api_storage)
    implementation(opentaint_ir_storage)

    implementation(KotlinDependency.Libs.kotlin_logging)
    implementation(KotlinDependency.Libs.reflect)

    implementation(Libs.fastutil)

    implementation(Libs.sarif4k)
}

tasks.withType<Test> {
    maxHeapSize = "4G"
    ensurePirEnvInitialized()

    val aliasSamplesDir = layout.projectDirectory.dir("samples-python-alias")
    inputs.dir(aliasSamplesDir)
    systemProperty("PY_ALIAS_SAMPLES_DIR", aliasSamplesDir.asFile.absolutePath)

    doFirst {
        environment(pirEnvironment())
    }
}
