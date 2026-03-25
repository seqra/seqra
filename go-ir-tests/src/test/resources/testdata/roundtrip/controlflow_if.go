package main

import "fmt"

func abs(x int) int {
	if x < 0 {
		return -x
	}
	return x
}

func max(a, b int) int {
	if a > b {
		return a
	}
	return b
}

func clamp(x, lo, hi int) int {
	if x < lo {
		return lo
	}
	if x > hi {
		return hi
	}
	return x
}

func main() {
	fmt.Println(abs(-5))
	fmt.Println(abs(3))
	fmt.Println(abs(0))
	fmt.Println(max(3, 7))
	fmt.Println(max(10, 2))
	fmt.Println(clamp(5, 0, 10))
	fmt.Println(clamp(-3, 0, 10))
	fmt.Println(clamp(15, 0, 10))
}
