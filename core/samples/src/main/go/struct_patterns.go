package test

// ── Struct field access pattern tests ────────────────────────────────

type SPData struct {
	first  string
	second string
	third  string
}

// ── Struct literal with tainted field ────────────────────────────────

func structLiteral001T() {
	data := source()
	s := SPData{first: data, second: "clean", third: "clean"}
	sink(s.first)
}

func structLiteral002F() {
	data := source()
	s := SPData{first: data, second: "clean", third: "clean"}
	sink(s.second)
}

// ── Multi-field struct, selective access ──────────────────────────────

func structMultiField001T() {
	data := source()
	s := SPData{first: data, second: "b", third: "c"}
	sink(s.first)
}

func structMultiField002F() {
	data := source()
	s := SPData{first: data, second: "b", third: "c"}
	sink(s.second)
}

// ── Function returning struct ────────────────────────────────────────

func makeSPData(val string) SPData {
	return SPData{first: val, second: "safe", third: "safe"}
}

func structFuncReturn001T() {
	data := source()
	s := makeSPData(data)
	sink(s.first)
}

func structFuncReturn002F() {
	data := source()
	s := makeSPData(data)
	sink(s.second)
}

// ── Pointer to struct, dereference and read ──────────────────────────

func structPtrDeref001T() {
	data := source()
	s := &SPData{first: data, second: "clean", third: "clean"}
	sink(s.first)
}

func structPtrDeref002F() {
	data := source()
	s := &SPData{first: data, second: "clean", third: "clean"}
	sink(s.second)
}

// ── Struct field reassignment ────────────────────────────────────────

func structReassign001T() {
	data := source()
	s := SPData{first: "clean", second: "clean", third: "clean"}
	s.first = data
	sink(s.first)
}

func structReassign002F() {
	data := source()
	s := SPData{first: "clean", second: "clean", third: "clean"}
	s.first = data
	sink(s.second)
}
