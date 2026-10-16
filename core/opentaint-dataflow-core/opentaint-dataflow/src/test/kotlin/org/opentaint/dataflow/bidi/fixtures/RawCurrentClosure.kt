package org.opentaint.dataflow.bidi.fixtures

import org.opentaint.dataflow.bidi.engine.RunConfig

/** Selected finite closure with raw phase inputs, independent of production stores and selectors. */
internal class RawCurrentClosure(program: FiniteProgramFixtures, config: RunConfig, inputs: ProgramRunInputs) {
    private val closure = NaiveClosure(program, config, semantics = NaiveClosure.Semantics.SELECTED, rawInputs = inputs)
    fun run(): RunObservation = closure.run()
}
