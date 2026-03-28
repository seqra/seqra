package test

// ── String operation tests ───────────────────────────────────────────

// ── String indexing ──────────────────────────────────────────────────

func stringIndex001T() {
	data := source()
	b := data[0]
	result := string(b)
	sink(result)
}

func stringIndex002F() {
	_ = source()
	data := "safe"
	b := data[0]
	result := string(b)
	sink(result)
}

// ── String slicing ───────────────────────────────────────────────────

func stringSlice001T() {
	data := source()
	result := data[1:3]
	sink(result)
}

func stringSlice002F() {
	_ = source()
	data := "safe string"
	result := data[1:3]
	sink(result)
}

// ── String through multiple variables ────────────────────────────────

func stringMultiVar001T() {
	a := source()
	b := a
	c := b
	sink(c)
}

func stringMultiVar002F() {
	a := source()
	_ = a
	b := "safe"
	c := b
	sink(c)
}

// ── String concat in loop ────────────────────────────────────────────

func stringConcatLoop001T() {
	data := source()
	result := ""
	for i := 0; i < 3; i++ {
		result = result + data
	}
	sink(result)
}

func stringConcatLoop002F() {
	_ = source()
	result := ""
	for i := 0; i < 3; i++ {
		result = result + "safe"
	}
	sink(result)
}
