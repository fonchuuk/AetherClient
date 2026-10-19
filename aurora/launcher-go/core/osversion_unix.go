//go:build linux

package core

import "syscall"

// osVersion — версия ядра Linux: именно её видит Java в os.version.
func osVersion() (string, error) {
	var uts syscall.Utsname
	if err := syscall.Uname(&uts); err != nil {
		return "", err
	}
	return cstring(uts.Release[:]), nil
}

// cstring — строка из буфера ядра, завершённая нулём.
func cstring(bytes []int8) string {
	buffer := make([]byte, 0, len(bytes))
	for _, value := range bytes {
		if value == 0 {
			break
		}
		buffer = append(buffer, byte(value))
	}
	return string(buffer)
}
