import org.gradle.plugin.use.PluginDependenciesSpec
import org.opentaint.common.KotlinDependency
import org.opentaint.common.dep
import org.opentaint.common.id

object KotlinDependencyExt {
    object Libs {
        val kotlin_metadata_jvm = dep(
            group = "org.jetbrains.kotlin",
            name = "kotlin-metadata-jvm",
            version = KotlinDependency.Versions.kotlin
        )
    }
}

fun PluginDependenciesSpec.kotlinSerialization() = id(KotlinDependency.Plugins.KotlinSerialization)
