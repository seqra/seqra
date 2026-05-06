package org.opentaint.ir.test.python.tier3

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test

/**
 * Round-trip tests targeting the callable-shim synthetic adapter class.
 *
 * @Disabled: the angle-bracket class name `<closure_…>` is not a valid Python
 * identifier, and the source reconstructor cannot produce parseable Python
 * for synthetic classes whose qualified names embed those characters. Until
 * `PIRReconstructor` grows full support for the new closure shape (see
 * `.agents/closure-lowering/summary.md` §1: "PIRReconstructor cell shape"),
 * leave this test disabled rather than relax the assertion.
 *
 * Note: [RoundTripLocalFunctionTest] still passes by going through the
 * sanitised function-only path (`reconstructWithLambdas` rewrites
 * `<closure_X>` to `__closure_X__` and emits the adapter class as Python
 * code). This test would target the *raw* synthetic class shape — not the
 * sanitised reconstruction — and is the placeholder for the follow-up.
 */
@Tag("tier3")
class RoundTripCallableShimTest {

    @Disabled("PIRReconstructor follow-up — synthesised <closure_*> class names are not valid Python identifiers")
    @Test
    fun `synthetic adapter class round-trips with raw angle-bracket name`() {
        // TODO: implement when PIRReconstructor learns the new closure shape.
        // The plan-of-record:
        //   1. Reconstructor recognises `<closure_X>` in module.classes.
        //   2. Adapter class emits as Python with sanitised name.
        //   3. PIRCall to the adapter constructor reconstructs as instantiation.
        //   4. Verify executing original Python and reconstructed Python yields
        //      identical results for a fixture exercising captures, *args,
        //      **kwargs, default values, and keyword-only arguments.
    }
}
