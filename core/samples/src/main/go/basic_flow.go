package test

// ── Basic intraprocedural flow tests ────────────────────────────────

func stringDirect() {
	sink(source())
}

func killByOverwrite001F() {
	data := source()
	data = "safe"
	sink(data)
}

func killByReassign001F() {
	data := source()
	other := data
	other = "safe"
	sink(other)
}

func noKill001T() {
	data := source()
	other := data
	data = "safe"
	sink(other)
}
