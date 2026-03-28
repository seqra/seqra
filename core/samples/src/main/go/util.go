package test

// String source/sink (primary)
func source() string     { return "tainted" }
func sink(data string)   { consume(data) }
func consume(str string) { _ = str }

// Typed sources for specific test categories
func sourceInt() int         { return 42 }
func sourceFloat() float64   { return 3.14 }
func sourceAny() interface{} { return "tainted" }
func sourceBool() bool       { return true }

// Typed sinks
func sinkAny(data interface{}) { _ = data }
func sinkInt(data int)         { _ = data }
func sinkBool(data bool)       { _ = data }
func sinkFloat(data float64)   { _ = data }

// Multiple taint mark sources/sinks
func sourceA() string   { return "a" }
func sourceB() string   { return "b" }
func sinkA(data string) { _ = data }
func sinkB(data string) { _ = data }

// Pass-through stubs (behavior configured via TaintRules.Pass)
func passthrough(data string) string          { return data }
func sanitize(data string) string             { return "clean" }
func transform(in1 string, in2 string) string { return in2 }

// Collection sources (whole container tainted via Result position)
func sourceSlice() []string        { return []string{"tainted"} }
func sourceMap() map[string]string { return map[string]string{"k": "tainted"} }

// Global variables for global taint tests
var globalTainted string
var globalClean string = "safe"
