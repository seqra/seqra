package p

//go:ir-test func=intToFloat
func intToFloat(x int) float64 {
	return float64(x) //@ inst("GoIRConvert")
}

//go:ir-test func=floatToInt
func floatToInt(x float64) int {
	return int(x) //@ inst("GoIRConvert")
}

//go:ir-test func=stringToBytes
func stringToBytes(s string) []byte {
	return []byte(s) //@ inst("GoIRConvert")
}

//go:ir-test func=bytesToString
func bytesToString(b []byte) string {
	return string(b) //@ inst("GoIRConvert")
}
