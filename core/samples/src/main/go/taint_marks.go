package test

// ── Multiple taint mark tests ────────────────────────────────────────

func taintMarkMatch001T() {
	data := sourceA()
	sinkA(data)
}

func taintMarkMismatch001F() {
	data := sourceA()
	sinkB(data)
}

func taintMarkMatch002T() {
	data := sourceB()
	sinkB(data)
}
