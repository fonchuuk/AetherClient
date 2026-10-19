package core

import (
	"strings"
	"testing"
)

// Проба по живым адресам Mojang. Запускается один раз, файл afterwards удаляется.
func TestLiveRuntimeTraversal(t *testing.T) {
	index, err := FetchRuntimeIndex()
	if err != nil {
		t.Skipf("нет доступа к Mojang: %v", err)
	}
	platform, err := PlatformKey(OSName(), OSArch())
	if err != nil {
		t.Fatal(err)
	}
	entry, err := index.Latest(platform, DefaultRuntimeComponent)
	if err != nil {
		t.Fatalf("обход индекса: %v", err)
	}
	t.Logf("платформа=%s компонент=%s версия=%s", platform, DefaultRuntimeComponent, entry.Version.Name)
	files, err := FetchRuntimeManifest(entry)
	if err != nil {
		t.Fatalf("манифест: %v", err)
	}
	t.Logf("файлов в манифесте: %d", len(files))
	needle := "bin/java"
	if platform == "windows-x64" {
		needle = "bin/javaw.exe"
	}
	found := ""
	for path, file := range files {
		if path == needle {
			found = file.Downloads["raw"].URL
		}
	}
	if found == "" {
		t.Fatalf("в манифесте нет %s", needle)
	}
	if !strings.HasPrefix(found, "https://") {
		t.Fatalf("подозрительная ссылка: %s", found)
	}
	t.Logf("%s -> %s", needle, found)
}
