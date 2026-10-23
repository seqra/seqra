package org.opentaint.dataflow.bidi.engine

import org.opentaint.dataflow.bidi.ap.ApMode
import org.opentaint.dataflow.bidi.ap.Direction
import org.opentaint.dataflow.bidi.ap.MethodKey
import org.opentaint.dataflow.bidi.ap.MarkMode
import org.opentaint.dataflow.bidi.driver.SeedIndex
import org.opentaint.dataflow.bidi.store.DemandStore
import org.opentaint.dataflow.bidi.store.RecordStore
import java.util.Collections

/** Immutable run inputs. The driver writes the backing records only at a completed-run barrier. */
class RunConfig(
    val index: Int,
    val fieldLimit: Int,
    val demand: DemandStore?,
    records: RecordStore,
    val seeds: SeedIndex,
    roots: List<MethodKey>,
    disableRun1Statics: Boolean = java.lang.Boolean.getBoolean("opentaint.bidi.disableRun1Statics"),
    val run1MarkMode: MarkMode = readRun1MarkMode(),
) {
    init {
        require(index >= 1) { "The run index must be positive" }
        require(fieldLimit == maxOf(1, index - 1)) {
            "The selected schedule requires limits 1, 1, 2, 3, ..."
        }
        require((index == 1) == (demand == null)) { "Only run 1 has no demand snapshot" }
    }

    /** DIAGNOSTIC ONLY: this unsound ablation can miss flows through static fields.
     * Exact global class markers are kept. The setting has no effect after run 1. */
    val disableRun1Statics: Boolean = index == 1 && disableRun1Statics

    val records: RecordStore = records.view()
    val roots: List<MethodKey> = Collections.unmodifiableList(ArrayList(roots))
    val direction: Direction get() = if (index % 2 == 1) Direction.FORWARD else Direction.BACKWARD
    val run1: Boolean get() = index == 1
    val restricted: Boolean get() = index > 1
    val mode: ApMode = ApMode(run1, direction, fieldLimit,
        markMode = if (run1) run1MarkMode else MarkMode.FULL)
    val seededSources: Boolean get() = restricted && direction == Direction.FORWARD
}

/** The driver captures this value once and passes it to every run configuration. */
internal fun readRun1MarkMode(value: String? = System.getProperty("opentaint.bidi.run1MarkMode")): MarkMode =
    when (value) {
        null, "FULL" -> MarkMode.FULL
        "GROUP" -> MarkMode.GROUP
        else -> error("Invalid opentaint.bidi.run1MarkMode=$value; use GROUP or FULL")
    }
