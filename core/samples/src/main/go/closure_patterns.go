package test

// ── Closure capture and invocation pattern tests ─────────────────────

// ── Direct capture of tainted variable ───────────────────────────────

func closureCapture001T() {
	data := source()
	f := func() string { return data }
	sink(f())
}

func closureCapture002F() {
	data := source()
	data = "safe"
	f := func() string { return data }
	sink(f())
}

// ── Closure capturing two variables ──────────────────────────────────

func closureTwoVars001T() {
	tainted := source()
	clean := "safe"
	f := func() string { return tainted + clean }
	sink(f())
}

func closureTwoVars002F() {
	tainted := source()
	clean := "safe"
	consume(tainted)
	f := func() string { return clean }
	sink(f())
}

// ── Nested closures ──────────────────────────────────────────────────

func closureNested001T() {
	data := source()
	outer := func() func() string {
		return func() string { return data }
	}
	inner := outer()
	sink(inner())
}

func closureNested002F() {
	data := source()
	consume(data)
	safe := "clean"
	outer := func() func() string {
		return func() string { return safe }
	}
	inner := outer()
	sink(inner())
}

// ── Closure assigned to variable then called ─────────────────────────

func closureAssign001T() {
	data := source()
	var f func() string
	f = func() string { return data }
	result := f()
	sink(result)
}

func closureAssign002F() {
	_ = source()
	var f func() string
	f = func() string { return "constant" }
	result := f()
	sink(result)
}

// ── Closure capturing slice ──────────────────────────────────────────

func closureSlice001T() {
	data := source()
	items := []string{data, "b", "c"}
	f := func() string { return items[0] }
	sink(f())
}

func closureSlice002F() {
	_ = source()
	items := []string{"safe", "b", "c"}
	f := func() string { return items[0] }
	sink(f())
}
