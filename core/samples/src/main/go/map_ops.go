package test

// ── Additional map operation tests ───────────────────────────────────

// ── Map with struct values ───────────────────────────────────────────

type MapItem struct {
	data  string
	label string
}

func mapStruct001T() {
	data := source()
	m := map[string]MapItem{
		"key": {data: data, label: "x"},
	}
	sink(m["key"].data)
}

func mapStruct002F() {
	data := source()
	m := map[string]MapItem{
		"key": {data: data, label: "x"},
	}
	sink(m["key"].label)
}

// ── Map iteration ────────────────────────────────────────────────────

func mapIter001T() {
	data := source()
	m := map[string]string{"k1": data, "k2": "safe"}
	var result string
	for _, v := range m {
		result = v
	}
	sink(result)
}

func mapIter002F() {
	data := source()
	m := map[string]string{"k1": "safe", "k2": "safe"}
	var result string
	for _, v := range m {
		result = v
	}
	sink(result)
	consume(data)
}

// ── Map key taint ────────────────────────────────────────────────────

func mapKeyTaint001T() {
	data := source()
	m := map[string]string{data: "value"}
	for k := range m {
		sink(k)
	}
}

// ── Map delete doesn't affect taint ──────────────────────────────────

func mapDelete001T() {
	data := source()
	m := map[string]string{"k1": data, "k2": "safe"}
	delete(m, "k1") // delete doesn't kill taint in analysis
	sink(m["k1"])
}

// ── Map comma-ok lookup ──────────────────────────────────────────────

func mapCommaOk001T() {
	data := source()
	m := map[string]string{"k": data}
	v, ok := m["k"]
	if ok {
		sink(v)
	}
}

func mapCommaOk002F() {
	_ = source()
	m := map[string]string{"k": "safe"}
	v, ok := m["k"]
	if ok {
		sink(v)
	}
}
