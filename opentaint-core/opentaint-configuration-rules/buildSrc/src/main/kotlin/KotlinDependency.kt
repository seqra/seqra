import org.gradle.plugin.use.PluginDependenciesSpec
import org.opentaint.common.KotlinDependency
import org.opentaint.common.id

fun PluginDependenciesSpec.kotlinSerialization() = id(KotlinDependency.Plugins.KotlinSerialization)
