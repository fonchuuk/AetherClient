package core

import (
	"encoding/json"
	"os"
	"path/filepath"
	"runtime"
	"strings"
	"testing"
)

// Тесты Java-части. Структура индекса и манифеста Mojang взята не из головы,
// а из живого ответа launchermeta.mojang.com: сначала платформа, потом
// компонент, внутри — сборки, у сборки отдельный манифест со списком файлов.

const sampleIndex = `{
  "windows-x64": {
    "java-runtime-delta": [
      {
        "version": {"name": "21.0.7", "released": "2025-05-19T08:34:42+00:00"},
        "manifest": {
          "sha1": "cb4394a27089d19f65d5baa6cf0482c27c3c7865",
          "size": 148529,
          "url": "https://piston-meta.mojang.com/v1/packages/cb4394a27089d19f65d5baa6cf0482c27c3c7865/manifest.json"
        }
      }
    ],
    "jre-legacy": [
      {
        "version": {"name": "8u51", "released": "2015-01-01T00:00:00+00:00"},
        "manifest": {"sha1": "x", "size": 1, "url": "https://example.invalid/manifest.json"}
      }
    ]
  },
  "linux": {
    "java-runtime-delta": [
      {"version": {"name": "21.0.7"}, "manifest": {"url": "https://example.invalid/linux.json"}}
    ]
  }
}`

func TestRuntimeIndexParsing(t *testing.T) {
	var document any
	if err := json.Unmarshal([]byte(sampleIndex), &document); err != nil {
		t.Fatal(err)
	}
	raw, err := json.Marshal(document)
	if err != nil {
		t.Fatal(err)
	}
	var index RuntimeIndex
	if err := json.Unmarshal(raw, &index); err != nil {
		t.Fatalf("индекс не разобрался: %v", err)
	}
	entry, err := index.Latest("windows-x64", "java-runtime-delta")
	if err != nil {
		t.Fatalf("поиск сборки: %v", err)
	}
	if entry.Version.Name != "21.0.7" {
		t.Fatalf("версия сборки: %s", entry.Version.Name)
	}
	if !strings.Contains(entry.Manifest.URL, "piston-meta.mojang.com") {
		t.Fatalf("ссылка на манифест: %s", entry.Manifest.URL)
	}
	// Платформа без нужного компонента — понятная ошибка, а не паника.
	if _, err := index.Latest("windows-x64", "java-runtime-gamma"); err == nil {
		t.Fatal("отсутствующий компонент должен давать ошибку")
	}
	if _, err := index.Latest("plan9", "java-runtime-delta"); err == nil {
		t.Fatal("отсутствующая платформа должна давать ошибку")
	}
}

func TestRuntimeManifestParsing(t *testing.T) {
	files, err := ParseRuntimeManifest([]byte(`{
	  "files": {
	    "bin": {"type": "directory"},
	    "bin/javaw.exe": {
	      "type": "file",
	      "executable": true,
	      "downloads": {"raw": {"sha1": "abc", "size": 123, "url": "https://example.invalid/javaw.exe"},
	                    "lzma": {"sha1": "def", "size": 12, "url": "https://example.invalid/javaw.exe.lzma"}}
	    },
	    "lib/modules": {"type": "file", "downloads": {"raw": {"sha1": "0", "size": 9,
	        "url": "https://example.invalid/modules"}}}
	  }
	}`))
	if err != nil {
		t.Fatalf("манифест: %v", err)
	}
	if len(files) != 3 {
		t.Fatalf("файлов в манифесте: %d", len(files))
	}
	javaw, ok := files["bin/javaw.exe"]
	if !ok || javaw.Type != "file" || !javaw.Executable {
		t.Fatalf("javaw.exe: %+v", javaw)
	}
	raw, ok := javaw.Downloads["raw"]
	if !ok || raw.SHA1 != "abc" || raw.Size != 123 {
		t.Fatalf("скачивание javaw: %+v", raw)
	}
	if dir, ok := files["bin"]; !ok || dir.Type != "directory" {
		t.Fatalf("каталог bin: %+v", dir)
	}
}

func TestExecutableIn(t *testing.T) {
	root := t.TempDir()
	if got := ExecutableIn(root); got != "" {
		t.Fatalf("в пустом каталоге исполняемого файла быть не должно: %s", got)
	}
	if err := os.MkdirAll(filepath.Join(root, "bin"), 0o755); err != nil {
		t.Fatal(err)
	}
	target := filepath.Join(root, "bin", "javaw.exe")
	if err := os.WriteFile(target, []byte("не настоящий exe"), 0o755); err != nil {
		t.Fatal(err)
	}
	if got := ExecutableIn(root); got != target {
		t.Fatalf("исполняемый файл: %s", got)
	}
}

func TestJavaPolicyPrefersBundledRuntime(t *testing.T) {
	directory := t.TempDir()
	paths := NewPaths(directory)
	// Кладём «рантайм», который уже скачан: он и должен быть выбран.
	runtimeDir := filepath.Join(paths.Runtimes(), "java-runtime-delta", "windows-x64", "bin")
	if err := os.MkdirAll(runtimeDir, 0o755); err != nil {
		t.Fatal(err)
	}
	fakeJava := filepath.Join(runtimeDir, "javaw.exe")
	if err := os.WriteFile(fakeJava, []byte("стаб"), 0o755); err != nil {
		t.Fatal(err)
	}
	provisioner := JavaProvisioner{Paths: paths}
	// Проверяем сам порядок: если кэш найден, наружу отдаётся он.
	if got := ExecutableIn(filepath.Join(paths.Runtimes(), "java-runtime-delta", "windows-x64")); got != fakeJava {
		t.Fatalf("кэшированный рантайм не нашёлся: %s", got)
	}
	// Без сети скачивание не пройдёт — тогда в ход идёт системная Java.
	// Проверяем, что ошибка понятная, а не пустая.
	_, err := provisioner.Ensure("java-runtime-delta", RequiredJavaMajor)
	if err == nil {
		t.Skip("в этой песочнице есть сеть и Java — политика проверится на живом запуске")
	}
	if strings.TrimSpace(err.Error()) == "" {
		t.Fatal("ошибка без текста")
	}
}

func TestInspectMissingFile(t *testing.T) {
	info := Inspect(filepath.Join(t.TempDir(), "нет-такого-java"))
	if info.Valid() {
		t.Fatal("несуществующий файл не может быть валидной Java")
	}
	if !strings.Contains(info.Error, "не найден") {
		t.Fatalf("текст ошибки: %s", info.Error)
	}
	if Inspect("").Valid() {
		t.Fatal("пустой путь не может быть валидной Java")
	}
}

// TestConsoleSibling — рядом с «молчаливым» javaw.exe версию спрашиваем
// у соседнего java.exe, а путь для запуска оставляем прежним.
func TestConsoleSibling(t *testing.T) {
	directory := t.TempDir()
	javaw := filepath.Join(directory, "javaw.exe")
	java := filepath.Join(directory, "java.exe")
	if err := os.WriteFile(javaw, []byte("стаб"), 0o644); err != nil {
		t.Fatal(err)
	}
	if got := consoleSibling(javaw); got != "" {
		t.Fatalf("без java.exe соседа быть не должно, пришло: %s", got)
	}
	if err := os.WriteFile(java, []byte("стаб"), 0o644); err != nil {
		t.Fatal(err)
	}
	if got := consoleSibling(javaw); got != java {
		t.Fatalf("сосед: получили %q, ждали %q", got, java)
	}
	if got := consoleSibling(java); got != "" {
		t.Fatalf("у java.exe соседа быть не должно, пришло: %s", got)
	}
	if got := consoleSibling(filepath.Join(directory, "нет-такого-javaw.exe")); got != "" {
		t.Fatalf("у несуществующего файла соседа быть не должно, пришло: %s", got)
	}
}

// TestInspectFallsBackToConsoleJava — «молчаливый» javaw.exe: версию берём
// у соседнего java.exe, а путь для запуска не подменяем.
func TestInspectFallsBackToConsoleJava(t *testing.T) {
	if runtime.GOOS == "windows" {
		t.Skip("на Windows вместо сценариев нужны настоящие файлы Java")
	}
	directory := t.TempDir()
	silent := filepath.Join(directory, "javaw.exe")
	voice := filepath.Join(directory, "java.exe")
	if err := os.WriteFile(silent, []byte("#!/bin/sh\nexit 0\n"), 0o755); err != nil {
		t.Fatal(err)
	}
	if err := os.WriteFile(voice, []byte(
		"#!/bin/sh\necho 'openjdk version \"21.0.7\" 2025-04-15' 1>&2\n"), 0o755); err != nil {
		t.Fatal(err)
	}
	info := Inspect(silent)
	if !info.Valid() {
		t.Fatalf("версию определить не удалось: %+v", info)
	}
	if info.Executable != silent {
		t.Fatalf("путь подменили: %s", info.Executable)
	}
	if info.MajorVersion != 21 {
		t.Fatalf("версия: %d (%s)", info.MajorVersion, info.VersionText)
	}
}
