plugins {
    `kotlin-conventions`
}

tasks.withType<ProcessResources> {
    val configFile = layout.projectDirectory.file("config/config.yaml")

    doLast {
        check(configFile.asFile.exists()) { "Configuration file not found" }
    }

    from(configFile)
}
