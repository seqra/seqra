package test

// ── Sanitization and taint killing tests ─────────────────────────────

// ── Basic overwrite sanitization ─────────────────────────────────────

func sanitize001F() {
	data := source()
	data = "safe"
	sink(data)
}

func sanitize002T() {
	data := source()
	other := data
	data = "safe" // kills data but not other
	sink(other)
}

// ── Sanitization through function call ───────────────────────────────

func sanitize003F() {
	data := source()
	result := dropValue(data)
	sink(result)
}

func sanitize004T() {
	data := source()
	_ = dropValue(data) // doesn't affect original
	sink(data)
}

// ── Conditional sanitization ─────────────────────────────────────────

func sanitizeCond001T() {
	data := source()
	if len(data) > 100 {
		data = "safe" // only sanitizes in one branch
	}
	sink(data) // taint may still reach here (conservative)
}

func sanitizeCond002F() {
	data := source()
	if true {
		data = "safe"
	} else {
		data = "also safe"
	}
	sink(data)
}

// ── Sanitization in loop ─────────────────────────────────────────────

func sanitizeLoop001T() {
	data := source()
	for i := 0; i < 3; i++ {
		if i == 2 {
			data = "safe"
		}
	}
	sink(data) // conservative: taint may not have been killed
}

func sanitizeLoop002F() {
	data := source()
	data = "safe"
	for i := 0; i < 3; i++ {
		// data stays safe
	}
	sink(data)
}

// ── Multiple taint sources, partial sanitization ─────────────────────

func sanitizePartial001T() {
	a := source()
	b := source()
	a = "safe" // kills a
	sink(b)    // b still tainted
	consume(a)
}

func sanitizePartial002F() {
	a := source()
	b := source()
	a = "safe"
	b = "safe"  // both killed
	sink(a + b) // both safe now
}

// ── Struct field overwrite ───────────────────────────────────────────

func sanitizeField001T() {
	data := source()
	p := SFPair{tainted: data, clean: "safe"}
	p.clean = "new safe" // doesn't affect tainted field
	sink(p.tainted)
}

func sanitizeField002F() {
	data := source()
	p := SFPair{tainted: data, clean: "safe"}
	p.tainted = "safe" // overwrites tainted field
	sink(p.tainted)
}
