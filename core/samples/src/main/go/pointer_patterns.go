package test

// ── Pointer aliasing and indirection pattern tests ───────────────────

type PPData struct {
	value string
	other string
}

// ── Pointer aliasing ─────────────────────────────────────────────────

func ptrAlias001T() {
	data := source()
	p1 := &data
	p2 := p1
	sink(*p2)
}

func ptrAlias002F() {
	data := source()
	_ = &data
	safe := "clean"
	p2 := &safe
	sink(*p2)
}

// ── Pointer to struct field ──────────────────────────────────────────

func ptrField001T() {
	data := source()
	obj := &PPData{value: "clean", other: "clean"}
	obj.value = data
	sink(obj.value)
}

func ptrField002F() {
	data := source()
	obj := &PPData{value: "clean", other: "clean"}
	obj.value = data
	sink(obj.other)
}

// ── Function writing through pointer parameter ──────────────────────

func writeThroughPtr(obj *PPData, val string) {
	obj.value = val
}

func readPPValue(obj *PPData) string {
	return obj.value
}

func readPPOther(obj *PPData) string {
	return obj.other
}

func ptrFunc001T() {
	data := source()
	obj := &PPData{value: "clean", other: "clean"}
	writeThroughPtr(obj, data)
	result := readPPValue(obj)
	sink(result)
}

func ptrFunc002F() {
	data := source()
	obj := &PPData{value: "clean", other: "clean"}
	writeThroughPtr(obj, data)
	result := readPPOther(obj)
	sink(result)
}

// ── Pointer dereference ──────────────────────────────────────────────

func ptrDeref001T() {
	data := source()
	p := &data
	result := *p
	sink(result)
}

func ptrDeref002F() {
	_ = source()
	safe := "clean"
	p := &safe
	result := *p
	sink(result)
}
