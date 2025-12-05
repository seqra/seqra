plugins {
    `kotlin-dsl`
    `maven-publish`
}

val kotlinVersion = "2.1.0"

group = "org.opentaint"

repositories {
    mavenCentral()
    gradlePluginPortal()
}

dependencies {
    implementation("org.jetbrains.kotlin:kotlin-gradle-plugin:$kotlinVersion")
}

val opentaintOrg = properties.getOrDefault("opentaintOrg", "opentaint")

publishing {
    repositories {
        maven {
            name = "GitHubPackages"
            url = uri("https://maven.pkg.github.com/$opentaintOrg/opentaint-common-build")
            credentials {
                username = System.getenv("GITHUB_ACTOR")
                password = System.getenv("GITHUB_TOKEN")
            }
        }
    }

    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
            tasks.findByName("kotlinSourcesJar")?.let { artifact(it) }
        }
    }
}
