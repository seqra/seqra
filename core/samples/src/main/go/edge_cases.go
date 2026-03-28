package test

// ── Edge case tests ──────────────────────────────────────────────────

// ── Nil/zero value handling ──────────────────────────────────────────

func nilSlice001F() {
	_ = source()
	var s []string
	if s != nil {
		sink(s[0])
	}
}

func nilMap001F() {
	_ = source()
	var m map[string]string
	if m != nil {
		sink(m["k"])
	}
}

// ── Empty struct ─────────────────────────────────────────────────────

type EdgeEmpty struct{}

func emptyStruct001F() {
	_ = source()
	e := EdgeEmpty{}
	_ = e
	sink("safe")
}

// ── Very long assignment chain ───────────────────────────────────────

func longChain001T() {
	v0 := source()
	v1 := v0
	v2 := v1
	v3 := v2
	v4 := v3
	v5 := v4
	v6 := v5
	v7 := v6
	v8 := v7
	v9 := v8
	sink(v9)
}

func longChain002F() {
	v0 := source()
	_ = v0
	v1 := "safe"
	v2 := v1
	v3 := v2
	v4 := v3
	v5 := v4
	v6 := v5
	v7 := v6
	v8 := v7
	v9 := v8
	sink(v9)
}

// ── Taint through multiple function hops ─────────────────────────────

func hop1(x string) string { return hop2(x) }
func hop2(x string) string { return hop3(x) }
func hop3(x string) string { return hop4(x) }
func hop4(x string) string { return hop5(x) }
func hop5(x string) string { return x }

func deepHop001T() {
	data := source()
	result := hop1(data)
	sink(result)
}

func deepHop002F() {
	_ = source()
	result := hop1("safe")
	sink(result)
}

// ── Recursive function ───────────────────────────────────────────────

func recurse(s string, n int) string {
	if n <= 0 {
		return s
	}
	return recurse(s, n-1)
}

func recursive001T() {
	data := source()
	result := recurse(data, 3)
	sink(result)
}

func recursive002F() {
	_ = source()
	result := recurse("safe", 3)
	sink(result)
}

// ── Same variable reused in different contexts ───────────────────────

func reuseVar001T() {
	x := source()
	sink(x) // first use — tainted
}

func reuseVar002F() {
	x := source()
	x = "safe"
	sink(x) // second use — overwritten
}

func reuseVar003T() {
	x := "safe"
	x = source()
	sink(x) // overwritten with taint
}

// ── Taint through temp variable ──────────────────────────────────────

func tempVar001T() {
	data := source()
	tmp := data
	data = "safe"
	sink(tmp) // tmp still holds tainted value
}

func tempVar002F() {
	data := source()
	tmp := "safe"
	_ = data
	sink(tmp)
}

// ── Multiple returns from same function ──────────────────────────────

func edgeMultiCall001T() {
	data := source()
	r1 := identity(data)
	r2 := identity("safe")
	sink(r1)
	consume(r2)
}

func edgeMultiCall002F() {
	data := source()
	r1 := identity("safe")
	r2 := identity(data)
	sink(r1)
	consume(r2)
}

// ── Struct literal with mixed taint ──────────────────────────────────

type EdgeMixed struct {
	a string
	b string
	c string
}

func structMixed001T() {
	data := source()
	m := EdgeMixed{a: data, b: "safe", c: "safe"}
	sink(m.a)
}

func structMixed002F() {
	data := source()
	m := EdgeMixed{a: data, b: "safe", c: "safe"}
	sink(m.b)
}

func structMixed003F() {
	data := source()
	m := EdgeMixed{a: data, b: "safe", c: "safe"}
	sink(m.c)
}

// ── Swap pattern ─────────────────────────────────────────────────────

func swapVars001T() {
	a := source()
	b := "safe"
	tmp := a
	a = b
	b = tmp
	sink(b) // b now holds original tainted value
	consume(a)
}

func swapVars002F() {
	a := source()
	b := "safe"
	tmp := a
	a = b
	b = tmp
	sink(a) // a now holds "safe"
	consume(b)
}
