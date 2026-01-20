package org.opentaint.jvm.sast.project.spring

import org.opentaint.ir.api.jvm.JIRClasspath
import org.opentaint.ir.api.jvm.JIRMethod
import org.opentaint.ir.api.jvm.JIRPrimitiveType
import org.opentaint.ir.api.jvm.JIRRefType
import org.opentaint.ir.api.jvm.ext.isSubClassOf
import org.opentaint.ir.api.jvm.ext.methods
import org.opentaint.ir.api.jvm.ext.unboxIfNeeded
import org.opentaint.ir.impl.cfg.util.isPrimitive
import org.opentaint.jvm.sast.project.ProjectClasses

fun SpringWebProjectContext.analyzeSpringRepositories(cp: JIRClasspath, projectClasses: ProjectClasses) {
    val repositoryClass = cp.findClassOrNull(SpringRepository) ?: return
    val repositoryComponents = allComponents().filter { it.isSubClassOf(repositoryClass) }
    val repositoryMethods = repositoryComponents.flatMapTo(mutableSetOf()) { repo ->
        repo.methods.mapNotNull { findRepositoryMethod(it, projectClasses) }
    }

    val types = SpringQueryReferenceReturnTypes(cp)
    for (repositoryMethod in repositoryMethods) {
        springRepositoryMethods[repositoryMethod.method] = repositoryMethod.createInfo(types)
    }
}

private class SpringQueryReferenceReturnTypes(val cp: JIRClasspath) {
    val iterable = cp.findClassOrNull("java.lang.Iterable")
    val optional = cp.findClassOrNull("java.util.Optional")
    val mono = cp.findClassOrNull(ReactorMono)
    val flux = cp.findClassOrNull(ReactorFlux)
}

sealed interface RepositoryMethod {
    val method: JIRMethod
    data class SpringDefault(override val method: JIRMethod) : RepositoryMethod
    data class ProjectDefined(override val method: JIRMethod) : RepositoryMethod
}

sealed interface SpringRepoQueryReturn {
    sealed interface Single : SpringRepoQueryReturn
    sealed interface Many : SpringRepoQueryReturn
    sealed interface Reactive : SpringRepoQueryReturn

    data object Unknown : SpringRepoQueryReturn
    data object Primitive: SpringRepoQueryReturn

    data object Entity : Single
    data object Optional : Single
    data object Mono : Single, Reactive

    data object Iterable : Many
    data object Flux : Many, Reactive
}

enum class SpringRepoQueryKind {
    SAVE, FIND, OTHER
}

data class RepositoryMethodInfo(
    val method: RepositoryMethod,
    val kind: SpringRepoQueryKind,
    val type: SpringRepoQueryReturn
)

private fun RepositoryMethod.createInfo(
    types: SpringQueryReferenceReturnTypes
): RepositoryMethodInfo {
    val queryKind = springRepoQueryKind(method.name)
    val queryRetType = springRepoQueryReturnType(method, types)
    return RepositoryMethodInfo(this, queryKind, queryRetType)
}

private fun springRepoQueryKind(methodName: String): SpringRepoQueryKind {
    if (methodName.startsWith("save")) return SpringRepoQueryKind.SAVE

    val getPrefixes = listOf("find", "read", "get")
    if (getPrefixes.any { methodName.startsWith(it) }) return SpringRepoQueryKind.FIND

    return SpringRepoQueryKind.OTHER
}

private fun springRepoQueryReturnType(
    method: JIRMethod,
    types: SpringQueryReferenceReturnTypes
): SpringRepoQueryReturn {
    val retTypeName = method.returnType
    if (retTypeName.isPrimitive) return SpringRepoQueryReturn.Primitive

    val retType = types.cp.findTypeOrNull(retTypeName.typeName)
        ?: return SpringRepoQueryReturn.Unknown

    val tryUnbox = retType.unboxIfNeeded()
    if (tryUnbox is JIRPrimitiveType) return SpringRepoQueryReturn.Primitive

    if (tryUnbox !is JIRRefType) return SpringRepoQueryReturn.Unknown

    val retClass = tryUnbox.jIRClass
    if (types.iterable != null && retClass.isSubClassOf(types.iterable)) return SpringRepoQueryReturn.Iterable
    if (types.optional != null && retClass.isSubClassOf(types.optional)) return SpringRepoQueryReturn.Optional
    if (types.mono != null && retClass.isSubClassOf(types.mono)) return SpringRepoQueryReturn.Mono
    if (types.flux != null && retClass.isSubClassOf(types.flux)) return SpringRepoQueryReturn.Flux

    return SpringRepoQueryReturn.Entity
}

private fun findRepositoryMethod(
    method: JIRMethod,
    projectClasses: ProjectClasses
): RepositoryMethod? {
    if (!method.isAbstract) return null

    if (method.enclosingClass.name.startsWith(SpringPackage)) {
        return RepositoryMethod.SpringDefault(method)
    }

    if (method.enclosingClass.declaration.location in projectClasses.projectLocations) {
        return RepositoryMethod.ProjectDefined(method)
    }

    return null
}
