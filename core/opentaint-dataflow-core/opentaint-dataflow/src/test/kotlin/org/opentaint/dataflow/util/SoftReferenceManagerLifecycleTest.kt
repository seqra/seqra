package org.opentaint.dataflow.util

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class SoftReferenceManagerLifecycleTest {
    @Test
    fun `cleanup counts live referents and disables registration until enable`(): Unit {
        val manager = SoftReferenceManager(RefManager())
        val value = Any()
        val live = assertNotNull(manager.createRef(value))
        val cleared = assertNotNull(manager.createRef(Any())).also { it.clear() }
        val empty = assertNotNull(manager.createRef<Any?>(null))

        assertEquals(1, manager.cleanup())
        assertNull(live.get())
        assertNull(cleared.get())
        assertNull(empty.get())
        assertNull(manager.createRef(value))
        assertEquals(-1, manager.cleanup())

        manager.enable()
        val next = assertNotNull(manager.createRef(value))
        assertSame(value, next.get())
        assertNull(live.get())
        assertEquals(1, manager.cleanup())
        assertNull(next.get())
    }

    @Test
    fun `distinct registrations of one referent are cleared separately`(): Unit {
        val manager = SoftReferenceManager(RefManager())
        val value = Any()
        val first = assertNotNull(manager.createRef(value))
        val second = assertNotNull(manager.createRef(value))

        assertTrue(first !== second)
        assertEquals(2, manager.cleanup())
        assertNull(first.get())
        assertNull(second.get())
    }
}
