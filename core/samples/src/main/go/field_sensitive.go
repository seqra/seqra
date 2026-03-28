package test

// ── Field sensitivity (struct fields) tests ──────────────────────────

// SFPair is a struct with tainted and clean fields
type SFPair struct {
	tainted string
	clean   string
}

// SFNested is a struct with a nested struct field
type SFNested struct {
	inner SFPair
	other string
}

// ── Direct field read/write ──────────────────────────────────────────

func structField001T() {
	data := source()
	p := SFPair{tainted: data, clean: "safe"}
	sink(p.tainted)
}

func structField002F() {
	data := source()
	p := SFPair{tainted: data, clean: "safe"}
	sink(p.clean)
}

func structFieldWrite001T() {
	data := source()
	var p SFPair
	p.tainted = data
	sink(p.tainted)
}

func structFieldWrite002F() {
	data := source()
	var p SFPair
	p.tainted = data
	sink(p.clean)
}

// ── Field sensitivity through function calls ─────────────────────────

func getPairTainted(p SFPair) string { return p.tainted }
func getPairClean(p SFPair) string   { return p.clean }

func structFieldInterproc001T() {
	data := source()
	p := SFPair{tainted: data, clean: "safe"}
	result := getPairTainted(p)
	sink(result)
}

func structFieldInterproc002F() {
	data := source()
	p := SFPair{tainted: data, clean: "safe"}
	result := getPairClean(p)
	sink(result)
}

// ── Nested struct field access ───────────────────────────────────────

func structNested001T() {
	data := source()
	inner := SFPair{tainted: data, clean: "safe"}
	n := SFNested{inner: inner, other: "safe"}
	sink(n.inner.tainted)
}

func structNested002F() {
	data := source()
	inner := SFPair{tainted: data, clean: "safe"}
	n := SFNested{inner: inner, other: "safe"}
	sink(n.other)
}
