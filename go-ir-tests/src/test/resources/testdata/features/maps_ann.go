package p

//go:ir-test func=makeMapExample
func makeMapExample() map[string]int {
	return make(map[string]int) //@ inst("GoIRMakeMap")
}

//go:ir-test func=mapLookup
func mapLookup(m map[string]int, key string) int {
	return m[key] //@ inst("GoIRLookup", commaOk=false)
}

//go:ir-test func=mapLookupOk
func mapLookupOk(m map[string]int, key string) (int, bool) {
	v, ok := m[key] //@ inst("GoIRLookup", commaOk=true)
	return v, ok
}

//go:ir-test func=mapUpdate
func mapUpdate(m map[string]int, key string, val_ int) {
	m[key] = val_ //@ inst("GoIRMapUpdate")
}
