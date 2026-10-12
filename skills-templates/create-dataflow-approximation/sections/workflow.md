### 1. Understand the propagation

Find and read each dataflow method's real source: take methods not yet in `build.done`, or the specific `methods` handed for repair even when already built. Leave built methods outside that explicit subset and their approximation source unchanged. An app-internal method sits in the project's own sources, a library method's source comes from its dependency (the language reference has how to get it). Read it to see how data moves from the method's inputs (receiver, arguments) to its outputs (return value, arguments it writes into, state it stores), gathering the full context needed to understand the function's behavior.

### 2. Write the approximation

Reproduce that propagation in the language-specific code form in the batch's approximation project at `.opentaint/dataflow/<batch>` — scaffold it if it doesn't exist yet — following the language reference's artifact layout. Cover every assigned callable variant, repair an explicitly handed callable in the existing source, and add new ones there rather than rewriting the file. The engine is field-sensitive — taint is tracked per field — so route data field-to-field exactly as the source does rather than tainting the whole object. The test project's negative samples (if present) verify this by storing taint in one field and reading another, so an over-broad model makes them fire. The concrete constructs and patterns are in the language reference.

### 3. Test against the test project

Run the approximation test directly as a foreground, blocking command and wait for exit — never background it or use Monitor. Apply this batch's sources and iterate until the samples pass. Feedback loop: a failing sample might be caused by the model's target type/member or signature not matching what the analyzer sees, or by the body not routing taint from the real source to the modeled output — diagnose the mismatch, fix, and re-run, don't rationalize a non-result. When the cause isn't obvious, localize where taint dies with a fact-reachability trace before guessing further per `references/debugging.md`. On a pass, append the method only if it is not already present in `build.done` (per Tracking); a repaired method remains recorded there.

### 4. Escalate

When the sample won't converge after ~3 fixes — whether the trace shows a faithful model still can't propagate (taint dying at a plain instruction the engine should carry through, an engine limitation) or the cause stays unclear — don't add a new method to `build.done` or alter an existing repaired method's tracking entry. Report it with the brief cause you found (per Output), for the orchestrator to escalate. Don't retry further.
