import org.jetbrains.kotlin.konan.properties.loadProperties

plugins {
    `kotlin-dsl`
}

val kotlinVersion = "2.1.0"

val rootProperties = layout.projectDirectory.file("../gradle.properties").asFile.absolutePath.let { loadProperties(it) }

repositories {
    mavenCentral()
    gradlePluginPortal()

    seqraRepository("seqra-common-build")
}

dependencies {
    implementation("org.seqra:seqra-common-build:${rootProperties.getProperty("seqraBuildVersion")}")
    implementation("org.jetbrains.kotlin:kotlin-gradle-plugin:$kotlinVersion")
}

fun seqraRepository(name: String) {
    val seqraOrg = rootProperties.getOrDefault("seqraOrg", "seqra")

    repositories {
        maven("https://maven.pkg.github.com/$seqraOrg/$name") {
            val seqraUser = System.getenv("SEQRA_GITHUB_ACTOR")
            val seqraToken = System.getenv("SEQRA_GITHUB_TOKEN")

            if (seqraUser != null && seqraToken != null) {
                credentials {
                    username = seqraUser
                    password = seqraToken
                }
            }
        }
    }
}
