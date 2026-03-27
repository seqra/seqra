package main

import "fmt"

func add(a, b int) int {
	return a + b
}

func sub(a, b int) int {
	return a - b
}

func mul(a, b int) int {
	return a * b
}

func main() {
	fmt.Println(add(3, 4))
	fmt.Println(sub(10, 3))
	fmt.Println(mul(5, 6))
	fmt.Println(add(sub(10, 3), mul(2, 3)))
}
