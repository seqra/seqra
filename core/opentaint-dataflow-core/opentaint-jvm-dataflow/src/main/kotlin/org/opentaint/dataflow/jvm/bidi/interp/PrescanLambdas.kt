package org.opentaint.dataflow.jvm.bidi.interp

import it.unimi.dsi.fastutil.ints.Int2ObjectMap
import org.opentaint.ir.api.jvm.JIRMethod

/** Frozen lambda implementations by caller and call-site instruction index. */
typealias PrescanLambdas = Map<JIRMethod, Int2ObjectMap<Set<JIRMethod>>>
