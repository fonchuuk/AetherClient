//go:build windows

package main

import (
	"os"
	"path/filepath"
)

// writeCrashNote — если окно поднять не удалось, пользователь всё равно должен
// увидеть причину: пишем её в файл рядом с exe.
func writeCrashNote(text string) {
	executable, err := os.Executable()
	if err != nil {
		return
	}
	target := filepath.Join(filepath.Dir(executable), "AetherGlass-ошибка.txt")
	_ = os.WriteFile(target, []byte(text), 0o644)
}
