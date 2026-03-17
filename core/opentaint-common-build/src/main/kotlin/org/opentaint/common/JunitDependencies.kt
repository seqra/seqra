@file:Suppress("ConstPropertyName")

package org.opentaint.common

object JunitDependencies {
    object Versions {
        const val junit = "5.9.3"
    }

    object Libs {
        // https://github.com/junit-team/junit5
        const val junit_bom = "org.junit:junit-bom:${Versions.junit}"
        const val junit_jupiter = "org.junit.jupiter:junit-jupiter"
        
        val junit_jupiter_api = dep(
            group = "org.junit.jupiter",
            name = "junit-jupiter-api",
            version = Versions.junit
        )
        val junit_jupiter_engine = dep(
            group = "org.junit.jupiter",
            name = "junit-jupiter-engine",
            version = Versions.junit
        )
        val junit_jupiter_params = dep(
            group = "org.junit.jupiter",
            name = "junit-jupiter-params",
            version = Versions.junit
        )
    }
}