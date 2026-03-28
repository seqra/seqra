package test

// ── Helper functions for interprocedural tests ──────────────────────

func identity(x string) string          { return x }
func identityChain(x string) string     { return identity(x) }
func identityChainDeep(x string) string { return identityChain(x) }
func selectFirst(a, b string) string    { return a }
func selectSecond(a, b string) string   { return b }
func dropValue(x string) string         { return "safe" }

// ── Argument/return passing ─────────────────────────────────────────

func returnValuePassing001F() {
	taintSrc := source()
	result := dropValue(taintSrc)
	sink(result)
}

func returnValuePassing002T() {
	taintSrc := source()
	result := identity(taintSrc)
	sink(result)
}

func argPassing001F() {
	taintSrc := source()
	result := selectSecond(taintSrc, "clean")
	sink(result)
}

func argPassing002T() {
	taintSrc := source()
	result := selectFirst(taintSrc, "clean")
	sink(result)
}

func argPassing005F() {
	taintSrc := source()
	result := selectFirst("clean", taintSrc)
	sink(result)
}

func argPassing006T() {
	taintSrc := source()
	result := selectSecond("clean", taintSrc)
	sink(result)
}

// ── Deep call chains ────────────────────────────────────────────────

func deepCall001T() {
	data := source()
	result := identity(data)
	sink(result)
}

func deepCall002T() {
	data := source()
	result := identityChain(data)
	sink(result)
}

func deepCall003T() {
	data := source()
	result := identityChainDeep(data)
	sink(result)
}

func deepCallClean001F() {
	data := source()
	_ = data
	result := identity("safe")
	sink(result)
}

// ── Argument position sensitivity ───────────────────────────────────

func argPosition001T() {
	data := source()
	result := selectFirst(data, "clean")
	sink(result)
}

func argPosition002F() {
	data := source()
	result := selectSecond(data, "clean")
	sink(result)
}
