package org.opentaint.ir.impl.python

import org.opentaint.ir.api.python.PIRFunction
import org.opentaint.ir.api.python.PIRLocation

/**
 * [method] is wired post-construction by [org.opentaint.ir.impl.python.flatToPir.FlatToPirConverter]
 * once the owning function exists. Until then the location carries only its
 * within-function identity ([index]); reading [method] before wiring throws.
 *
 * This is the one remaining `lateinit` in the IR build path. Every
 * [org.opentaint.ir.api.python.PIRInstruction] receives its location at
 * construction time, so `inst.location` is `val`; the chicken-and-egg with
 * the owning function is isolated to this single field.
 */
class PIRLocationImpl(
    override val index: Int,
) : PIRLocation {
    override lateinit var method: PIRFunction

    override fun toString(): String = "PIRLocation(index=$index)"
}
