package test

// ── Pass-through rule tests ──────────────────────────────────────────

func passThrough001T() {
	data := source()
	result := passthrough(data)
	sink(result)
}

func passThrough002F() {
	data := source()
	result := sanitize(data)
	sink(result)
}

func passThrough003T() {
	data := source()
	result := transform(data, "other")
	sink(result)
}

func passThrough004F() {
	result := transform("clean", source())
	sink(result)
}
