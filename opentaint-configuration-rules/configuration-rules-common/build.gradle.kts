import org.opentaint.common.OpentaintIrDependency

plugins {
    id("kotlin-conventions")
}

dependencies {
    implementation(OpentaintIrDependency.Libs.opentaint-ir_api_common)
}
