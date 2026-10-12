package org.opentaint.project

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ArtifactVersionsTest {

    private fun assertGreater(left: String, right: String) {
        assertTrue(compareArtifactVersions(left, right) > 0, "expected $left > $right")
        assertTrue(compareArtifactVersions(right, left) < 0, "expected $right < $left")
    }

    @Test
    fun `numeric segments compare numerically, not lexicographically`() {
        assertGreater("1.10.0", "1.9.0")
        assertGreater("4.2.0", "4.0.10")
        assertEquals(0, compareArtifactVersions("1.2.3", "1.2.3"))
    }

    @Test
    fun `a longer numeric version is greater`() {
        assertGreater("1.2.1", "1.2")
    }

    @Test
    fun `a qualifier makes a version pre-release`() {
        assertGreater("4.2.0", "4.2.0-rc3")
        assertGreater("1.5.21", "1.5.21-SNAPSHOT")
        assertGreater("4.2.0-rc3", "4.0.10")
    }

    @Test
    fun `qualifiers compare to each other lexicographically`() {
        assertGreater("1.0-rc2", "1.0-beta1")
    }

    @Test
    fun `one version per artifact is kept, the highest`() {
        val deps = listOf(
            Triple("com.google.guava", "guava", "33.0-jre"),
            Triple("ch.qos.logback", "logback-classic", "1.5.18"),
            Triple("com.google.guava", "guava", "33.2-jre"),
            Triple("ch.qos.logback", "logback-classic", "1.5.21"),
            Triple("com.google.guava", "guava", "33.1-jre"),
        )
        val dropped = mutableListOf<Triple<String, String, String>>()

        val kept = deps.singleVersionPerArtifact(
            artifact = { it.first to it.second },
            version = { it.third },
            onDropped = { _, d -> dropped += d }
        )

        assertEquals(
            listOf(
                Triple("com.google.guava", "guava", "33.2-jre"),
                Triple("ch.qos.logback", "logback-classic", "1.5.21"),
            ),
            kept,
            "must keep the highest version of each artifact, in first-seen order"
        )
        assertEquals(3, dropped.size)
    }

    @Test
    fun `versions of different major lines coexist`() {
        // conductor builds os-persistence-v2 against opensearch-rest-client 2.x and os-persistence-v3
        // against 3.x — different majors of one artifact, kept apart by the build itself (it shades
        // them). Their APIs are incompatible: RestClientBuilder's callbacks take Apache HttpClient 4
        // types in 2.x and HttpClient 5 types in 3.x. Collapsing the two loses the API a module
        // actually compiles against.
        val deps = listOf(
            Triple("org.opensearch.client", "opensearch-rest-client", "2.18.0"),
            Triple("org.opensearch.client", "opensearch-rest-client", "3.5.0"),
        )

        assertEquals(
            deps,
            deps.singleVersionPerArtifact({ it.first to it.second }, { it.third }),
            "different majors are incompatible artifacts, not versions of one"
        )
    }

    @Test
    fun `the highest version of each major line is kept`() {
        val deps = listOf(
            Triple("com.fasterxml.jackson.core", "jackson-core", "2.14.2"),
            Triple("com.fasterxml.jackson.core", "jackson-core", "2.18.0"),
            Triple("com.fasterxml.jackson.core", "jackson-core", "1.9.13"),
            Triple("com.fasterxml.jackson.core", "jackson-core", "2.15.3"),
        )

        assertEquals(
            listOf(
                Triple("com.fasterxml.jackson.core", "jackson-core", "2.18.0"),
                Triple("com.fasterxml.jackson.core", "jackson-core", "1.9.13"),
            ),
            deps.singleVersionPerArtifact({ it.first to it.second }, { it.third }),
            "drift inside a major line collapses; the 1.x line survives on its own"
        )
    }

    @Test
    fun `a non-numeric version is never collapsed into another`() {
        val deps = listOf(
            Triple("com.example", "lib", "RELEASE"),
            Triple("com.example", "lib", "MILESTONE"),
        )

        assertEquals(
            deps,
            deps.singleVersionPerArtifact({ it.first to it.second }, { it.third }),
            "without a numeric major there is no compatibility claim to make"
        )
    }

    @Test
    fun `an unresolvable highest version falls back to the highest version that resolves`() {
        val deps = listOf(
            Triple("com.fasterxml.jackson.core", "jackson-core", "2.14.2"),
            Triple("com.fasterxml.jackson.core", "jackson-core", "2.17.3"),
            Triple("com.fasterxml.jackson.core", "jackson-core", "2.15.3"),
        )
        // 2.17.3 is in the dependency graph but was never downloaded: metadata only, no jar
        val jars = mapOf("2.14.2" to "jackson-core-2.14.2.jar", "2.15.3" to "jackson-core-2.15.3.jar")

        val kept = deps.singleResolvedVersionPerArtifact(
            artifact = { it.first to it.second },
            version = { it.third },
            resolve = { jars[it.third] },
        )

        assertEquals(
            listOf("jackson-core-2.15.3.jar"),
            kept,
            "an artifact whose highest version has no jar must still reach the model"
        )
    }

    @Test
    fun `an artifact that resolves at no version is dropped`() {
        val deps = listOf(Triple("com.example", "ghost", "1.0"), Triple("com.example", "ghost", "2.0"))

        val kept = deps.singleResolvedVersionPerArtifact(
            artifact = { it.first to it.second },
            version = { it.third },
            resolve = { null },
        )

        assertEquals(emptyList(), kept)
    }

    @Test
    fun `distinct artifacts of the same group are kept apart`() {
        val deps = listOf(
            Triple("org.springframework", "spring-core", "6.1.0"),
            Triple("org.springframework", "spring-web", "6.1.0"),
        )
        assertEquals(deps, deps.singleVersionPerArtifact({ it.first to it.second }, { it.third }))
    }
}
