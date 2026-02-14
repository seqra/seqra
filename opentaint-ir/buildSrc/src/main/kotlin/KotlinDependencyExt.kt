import org.gradle.plugin.use.PluginDependenciesSpec
import org.opentaint.common.KotlinDependency
import org.opentaint.common.dep
import org.opentaint.common.id

object KotlinDependencyExt {
    object Libs {
        val kotlin_metadata_jvm_compile = dep(
            group = "org.jetbrains.kotlin",
            name = "kotlin-metadata-jvm",
            version = KotlinDependency.Versions.kotlin
        )

        // note: binary compatibility required
        val kotlin_metadata_jvm_runtime = dep(
            group = "org.jetbrains.kotlin",
            name = "kotlin-metadata-jvm",
            version = "2.3.0"
        )
    }
}

fun PluginDependenciesSpec.kotlinSerialization() = id(KotlinDependency.Plugins.KotlinSerialization)
