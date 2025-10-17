package org.opentaint.jvm.sast.project

import com.charleskorn.kaml.SingleLineStringStyle
import com.charleskorn.kaml.Yaml
import com.charleskorn.kaml.YamlConfiguration
import com.charleskorn.kaml.decodeFromStream
import com.charleskorn.kaml.encodeToStream
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.inputStream
import kotlin.io.path.outputStream
import kotlin.io.path.relativeTo

@Serializable
data class Project(
    val sourceRoot: @Serializable(with = PathAsStringSerializer::class) Path,
    val javaToolchain: @Serializable(with = PathAsStringSerializer::class) Path? = null,
    val modules: List<ProjectModuleClasses>,
    val dependencies: List<@Serializable(with = PathAsStringSerializer::class) Path> = emptyList(),
    val subProjects: List<Project> = emptyList(),
) {
    fun relativeTo(path: Path): Project = Project(
        sourceRoot.relativeTo(path),
        javaToolchain?.relativeTo(path),
        modules.map { it.relativeTo(path) },
        dependencies.map { it.relativeTo(path) },
        subProjects.map { it.relativeTo(path) }
    )

    fun resolve(base: Path): Project = Project(
        base.resolve(sourceRoot),
        javaToolchain?.let { base.resolve(it) },
        modules.map { it.resolve(base) },
        dependencies.map { base.resolve(it) },
        subProjects.map { it.resolve(base) }
    )

    fun dump(path: Path) {
        path.outputStream().use {
            yaml().encodeToStream(this, it)
        }
    }

    companion object {
        private fun yaml() = Yaml(
            configuration =
            YamlConfiguration(
                encodeDefaults = false,
                singleLineStringStyle = SingleLineStringStyle.PlainExceptAmbiguous,
            )
        )

        fun load(path: Path): Project = path.inputStream().use {
            yaml().decodeFromStream<Project>(it)
        }
    }
}

@Serializable
data class ProjectModuleClasses(
    val moduleSourceRoot: @Serializable(with = PathAsStringSerializer::class) Path,
    val moduleClasses: List<@Serializable(with = PathAsStringSerializer::class) Path>
) {
    fun relativeTo(path: Path): ProjectModuleClasses = ProjectModuleClasses(
        moduleSourceRoot.relativeTo(path),
        moduleClasses.map { it.relativeTo(path) }
    )

    fun resolve(base: Path): ProjectModuleClasses = ProjectModuleClasses(
        base.resolve(moduleSourceRoot),
        moduleClasses.map { base.resolve(it) }
    )
}

object PathAsStringSerializer : KSerializer<Path> {
    override val descriptor = PrimitiveSerialDescriptor("Path", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: Path) = encoder.encodeString(value.toString())

    override fun deserialize(decoder: Decoder): Path = Path(decoder.decodeString())
}
