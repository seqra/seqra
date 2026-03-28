package test

// ── Pointer and heap escape tests ────────────────────────────────────

// ── Basic pointer operations ─────────────────────────────────────────

func pointer001T() {
	data := source()
	p := &data
	sink(*p)
}

func pointer002F() {
	data := source()
	_ = data
	safe := "safe"
	p := &safe
	sink(*p)
}

// ── Heap-allocated struct via new ────────────────────────────────────

type HeapObj struct {
	value string
}

func heapNew001T() {
	data := source()
	obj := new(HeapObj)
	obj.value = data
	sink(obj.value)
}

func heapNew002F() {
	_ = source()
	obj := new(HeapObj)
	obj.value = "safe"
	sink(obj.value)
}

// ── Heap escape: struct returned from function ───────────────────────

func makeHeapObj(val1 string) *HeapObj {
	return &HeapObj{value: val1}
}

func heapEscape001T() {
	data := source()
	obj := makeHeapObj(data)
	sink(obj.value)
}

func heapEscape002F() {
	_ = source()
	obj := makeHeapObj("safe")
	sink(obj.value)
}

// ── Pointer passed to function ───────────────────────────────────────

func setPtrValue(obj *HeapObj, val1 string) {
	obj.value = val1
}

func ptrArg001T() {
	data := source()
	obj := &HeapObj{}
	setPtrValue(obj, data)
	sink(obj.value)
}

func ptrArg002F() {
	_ = source()
	obj := &HeapObj{}
	setPtrValue(obj, "safe")
	sink(obj.value)
}

// ── Pointer indirection (double pointer) ─────────────────────────────

func ptrToPtr001T() {
	data := source()
	p := &data
	pp := &p
	sink(**pp)
}

// ── Slice of pointers ────────────────────────────────────────────────

func sliceOfPtr001T() {
	data := source()
	obj := &HeapObj{value: data}
	ptrs := []*HeapObj{obj}
	sink(ptrs[0].value)
}

func sliceOfPtr002F() {
	_ = source()
	obj := &HeapObj{value: "safe"}
	ptrs := []*HeapObj{obj}
	sink(ptrs[0].value)
}

// ── Map of pointers ──────────────────────────────────────────────────

func mapOfPtr001T() {
	data := source()
	obj := &HeapObj{value: data}
	m := map[string]*HeapObj{"key": obj}
	sink(m["key"].value)
}

func mapOfPtr002F() {
	_ = source()
	obj := &HeapObj{value: "safe"}
	m := map[string]*HeapObj{"key": obj}
	sink(m["key"].value)
}
