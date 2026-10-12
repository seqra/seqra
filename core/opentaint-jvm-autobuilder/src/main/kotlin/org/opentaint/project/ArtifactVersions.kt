package org.opentaint.project

/**
 * Version comparison in the spirit of Maven/Gradle conflict resolution: segments are compared one by
 * one, numerically where both sides are numeric, and a trailing qualifier (`-rc3`, `-SNAPSHOT`) makes
 * a version *lower* than the same version without it.
 */
internal fun compareArtifactVersions(left: String, right: String): Int {
    val leftParts = left.split(*VERSION_SEPARATORS)
    val rightParts = right.split(*VERSION_SEPARATORS)

    for (i in 0 until maxOf(leftParts.size, rightParts.size)) {
        val l = leftParts.getOrNull(i)
        val r = rightParts.getOrNull(i)

        // one version ran out of segments: a numeric tail means a later release (1.2.1 > 1.2),
        // a qualifier tail means a pre-release of the shorter one (1.2 > 1.2-rc1)
        if (l == null) return if (r!!.isNumeric()) -1 else 1
        if (r == null) return if (l.isNumeric()) 1 else -1

        val cmp = when {
            l.isNumeric() && r.isNumeric() -> l.toBigInteger().compareTo(r.toBigInteger())
            l.isNumeric() -> 1
            r.isNumeric() -> -1
            else -> l.compareTo(r)
        }
        if (cmp != 0) return cmp
    }

    return 0
}

/**
 * The major-version line a version belongs to, or null when it does not start with a number.
 *
 * Versions of one line are the same artifact evolving; versions of different lines are different
 * artifacts that happen to share a name, and a multi-module build routinely uses several at once.
 */
private fun String.majorVersionLine(): String? =
    takeWhile { it !in VERSION_SEPARATORS }.takeIf { it.isNumeric() }

/**
 * Keeps a single version of every artifact per major-version line — the highest — the way a build
 * tool resolves a conflict. Without this the model carries every version any module resolved, which
 * both inflates it and leaves the choice between same-named classes to classpath lookup order.
 *
 * Collapsing stops at the major-version boundary. Drift inside a line is the same API at different
 * patch levels, so the highest stands in for all of them; across lines the APIs are incompatible by
 * construction, and a build that resolves two majors of one artifact genuinely needs both (conductor
 * compiles one module against `opensearch-rest-client` 2.x and another against 3.x, whose callbacks
 * take Apache HttpClient 4 and 5 types respectively). Keeping only the highest would leave every
 * module on the older line compiled against classes the model no longer has. A version with no
 * numeric major makes no compatibility claim at all and is never collapsed into another.
 *
 * Order is preserved: an artifact keeps the position of its first occurrence.
 */
internal fun <T> List<T>.singleVersionPerArtifact(
    artifact: (T) -> Pair<String, String>,
    version: (T) -> String,
    onDropped: (kept: T, dropped: T) -> Unit = { _, _ -> },
): List<T> {
    val best = LinkedHashMap<Triple<String, String, String>, T>()

    for (dependency in this) {
        val (groupId, artifactId) = artifact(dependency)
        val dependencyVersion = version(dependency)
        // no numeric major: key on the whole version so the entry stands on its own
        val line = dependencyVersion.majorVersionLine() ?: dependencyVersion
        val key = Triple(groupId, artifactId, line)
        val current = best[key]

        if (current == null) {
            best[key] = dependency
            continue
        }

        if (compareArtifactVersions(version(dependency), version(current)) > 0) {
            best[key] = dependency
            onDropped(dependency, current)
        } else {
            onDropped(current, dependency)
        }
    }

    return best.values.toList()
}

/**
 * Keeps a single *usable* version of every artifact: the highest one that actually resolves to a file.
 *
 * A version present in the dependency graph is no guarantee of a downloaded artifact — the graph is
 * resolved from metadata alone, so a version no configuration ever compiled against leaves only a POM
 * in the local cache. Picking the highest version before checking that it resolves therefore drops the
 * artifact from the model entirely, taking its classes with it.
 */
internal fun <T, R : Any> List<T>.singleResolvedVersionPerArtifact(
    artifact: (T) -> Pair<String, String>,
    version: (T) -> String,
    resolve: (T) -> R?,
    onDropped: (kept: T, dropped: T) -> Unit = { _, _ -> },
): List<R> = mapNotNull { dependency -> resolve(dependency)?.let { dependency to it } }
    .singleVersionPerArtifact(
        artifact = { artifact(it.first) },
        version = { version(it.first) },
        onDropped = { kept, dropped -> onDropped(kept.first, dropped.first) },
    )
    .map { it.second }

private val VERSION_SEPARATORS = charArrayOf('.', '-', '_', '+')

private fun String.isNumeric(): Boolean = isNotEmpty() && all { it.isDigit() }
