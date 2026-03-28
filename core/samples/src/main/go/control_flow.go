package test

// ── Control flow tests ──────────────────────────────────────────────

func conditionalIf001T() {
	data := source()
	var result string
	if len(data) > 0 {
		result = data
	} else {
		result = "safe"
	}
	sink(result)
}

func conditionalIf002F() {
	data := source()
	var result string
	if len(data) > 0 {
		result = "safe"
	} else {
		result = "safe"
	}
	sink(result)
	consume(data)
}

func forBody001T() {
	data := source()
	var result string
	for i := 0; i < 1; i++ {
		result = data
	}
	sink(result)
}

func forBody002F() {
	data := source()
	var result string
	for i := 0; i < 1; i++ {
		result = "safe"
	}
	sink(result)
	consume(data)
}
