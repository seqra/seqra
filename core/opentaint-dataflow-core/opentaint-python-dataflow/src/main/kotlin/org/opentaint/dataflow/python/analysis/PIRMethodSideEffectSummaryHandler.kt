package org.opentaint.dataflow.python.analysis

import org.opentaint.dataflow.ap.ifds.analysis.MethodSideEffectSummaryHandler

/**
 * No-op side effect handler for the minimal prototype.
 * All methods use default implementations from MethodSideEffectSummaryHandler.
 */
class PIRMethodSideEffectSummaryHandler : MethodSideEffectSummaryHandler
