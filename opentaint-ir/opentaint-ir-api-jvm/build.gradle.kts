import org.opentaint.common.KotlinDependency

plugins {
    id("kotlin-conventions")
}

dependencies {
    api(project(":opentaint-ir-api-common"))
    api(project(":opentaint-ir-api-storage"))

    api(Libs.asm)
    api(Libs.asm_tree)
    api(Libs.asm_commons)
    api(Libs.asm_util)

    api(KotlinDependency.Libs.kotlinx_coroutines_core)
}
