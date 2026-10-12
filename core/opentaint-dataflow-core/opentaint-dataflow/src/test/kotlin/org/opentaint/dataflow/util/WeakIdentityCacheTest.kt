package org.opentaint.dataflow.util

import java.lang.ref.WeakReference
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.*

class WeakIdentityCacheTest {
    private data class EqualKey(val value: Int)

    @Suppress("UNCHECKED_CAST")
    private fun references(cache: WeakIdentityCache<*, *>): List<WeakReference<Any>> {
        val values = cache.javaClass.getDeclaredField("values").apply { isAccessible = true }.get(cache) as Map<*, *>
        return values.keys.map { it as WeakReference<Any> }
    }

    @Test fun `surviving keys compute once by identity without invoking raw equality or hash code`(): Unit {
        val cache = WeakIdentityCache<Any, Any>()
        val a = EqualKey(1)
        val b = EqualKey(1)
        var builds = 0
        val first = cache.getOrPut(a) { builds++; Any() }
        assertSame(first, cache.getOrPut(a) { error("rebuilt surviving key") })
        val second = cache.getOrPut(b) { builds++; Any() }
        assertNotSame(first, second)
        val deep = object {
            override fun hashCode(): Int = error("raw hash called")
            override fun equals(other: Any?): Boolean = error("raw equality called")
        }
        val value = cache.getOrPut(deep) { builds++; Any() }
        assertSame(value, cache.getOrPut(deep) { error("rebuilt deep key") })
        assertEquals(3, builds)
        assertEquals(3, cache.size)
    }

    @Test fun `explicit weak key clear and enqueue prune only that mapping and permit recomputation`(): Unit {
        val cache = WeakIdentityCache<Any, Any>()
        val a = Any()
        val b = Any()
        val first = cache.getOrPut(a) { Any() }
        val retained = cache.getOrPut(b) { Any() }
        val oldReference = references(cache).single { it.get() === a }
        oldReference.clear()
        assertTrue(oldReference.enqueue())
        assertEquals(1, cache.pruneAll())
        assertEquals(1, cache.size)
        assertSame(retained, cache.getOrPut(b) { error("pruned another key") })
        val rebuilt = cache.getOrPut(a) { Any() }
        assertNotSame(first, rebuilt)
        assertSame(rebuilt, cache.getOrPut(a) { error("rebuilt twice") })
        assertEquals(2, cache.size)
    }

    @Test fun `bounded lookup maintenance and full pruning remove queued entries without GC timing`(): Unit {
        val cache = WeakIdentityCache<Any, Int>()
        val keys = List(64) { Any() }
        keys.forEachIndexed { index, key -> cache.getOrPut(key) { index } }
        references(cache).forEach { it.clear(); assertTrue(it.enqueue()) }
        assertEquals(64, cache.size)
        assertEquals(64, cache.getOrPut(Any()) { 64 })
        assertEquals(33, cache.size)
        assertEquals(32, cache.pruneAll())
        assertEquals(1, cache.size)
        assertEquals(0, cache.pruneAll())
        assertFailsWith<IllegalArgumentException> { cache.prune(-1) }
    }

    @Test fun `queued old key cannot erase a new mapping for the same surviving instance`(): Unit {
        val cache = WeakIdentityCache<Any, Any>()
        val key = Any()
        cache.getOrPut(key) { Any() }
        val old = references(cache).single()
        old.clear()
        val replacement = cache.getOrPut(key) { Any() }
        assertEquals(2, cache.size)
        assertTrue(old.enqueue())
        assertEquals(1, cache.pruneAll())
        assertEquals(1, cache.size)
        assertSame(replacement, cache.getOrPut(key) { error("removed replacement") })
    }

    @Test fun `concurrent lookups validate a surviving object once`(): Unit {
        val cache = WeakIdentityCache<Any, Any>()
        val key = Any()
        val start = CountDownLatch(1)
        val builds = AtomicInteger()
        val pool = Executors.newFixedThreadPool(8)
        try {
            val tasks = List(16) {
                pool.submit<Any> {
                    check(start.await(10, TimeUnit.SECONDS))
                    cache.getOrPut(key) { builds.incrementAndGet(); Any() }
                }
            }
            start.countDown()
            val expected = tasks.first().get(10, TimeUnit.SECONDS)
            tasks.forEach { assertSame(expected, it.get(10, TimeUnit.SECONDS)) }
            assertEquals(1, builds.get())
            assertEquals(1, cache.size)
        } finally {
            start.countDown()
            pool.shutdownNow()
        }
    }

    @Test fun `a failed computation stores no result and permits a successful retry`(): Unit {
        val cache = WeakIdentityCache<Any, Any>()
        val key = Any()
        assertFailsWith<IllegalStateException> { cache.getOrPut(key) { error("validate") } }
        assertEquals(0, cache.size)
        val result = cache.getOrPut(key) { Any() }
        assertSame(result, cache.getOrPut(key) { error("revalidated") })
        assertEquals(1, cache.size)
    }
}
