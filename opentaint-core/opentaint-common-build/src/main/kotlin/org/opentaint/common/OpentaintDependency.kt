package org.opentaint.common

import org.gradle.api.Project

interface OpentaintDependency {
    fun Project.propertyDep(group: String, name: String): String {
        return "$group:$name"
    }
}
