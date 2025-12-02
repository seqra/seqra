package org.opentaint.common

import org.gradle.api.Project
import org.gradle.kotlin.dsl.maven
import org.gradle.kotlin.dsl.repositories

fun Project.opentaintRepository(name: String) {
    repositories {
        maven("https://maven.pkg.github.com/explyt/$name") {
            credentials {
                username = System.getenv("GITHUB_ACTOR")
                password = System.getenv("GITHUB_TOKEN")
            }
        }
    }
}
