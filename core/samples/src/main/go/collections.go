package test

// ── Collection sensitivity tests (slices, maps, arrays) ─────────────
// Key model: key-insensitive, but element-level vs container are distinct.

// ── Slice tests ──────────────────────────────────────────────────────

func sliceElem001T() {
	data := source()
	s := make([]string, 3)
	s[0] = data
	sink(s[0])
}

func sliceElem002T() {
	data := source()
	s := make([]string, 3)
	s[0] = data
	sink(s[1]) // key-insensitive: any element access on tainted container
}

func sliceLiteral001T() {
	data := source()
	s := []string{data, "clean"}
	sink(s[0])
}

func sliceCopy001T() {
	data := source()
	s := make([]string, 3)
	s[0] = data
	s2 := s
	sink(s2[0])
}

func slicePassToFunc001T() {
	data := source()
	s := make([]string, 3)
	s[0] = data
	readSlice(s)
}

func readSlice(s []string) {
	sink(s[0])
}

func sliceReturnElem001T() {
	data := source()
	s := make([]string, 3)
	s[0] = data
	result := getSliceElem(s)
	sink(result)
}

func getSliceElem(s []string) string {
	return s[0]
}

func sliceOverwrite001F() {
	data := source()
	s := make([]string, 3)
	s[0] = data
	s[0] = "safe"
	sink(s[0])
}

// ── Map tests ────────────────────────────────────────────────────────

func mapElem001T() {
	data := source()
	m := make(map[string]string)
	m["key1"] = data
	sink(m["key1"])
}

func mapElem002T() {
	data := source()
	m := make(map[string]string)
	m["key1"] = data
	sink(m["key2"]) // key-insensitive
}

func mapLiteral001T() {
	data := source()
	m := map[string]string{"k": data}
	sink(m["k"])
}

func mapPassToFunc001T() {
	data := source()
	m := make(map[string]string)
	m["k"] = data
	readMap(m)
}

func readMap(m map[string]string) {
	sink(m["k"])
}

func mapReturnElem001T() {
	data := source()
	m := make(map[string]string)
	m["k"] = data
	result := getMapElem(m)
	sink(result)
}

func getMapElem(m map[string]string) string {
	return m["k"]
}

// ── Array tests ──────────────────────────────────────────────────────

func arrayElem001T() {
	data := source()
	var a [3]string
	a[0] = data
	sink(a[0])
}

func arrayElem002T() {
	data := source()
	var a [3]string
	a[0] = data
	sink(a[1]) // key-insensitive
}

func arrayPassToFunc001T() {
	data := source()
	var a [3]string
	a[0] = data
	readArray(a)
}

func readArray(a [3]string) {
	sink(a[0])
}

// ── Mixed: slice of structs ──────────────────────────────────────────

type CollItem struct {
	value string
	label string
}

func sliceOfStructs001T() {
	data := source()
	items := []CollItem{{value: data, label: "x"}}
	sink(items[0].value)
}

func sliceOfStructs002F() {
	data := source()
	items := []CollItem{{value: data, label: "x"}}
	sink(items[0].label)
}
