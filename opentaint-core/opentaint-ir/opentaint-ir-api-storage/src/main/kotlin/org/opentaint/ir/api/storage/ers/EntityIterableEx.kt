package org.opentaint.ir.api.storage.ers

/**
 * Default lazy implementations of binary operations over instances of `EntityIterable`
 */
fun EntityIterable.union(other: EntityIterable): EntityIterable {
    val self = this
    return object : EntityIterable {
        override fun iterator(): Iterator<Entity> {
            return object : Iterator<Entity> {

                val iterated = HashSet<Entity>()
                var iter = self.iterator()
                var switchedToOther = false
                var next: Entity? = null

                override fun hasNext(): Boolean {
                    advance()
                    return next != null
                }

                override fun next(): Entity {
                    advance()
                    return (next ?: error("No more entities available")).also { next = null }
                }

                private fun advance() {
                    if (next == null) {
                        if (!switchedToOther) {
                            if (iter.hasNext()) {
                                next = iter.next().also {
                                    iterated.add(it)
                                }
                                return
                            }
                            iter = other.iterator()
                            switchedToOther = true
                        }
                        while (iter.hasNext()) {
                            iter.next().let {
                                if (it !in iterated) {
                                    next = it
                                    return
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

fun EntityIterable.intersect(other: EntityIterable): EntityIterable {
    val self = this
    return object : EntityIterable {
        override fun iterator(): Iterator<Entity> {
            val otherSet = other.toEntityIdSet()
            return self.filterEntityIds { id -> id in otherSet }.iterator()
        }
    }
}

fun EntityIterable.subtract(other: EntityIterable): EntityIterable {
    val self = this
    return object : EntityIterable {
        override fun iterator(): Iterator<Entity> {
            val otherSet = other.toEntityIdSet()
            return self.filterEntityIds { id -> id !in otherSet }.iterator()
        }
    }
}

fun EntityIterable.toEntityIdSet(): Set<EntityId> {
    val it = iterator()
    if (!it.hasNext()) {
        return emptySet()
    }
    val element = it.next()
    if (!it.hasNext()) {
        return setOf(element.id)
    }
    val result = LinkedHashSet<EntityId>()
    result.add(element.id)
    while (it.hasNext()) {
        result.add(it.next().id)
    }
    return result
}