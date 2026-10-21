import OpentaintConfigurationDependency.opentaintRulesPython

plugins {
    `kotlin-conventions`
}

dependencies {
    api(project(":java-config"))
    implementation(opentaintRulesPython)
}

tasks.withType<ProcessResources> {
    val modelDir = layout.projectDirectory.dir("../../../model/python")

    from(modelDir) {
        into("model/python")
    }
}
