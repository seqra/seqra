package org.opentaint.ir.util.collections

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class SparseBitSetTest {

    private lateinit var sparseBitSet: SparseBitSet

    @BeforeEach
    fun setUp() {
        sparseBitSet = SparseBitSet()
    }

    /**
     * - Given an empty SparseBitSet instance
     * - When a bit is set to a specific value
     * - Then the SparseBitSet should indicate that the bit is contained within it
     * - And the SparseBitSet should not be empty
     */
    @Test
    fun `verify that a bit can be set in the SparseBitSet`() {
        val bitToSet = 5L
        val wasSet = sparseBitSet.set(bitToSet)
        assertTrue(wasSet)
        assertTrue(sparseBitSet.contains(bitToSet))
        assertFalse(sparseBitSet.isEmpty)
    }

    /**
     * - Given a SparseBitSet with a bit set to a specific value
     * - When the same bit is set again
     * - Then the SparseBitSet should indicate that the bit is still contained within it
     * - And the SparseBitSet should remain unchanged
     */
    @Test
    fun `verify that setting the same bit twice does not mutate the SparseBitSet`() {
        val bitToSet = 5L
        sparseBitSet.set(bitToSet)
        val wasSetAgain = sparseBitSet.set(bitToSet)
        assertFalse(wasSetAgain)
        assertTrue(sparseBitSet.contains(bitToSet))
    }

    /**
     * - Given a SparseBitSet with a bit set to a specific value
     * - When the bit is cleared
     * - Then the SparseBitSet should indicate that the bit is no longer contained within it
     * - And the SparseBitSet should reflect that it is not empty if other bits are present
     */
    @Test
    fun `verify that a bit can be cleared from the SparseBitSet`() {
        val bitToSet = 5L
        sparseBitSet.set(bitToSet)
        val wasCleared = sparseBitSet.clear(bitToSet)
        assertTrue(wasCleared)
        assertFalse(sparseBitSet.contains(bitToSet))
    }

    /**
     * - Given a SparseBitSet with a specific bit set
     * - When a different bit is cleared
     * - Then the SparseBitSet should indicate that the original bit is still contained within it
     * - And the SparseBitSet should remain unchanged
     */
    @Test
    fun `verify that clearing a bit that is not set does not mutate the SparseBitSet`() {
        val bitToSet = 5L
        sparseBitSet.set(bitToSet)
        val wasCleared = sparseBitSet.clear(10L)
        assertFalse(wasCleared)
        assertTrue(sparseBitSet.contains(bitToSet))
    }

    /**
     * - Given a SparseBitSet with multiple bits set
     * - When a test is performed on a specific bit that is set
     * - Then the result should indicate that the bit is contained within the SparseBitSet
     * - And when a test is performed on a bit that is not set
     * - Then the result should indicate that the bit is not contained within the SparseBitSet
     */
    @Test
    fun `verify that testing a bit returns the correct result`() {
        sparseBitSet.set(5L)
        assertTrue(sparseBitSet.test(5L))
        assertFalse(sparseBitSet.test(10L))
    }

    @Test
    fun `verify iterator works correctly`() {
        for (l in 5L..555L step 10) {
            sparseBitSet.set(l)
        }
        val iterator = sparseBitSet.iterator()
        for (l in 5L..555L step 10) {
            assertTrue(iterator.hasNext())
            assertEquals(l, iterator.next())
        }
    }
}