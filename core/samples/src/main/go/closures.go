package test

// ── Closure and anonymous function tests ─────────────────────────────

// ── Basic anonymous functions ────────────────────────────────────────

func anonFunc001T() {
	data := source()
	f := func(x string) string { return x }
	result := f(data)
	sink(result)
}

func anonFunc002F() {
	data := source()
	_ = data
	f := func(x string) string { return "safe" }
	result := f("anything")
	sink(result)
}

func anonFuncDirect001T() {
	data := source()
	result := func(x string) string { return x }(data)
	sink(result)
}

func anonFuncDirect002F() {
	_ = source()
	result := func(x string) string { return "safe" }("anything")
	sink(result)
}

// ── Closures that capture variables ──────────────────────────────────

func closure001T() {
	data := source()
	f := func() string { return data }
	result := f()
	sink(result)
}

func closure002F() {
	data := source()
	data = "safe"
	f := func() string { return data }
	result := f()
	sink(result)
}

func closureModify001T() {
	data := "safe"
	f := func() {
		data = source()
	}
	f()
	sink(data)
}

func closureModify002F() {
	data := source()
	f := func() {
		data = "safe"
	}
	f()
	sink(data)
}

// ── Closure returned from function ───────────────────────────────────

func makeAdder(prefix string) func(string) string {
	return func(s string) string { return prefix + s }
}

func closureReturn001T() {
	data := source()
	adder := makeAdder(data)
	result := adder("suffix")
	sink(result)
}

func closureReturn002F() {
	_ = source()
	adder := makeAdder("safe")
	result := adder("suffix")
	sink(result)
}

// ── Higher-order functions ───────────────────────────────────────────

func applyFunc(f func(string) string, data string) string {
	return f(data)
}

func higherOrder001T() {
	data := source()
	result := applyFunc(func(s string) string { return s }, data)
	sink(result)
}

func higherOrder002F() {
	data := source()
	_ = data
	result := applyFunc(func(s string) string { return "safe" }, "anything")
	sink(result)
}

func higherOrder003T() {
	data := source()
	result := applyFunc(identity, data)
	sink(result)
}

func higherOrder004F() {
	data := source()
	result := applyFunc(dropValue, data)
	sink(result)
}
