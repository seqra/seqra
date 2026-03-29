package test

// ── Sanitization pattern tests ───────────────────────────────────────

// ── Conditional sanitization ─────────────────────────────────────────

func sanitizeConditional001T() {
	data := source()
	if len(data) > 5 {
		data = sanitize(data)
	}
	// only one branch sanitizes → conservative: still tainted
	sink(data)
}

func sanitizeConditional002F() {
	data := source()
	if len(data) > 5 {
		data = "safe1"
	} else {
		data = "safe2"
	}
	sink(data)
}

// ── Return from function (identity vs constant) ──────────────────────

func sanitizeReturn001T() {
	data := source()
	result := identity(data)
	sink(result)
}

func sanitizeReturn002F() {
	data := source()
	result := dropValue(data)
	sink(result)
}

// ── Chain of functions ───────────────────────────────────────────────

func sanitizeChain001T() {
	data := source()
	step1 := identity(data)
	step2 := passthrough(step1)
	sink(step2)
}

func sanitizeChain002F() {
	data := source()
	step1 := identity(data)
	step2 := dropValue(step1)
	sink(step2)
}

// ── Reassignment of tainted variable ─────────────────────────────────

func sanitizeReassign001T() {
	data := source()
	original := data
	data = "safe"
	// original still holds taint
	sink(original)
}

func sanitizeReassign002F() {
	data := source()
	data = "safe"
	sink(data)
}
