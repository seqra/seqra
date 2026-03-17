package org.opentaint.common

import org.gradle.plugin.use.PluginDependenciesSpec
import org.gradle.plugin.use.PluginDependencySpec

fun dep(group: String, name: String, version: String): String = "$group:$name:$version"

class ProjectPlugin(val id: String, val version: String)

fun PluginDependenciesSpec.id(plugin: ProjectPlugin): PluginDependencySpec =
    id(plugin.id).version(plugin.version)
