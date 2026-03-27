package p

//go:ir-test func=rangeSlice
func rangeSlice(xs []int) int {
	total := 0
	for _, x := range xs {
		total += x
	}
	return total
}

//go:ir-test func=rangeMap
func rangeMap(m map[string]int) int {
	total := 0
	for _, v := range m {
		total += v
	}
	return total
}

//go:ir-test func=rangeString
func rangeString(s string) int {
	count := 0
	for range s {
		count++
	}
	return count
}
