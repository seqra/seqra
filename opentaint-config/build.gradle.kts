import OpentaintConfigurationDependency.opentaintRulesJvm

plugins {
    `kotlin-conventions`
}

dependencies {
    implementation(opentaintRulesJvm)
}

//sourceSets {
//    main {
//        resources.srcDirs += files("config")
//    }
//}

tasks.withType<ProcessResources> {
    val configDir = layout.projectDirectory.dir("config")

    from(configDir)
}
