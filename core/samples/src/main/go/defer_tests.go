package test

// ── Defer execution order tests ──────────────────────────────────────
// Defer args are evaluated immediately, but the call runs after surrounding code.

// ── Basic defer ──────────────────────────────────────────────────────

func defer001T() {
	data := source()
	defer consume(data) // deferred, but data is tainted at eval time
	sink(data)          // sink runs before defer body
}

func defer002F() {
	data := "safe"
	defer consume(data)
	sink(data) // data is safe here
}

// ── Defer with sink ──────────────────────────────────────────────────

func deferSink001T() {
	data := source()
	defer sink(data) // args evaluated immediately: data is tainted
}

func deferSink002F() {
	data := "safe"
	defer sink(data) // args evaluated immediately: data is safe
	data = source()  // this happens after defer eval, before defer runs
	consume(data)
}

// ── Defer in loop ────────────────────────────────────────────────────

func deferLoop001T() {
	data := source()
	for i := 0; i < 1; i++ {
		defer sink(data)
	}
}

// ── Defer with closure ───────────────────────────────────────────────

func deferClosure001T() {
	data := source()
	defer func() {
		sink(data) // closure captures data; data is tainted at call time
	}()
}

func deferClosure002F() {
	data := source()
	data = "safe"
	defer func() {
		sink(data) // data was overwritten before defer was set up
	}()
}

// ── Multiple defers (LIFO order) ─────────────────────────────────────

func deferMultiple001T() {
	data := source()
	defer consume(data)
	defer sink(data) // this runs before the consume defer
}
