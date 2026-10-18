//go:build !windows

package core

import (
	"os"
	"path/filepath"
	"runtime"
	"syscall"
)

// hiddenWindowAttr вне Windows не нужен: там нет консольного окна у GUI-процесса.
func hiddenWindowAttr() *syscall.SysProcAttr { return nil }

// vendorCandidates — типичные каталоги установки Java в Linux и macOS.
func vendorCandidates(name string) []string {
	roots := []string{
		"/usr/lib/jvm",
		"/usr/java",
		"/opt/java",
		"/Library/Java/JavaVirtualMachines",
	}
	var result []string
	for _, root := range roots {
		entries, err := os.ReadDir(root)
		if err != nil {
			continue
		}
		for _, entry := range entries {
			if !entry.IsDir() {
				continue
			}
			candidate := filepath.Join(root, entry.Name(), "bin", name)
			if runtime.GOOS == "darwin" {
				candidate = filepath.Join(root, entry.Name(), "Contents", "Home", "bin", name)
			}
			if info, err := os.Stat(candidate); err == nil && !info.IsDir() {
				result = append(result, candidate)
			}
		}
	}
	return result
}
