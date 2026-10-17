package core

import "runtime"

// classpathSeparator — разделитель путей в classpath: «;» в Windows, «:» в остальных.
func classpathSeparator() rune {
	if runtime.GOOS == "windows" {
		return ';'
	}
	return ':'
}
