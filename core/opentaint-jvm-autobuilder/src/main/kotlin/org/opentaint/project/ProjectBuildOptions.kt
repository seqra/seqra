package org.opentaint.project

import com.github.ajalt.clikt.parameters.groups.OptionGroup
import com.github.ajalt.clikt.parameters.options.multiple
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.types.path
import org.opentaint.util.newDirectory
import org.opentaint.util.newFile
import java.nio.file.Path

sealed class ProjectBuildOptions(name: String) : OptionGroup(name)

class PortableProjectBuild : ProjectBuildOptions("Portable project build") {
    override val groupHelp: String
        get() = "Produce portable project: copy whole project, dependencies and jvm toolchain"

    val resultDir: Path by option(help = "Portable project build result directory")
        .newDirectory()
        .required()
}

class SimpleProjectBuild : ProjectBuildOptions("Host project build") {
    override val groupHelp: String
        get() = "Build project"

    val result: Path by option(help = "Resolved project configuration (yaml)")
        .newFile()
        .required()
}

sealed class ProjectBuildType(name: String) : OptionGroup(name)

class BuildProject : ProjectBuildType("Build project") {
    override val groupHelp: String
        get() = "Build project using build system (maven, gradle)"
}

class ProjectFromCP : ProjectBuildType("Create project from class path") {
    override val groupHelp: String
        get() = "Create project from class path files"

    val toolchain: Path? by option(help = "Project jvm toolchain")
        .path()

    val cp: List<Path> by option(help = "Project class path")
        .path()
        .multiple(required = true)

    val pkg: List<String> by option(help = "Project package name")
        .multiple()
}
