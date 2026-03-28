package test

// ── Combination / stress tests ───────────────────────────────────────
// Tests that combine multiple features to verify complex interactions.

// ── Struct + interface + method call chain ────────────────────────────

type CombProcessor interface {
	Process() string
}

type CombTainted struct {
	data string
}

func (c CombTainted) Process() string { return c.data }

type CombSafe struct{}

func (c CombSafe) Process() string { return "safe" }

func combStructInterface001T() {
	data := source()
	var p CombProcessor = CombTainted{data: data}
	result := p.Process()
	sink(result)
}

func combStructInterface002F() {
	_ = source()
	var p CombProcessor = CombSafe{}
	result := p.Process()
	sink(result)
}

// ── Closure + struct field ───────────────────────────────────────────

func combClosureField001T() {
	data := source()
	p := SFPair{tainted: data, clean: "safe"}
	f := func() string { return p.tainted }
	result := f()
	sink(result)
}

func combClosureField002F() {
	data := source()
	p := SFPair{tainted: data, clean: "safe"}
	f := func() string { return p.clean }
	result := f()
	sink(result)
}

// ── Map + function call + multi-return ───────────────────────────────

func combMapFunc001T() {
	data := source()
	m := map[string]string{"key": data}
	result, _ := twoReturns(m["key"], "clean")
	sink(result)
}

func combMapFunc002F() {
	data := source()
	m := map[string]string{"key": data}
	_, result := twoReturns(m["key"], "clean")
	sink(result)
}

// ── Slice + loop + function call ─────────────────────────────────────

func combSliceLoop001T() {
	data := source()
	s := []string{"a", data, "b"}
	var result string
	for _, v := range s {
		result = identity(v)
	}
	sink(result)
}

func combSliceLoop002F() {
	data := source()
	s := []string{"a", data, "b"}
	var result string
	for _, v := range s {
		result = dropValue(v)
	}
	sink(result)
}

// ── Pointer + method + interface ─────────────────────────────────────

func combPtrMethod001T() {
	data := source()
	obj := &MRPtrContainer{}
	obj.SetValue(data)
	result := obj.GetValue()
	sink(result)
}

func combPtrMethod002F() {
	_ = source()
	obj := &MRPtrContainer{}
	obj.SetValue("safe")
	result := obj.GetValue()
	sink(result)
}

// ── Nested function calls + struct ───────────────────────────────────

func wrapInPair(data string) SFPair {
	return SFPair{tainted: data, clean: "safe"}
}

func extractFromPair(p SFPair) string {
	return p.tainted
}

func combNestedFunc001T() {
	data := source()
	result := extractFromPair(wrapInPair(data))
	sink(result)
}

func combNestedFunc002F() {
	_ = source()
	result := extractFromPair(wrapInPair("safe"))
	// wrapInPair puts "safe" in tainted field, so extractFromPair returns "safe"
	// However, the field is literally "safe" string, not tainted
	sink(result)
}

// ── Deep chain: closure capturing struct returned from func ──────────

func combDeepChain001T() {
	data := source()
	p := wrapInPair(data)
	f := func() string {
		return extractFromPair(p)
	}
	result := f()
	sink(result)
}

func combDeepChain002F() {
	_ = source()
	p := wrapInPair("safe")
	f := func() string {
		return extractFromPair(p)
	}
	result := f()
	sink(result)
}

// ── Struct field + slice element ─────────────────────────────────────

type CombHolder struct {
	items []string
}

func combStructSlice001T() {
	data := source()
	h := CombHolder{items: []string{data}}
	sink(h.items[0])
}

func combStructSlice002F() {
	_ = source()
	h := CombHolder{items: []string{"safe"}}
	sink(h.items[0])
}

// ── Multiple assignments in sequence ─────────────────────────────────

func combSequence001T() {
	a := source()
	b := a
	c := b
	d := c
	e := d
	sink(e)
}

func combSequence002F() {
	a := source()
	b := a
	_ = b
	c := "safe"
	d := c
	e := d
	sink(e)
}
