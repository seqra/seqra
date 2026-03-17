import OpentaintConfigurationDependency.opentaintRulesJvm

plugins {
    `kotlin-conventions`
}

dependencies {
    implementation(opentaintRulesJvm)
}

tasks.withType<ProcessResources> {
    val configDir = layout.projectDirectory.dir("config")

    from(configDir)
}
