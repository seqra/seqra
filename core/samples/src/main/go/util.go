package test

// String source/sink (primary)
func source() string     { return "tainted" }
func sink(data string)   { consume(data) }
func consume(str string) { _ = str }

// Typed sources for specific test categories
func sourceInt() int         { return 42 }
func sourceAny() interface{} { return "tainted" }

// Typed sinks
func sinkAny(data interface{}) { _ = data }
func sinkInt(data int)         { _ = data }
