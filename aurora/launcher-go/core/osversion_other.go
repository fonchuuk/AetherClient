//go:build !windows && !linux

package core

// osVersion — версию ОС узнать нечем (на macOS без cgo это требует вызова
// sysctlbyname). Правила Mojang с os.version для актуальных версий игры не
// используются, поэтому пустая строка ни на что не влияет.
func osVersion() (string, error) { return "", nil }
