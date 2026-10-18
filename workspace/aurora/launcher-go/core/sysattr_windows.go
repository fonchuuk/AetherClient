//go:build windows

package core

import (
	"fmt"
	"os"
	"path/filepath"
	"syscall"

	"golang.org/x/sys/windows"
)

// hiddenWindowAttr — окно запущенной игры не должно появляться отдельным
// консольным окном, поэтому процесс создаётся со скрытым окном.
func hiddenWindowAttr() *syscall.SysProcAttr {
	return &syscall.SysProcAttr{HideWindow: true}
}

// vendorCandidates — Java, установленная известными сборками.
func vendorCandidates(name string) []string {
	roots := []string{
		`C:\Program Files\Java`,
		`C:\Program Files\Eclipse Adoptium`,
		`C:\Program Files\Zulu`,
		`C:\Program Files\BellSoft`,
		`C:\Program Files\Amazon Corretto`,
		`C:\Program Files\Semeru`,
		`C:\Program Files\Microsoft`,
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
			if info, err := os.Stat(candidate); err == nil && !info.IsDir() {
				result = append(result, candidate)
			}
		}
	}
	return result
}

// osVersion — точная версия Windows: RtlGetVersion, а не устаревший GetVersion,
// который на Windows 8.1 и новее отдаёт заниженный номер.
func osVersion() (string, error) {
	info := windows.RtlGetVersion()
	if info == nil {
		return "", &Errorf{Message: "не удалось узнать версию Windows"}
	}
	return fmt.Sprintf("%d.%d.%d", info.MajorVersion, info.MinorVersion, info.BuildNumber), nil
}
