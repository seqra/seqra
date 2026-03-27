package p

//go:ir-test func=makeSlice
func makeSlice(n int) []int {
	return make([]int, n) //@ inst("GoIRMakeSlice")
}

//go:ir-test func=sliceIndex
func sliceIndex(s []int, i int) int {
	return s[i] //@ inst("GoIRIndexAddr")
}

//go:ir-test func=sliceSlice
func sliceSlice(s []int) []int {
	return s[1:3] //@ inst("GoIRSlice")
}

//go:ir-test func=sliceIndexAddr
func sliceIndexAddr(s []int, i int, val_ int) {
	s[i] = val_ //@ inst("GoIRIndexAddr")
}
