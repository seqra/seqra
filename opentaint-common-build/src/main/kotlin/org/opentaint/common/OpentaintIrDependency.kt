@file:Suppress("ConstPropertyName")

package org.opentaint.common

object Opentaint-IRDependency {
    object Versions {
        const val opentaint-ir = "62685936a106dd6e41c623beecc19c31f5a0b942"
    }

    object Libs {
        // https://github.com/opentaint/opentaint-ir
        const val opentaint-irPackage = "com.github.Opentaint.opentaint-ir" // use "org.opentaint.ir" with includeBuild

        val opentaint-ir_core = dep(
            group = opentaint-irPackage,
            name = "opentaint-ir-core",
            version = Versions.opentaint-ir
        )

        val opentaint-ir_api_common = dep(
            group = opentaint-irPackage,
            name = "opentaint-ir-api-common",
            version = Versions.opentaint-ir
        )

        val opentaint-ir_api_jvm = dep(
            group = opentaint-irPackage,
            name = "opentaint-ir-api-jvm",
            version = Versions.opentaint-ir
        )

        val opentaint-ir_api_storage = dep(
            group = opentaint-irPackage,
            name = "opentaint-ir-api-storage",
            version = Versions.opentaint-ir
        )

        val opentaint-ir_storage = dep(
            group = opentaint-irPackage,
            name = "opentaint-ir-storage",
            version = Versions.opentaint-ir
        )

        val opentaint-ir_approximations = dep(
            group = opentaint-irPackage,
            name = "opentaint-ir-approximations",
            version = Versions.opentaint-ir
        )

        val opentaint-ir_ets = dep(
            group = opentaint-irPackage,
            name = "opentaint-ir-ets",
            version = Versions.opentaint-ir
        )
    }
}