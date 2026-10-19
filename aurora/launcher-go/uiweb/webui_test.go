package uiweb

import (
	_ "embed"
	"strings"
	"testing"
)

//go:embed webui/index.html
var testIndexHTML string

func TestWebUIRootAndModsFolderButtons(t *testing.T) {
	if !strings.Contains(testIndexHTML, "Коренная папка") {
		t.Fatalf("webui/index.html must contain button 'Коренная папка'")
	}
	if !strings.Contains(testIndexHTML, "Папка Mods") {
		t.Fatalf("webui/index.html must contain button 'Папка Mods'")
	}
	if !strings.Contains(testIndexHTML, "openFolder") {
		t.Fatalf("webui/index.html must wire 'openFolder'")
	}
	if !strings.Contains(testIndexHTML, "openModsFolder") {
		t.Fatalf("webui/index.html must wire 'openModsFolder'")
	}
}
