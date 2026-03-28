package org.opentaint.dataflow.go.analysis

import org.opentaint.dataflow.ap.ifds.analysis.MethodSideEffectSummaryHandler

/**
 * No-op side-effect handler for Go MVP.
 * All methods use default implementations that return emptySet().
 */
class GoMethodSideEffectHandler : MethodSideEffectSummaryHandler
