import org.opentaint.common.dep

object TestDependencies {
    object Versions {
        const val javax_activation = "1.1"
        const val javax_mail = "1.4.7"
        const val jetbrains_annotations = "20.1.0"
        const val joda_time = "2.12.5"
        const val slf4j = "1.7.36"
        const val jgit_test_only_version = "5.9.0.202009080501-r"
        const val commons_compress_test_only_version = "1.21"
    }

    object Libs {
        val slf4j_simple = dep(
            group = "org.slf4j",
            name = "slf4j-simple",
            version = Versions.slf4j
        )

        val javax_activation = dep(
            group = "javax.activation",
            name = "activation",
            version = Versions.javax_activation
        )

        val javax_mail = dep(
            group = "javax.mail",
            name = "mail",
            version = Versions.javax_mail
        )

        val joda_time = dep(
            group = "joda-time",
            name = "joda-time",
            version = Versions.joda_time
        )

        val jetbrains_annotations = dep(
            group = "org.jetbrains",
            name = "annotations",
            version = Versions.jetbrains_annotations
        )

        val jgit_test_only_lib = dep(
            group = "org.eclipse.jgit",
            name = "org.eclipse.jgit",
            version = Versions.jgit_test_only_version
        )

        val commons_compress_test_only_lib = dep(
            group = "org.apache.commons",
            name = "commons-compress",
            version = Versions.commons_compress_test_only_version
        )
    }
}
