//go:build windows

package main

import "aetherglass/launcher/uiweb"

func main() {
	if err := uiweb.Run(); err != nil {
		// Окно показать не удалось — пишем последнее, что знаем, в файл рядом с exe.
		writeCrashNote(err.Error())
	}
}
