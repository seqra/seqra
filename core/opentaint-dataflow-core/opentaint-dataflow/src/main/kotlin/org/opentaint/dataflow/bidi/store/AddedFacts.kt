package org.opentaint.dataflow.bidi.store

import org.opentaint.dataflow.bidi.ap.Layer
import org.opentaint.dataflow.bidi.ap.MethodKey
import org.opentaint.dataflow.bidi.ap.PremiseKey
import org.opentaint.ir.api.common.cfg.CommonInst

/** The exact caller side of a link. A08 adds the run-local link store. */
data class CallerRef(val caller: MethodKey, val premise: PremiseKey, val callerLayer: Layer, val call: CommonInst)
