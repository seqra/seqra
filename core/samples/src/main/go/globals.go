package test

// ── Global variable tests ────────────────────────────────────────────

func globalWrite001T() {
	globalTainted = source()
	sink(globalTainted)
}

func globalWrite002F() {
	_ = source()
	globalClean = "still safe"
	sink(globalClean)
}

func globalWriteRead001T() {
	globalTainted = source()
	result := globalTainted
	sink(result)
}

func globalWriteRead002F() {
	_ = source()
	globalClean = "safe"
	result := globalClean
	sink(result)
}

// ── Global through function ──────────────────────────────────────────

func setGlobal(val string) {
	globalTainted = val
}

func getGlobal() string {
	return globalTainted
}

func globalFunc001T() {
	data := source()
	setGlobal(data)
	result := getGlobal()
	sink(result)
}

func globalFunc002F() {
	_ = source()
	setGlobal("safe")
	result := getGlobal()
	sink(result)
}

// ── Global struct ────────────────────────────────────────────────────

type GlobalHolder struct {
	data string
}

var globalHolder GlobalHolder

func globalStruct001T() {
	globalHolder.data = source()
	sink(globalHolder.data)
}

func globalStruct002F() {
	_ = source()
	globalHolder.data = "safe"
	sink(globalHolder.data)
}
