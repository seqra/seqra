package org.opentaint.jvm.util.types

import org.opentaint.ir.api.jvm.JcClassOrInterface
import org.opentaint.ir.api.jvm.JcClasspath
import org.opentaint.ir.api.jvm.ext.findClass
import java.util.PriorityQueue

class ScorerExtension<Result : Comparable<Result>>(
    val cp: JcClasspath,
    val key: Any,
) {
    private val scorer by lazy {
        val matchingFeatures = mutableListOf<ClassScorer<Result>>()
        cp.db.features.forEach { feature ->
            if (feature is ClassScorer<*> && feature.key == key) {

                @Suppress("UNCHECKED_CAST")
                matchingFeatures.add(feature as ClassScorer<Result>)
            }
        }

        matchingFeatures.first()
    }

    fun getScore(jcClass: JcClassOrInterface): Result? =
        scorer.getScore(jcClass.declaration.location, jcClass.name)

    fun allClassesSorted(): Sequence<JcClassOrInterface> {
        data class Node<Result : Comparable<Result>>(
            val result: Result,
            val className: String,
            val other: Iterator<Pair<Result, String>>,
        ) : Comparable<Node<Result>> {
            override fun compareTo(other: Node<Result>): Int = -result.compareTo(other.result)
        }

        return sequence {
            val queue = PriorityQueue<Node<Result>>()

            fun advance(iterator: Iterator<Pair<Result, String>>) {
                if (!iterator.hasNext()) {
                    return
                }
                val (result, className) = iterator.next()
                queue.add(Node(result, className, iterator))
            }

            for (location in cp.registeredLocations) {
                val iterator = scorer.sortedClasses(location).iterator()
                advance(iterator)
            }

            while (queue.isNotEmpty()) {
                val top = queue.poll()
                val (_, className, iterator) = top
                yield(cp.findClass(className))
                advance(iterator)
            }
        }
    }
}
