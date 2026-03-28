package test

// ── Additional struct operation tests ────────────────────────────────

// ── Struct copy semantics ────────────────────────────────────────────

type SOData struct {
	value string
	extra string
}

func structCopy001T() {
	data := source()
	original := SOData{value: data, extra: "x"}
	copied := original
	sink(copied.value)
}

func structCopy002F() {
	data := source()
	original := SOData{value: data, extra: "x"}
	copied := original
	sink(copied.extra)
}

func structCopy003T() {
	data := source()
	original := SOData{value: data, extra: "x"}
	copied := original
	original.value = "safe" // mutating original doesn't affect copy
	sink(copied.value)
}

// ── Struct as function argument (value semantics) ────────────────────

func readSOValue(d SOData) string { return d.value }
func readSOExtra(d SOData) string { return d.extra }

func structArg001T() {
	data := source()
	d := SOData{value: data, extra: "x"}
	result := readSOValue(d)
	sink(result)
}

func structArg002F() {
	data := source()
	d := SOData{value: data, extra: "x"}
	result := readSOExtra(d)
	sink(result)
}

// ── Struct returned from function ────────────────────────────────────

func makeSOData(val string) SOData {
	return SOData{value: val, extra: "x"}
}

func structReturn001T() {
	data := source()
	d := makeSOData(data)
	sink(d.value)
}

func structReturn002F() {
	data := source()
	d := makeSOData(data)
	sink(d.extra)
}

// ── Nested struct modification ───────────────────────────────────────

type SOOuter struct {
	inner SOData
	label string
}

func nestedStructMod001T() {
	data := source()
	o := SOOuter{
		inner: SOData{value: data, extra: "x"},
		label: "y",
	}
	sink(o.inner.value)
}

func nestedStructMod002F() {
	data := source()
	o := SOOuter{
		inner: SOData{value: data, extra: "x"},
		label: "y",
	}
	sink(o.label)
}

func nestedStructMod003F() {
	data := source()
	o := SOOuter{
		inner: SOData{value: data, extra: "x"},
		label: "y",
	}
	sink(o.inner.extra)
}

// ── Struct pointer field modification ────────────────────────────────

func structPtrField001T() {
	data := source()
	d := &SOData{}
	d.value = data
	sink(d.value)
}

func structPtrField002F() {
	data := source()
	d := &SOData{}
	d.value = data
	sink(d.extra)
}

// ── Struct with method modifying field ───────────────────────────────

type SOWithMethod struct {
	data string
}

func (s *SOWithMethod) Set(val string) { s.data = val }
func (s SOWithMethod) Get() string     { return s.data }

func structMethod001T() {
	data := source()
	s := &SOWithMethod{}
	s.Set(data)
	result := s.Get()
	sink(result)
}

func structMethod002F() {
	_ = source()
	s := &SOWithMethod{}
	s.Set("safe")
	result := s.Get()
	sink(result)
}
