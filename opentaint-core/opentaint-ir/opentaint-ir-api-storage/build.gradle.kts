import org.opentaint.common.KotlinDependency

plugins {
    id("kotlin-conventions")
}

dependencies {
    api(KotlinDependency.Libs.kotlinx_collections)
}
