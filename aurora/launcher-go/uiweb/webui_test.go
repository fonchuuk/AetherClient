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
	if !strings.Contains(testIndexHTML, "Папка Ресурспаков") {
		t.Fatalf("webui/index.html must contain button 'Папка Ресурспаков'")
	}
	if !strings.Contains(testIndexHTML, "openFolder") {
		t.Fatalf("webui/index.html must wire 'openFolder'")
	}
	if !strings.Contains(testIndexHTML, "openModsFolder") {
		t.Fatalf("webui/index.html must wire 'openModsFolder'")
	}
	if !strings.Contains(testIndexHTML, "openResourcePacksFolder") {
		t.Fatalf("webui/index.html must wire 'openResourcePacksFolder'")
	}
	if !strings.Contains(testIndexHTML, "setModProjectType") {
		t.Fatalf("webui/index.html must wire 'setModProjectType' for Mods & Resource Packs")
	}
	if !strings.Contains(testIndexHTML, "loginOffline") {
		t.Fatalf("webui/index.html must wire 'loginOffline' for offline accounts")
	}
	if strings.Contains(testIndexHTML, ">Java</button>") || strings.Contains(testIndexHTML, "<span>Java</span>") {
		t.Fatalf("webui/index.html must NOT have a 'Java' tab in the left navigation rail")
	}
	if !strings.Contains(testIndexHTML, "saveLauncherSize") {
		t.Fatalf("webui/index.html must persist launcher window size via 'saveLauncherSize'")
	}
	if !strings.Contains(testIndexHTML, "btn-play") {
		t.Fatalf("webui/index.html must style 'Играть' as the largest prominent button ('btn-play')")
	}
	if !strings.Contains(testIndexHTML, "mod-card") {
		t.Fatalf("webui/index.html must render mods and resourcepacks as cards ('mod-card')")
	}
	if !strings.Contains(testIndexHTML, "search-wrap") {
		t.Fatalf("webui/index.html must include glass search input with magnifying glass icon ('search-wrap')")
	}
}
