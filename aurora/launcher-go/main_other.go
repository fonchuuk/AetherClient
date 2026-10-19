//go:build !windows

package main

import (
	"fmt"
	"os"
)

// main вне Windows существует только для того, чтобы тесты логики собирались
// на любой машине: сам лаунчер — программа Windows.
func main() {
	fmt.Fprintln(os.Stdout, "AetherGlass Launcher собирается только для Windows. "+
		"Логика проверяется тестами: go test ./core")
}
