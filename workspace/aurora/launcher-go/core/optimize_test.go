package core

import (
	"archive/zip"
	"crypto/sha1"
	"encoding/hex"
	"net/http"
	"net/http/httptest"
	"os"
	"path/filepath"
	"sort"
	"strconv"
	"strings"
	"sync"
	"testing"
	"time"
)

// testify не нужен: проверки короткие и свои.

func sha1Of(t *testing.T, data []byte) string {
	t.Helper()
	sum := sha1.Sum(data)
	return hex.EncodeToString(sum[:])
}

// TestDownloadAllParallel — параллельная загрузка: файлы лежат там, где просили,
// контрольные суммы проверяются, а уже готовый файл не качается повторно.
func TestDownloadAllParallel(t *testing.T) {
	payload := map[string][]byte{
		"/first":  []byte("первый файл"),
		"/second": []byte("второй файл, чуть длиннее"),
		"/third":  []byte(strings.Repeat("третий", 5000)),
	}
	var mutex sync.Mutex
	requests := map[string]int{}
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		mutex.Lock()
		requests[r.URL.Path]++
		mutex.Unlock()
		body, ok := payload[r.URL.Path]
		if !ok {
			w.WriteHeader(http.StatusNotFound)
			return
		}
		_, _ = w.Write(body)
	}))
	defer server.Close()

	root := t.TempDir()
	// Первый файл положим заранее и корректным содержимым: его качать не должны.
	readyPath := filepath.Join(root, "first.bin")
	if err := os.WriteFile(readyPath, payload["/first"], 0o644); err != nil {
		t.Fatal(err)
	}

	jobs := []DownloadJob{
		{URL: server.URL + "/first", Target: readyPath, Size: int64(len(payload["/first"])),
			SHA1: sha1Of(t, payload["/first"]), Name: "первый"},
		{URL: server.URL + "/second", Target: filepath.Join(root, "second.bin"),
			Size: int64(len(payload["/second"])), SHA1: sha1Of(t, payload["/second"])},
		{URL: server.URL + "/third", Target: filepath.Join(root, "sub", "third.bin"),
			Size: int64(len(payload["/third"])), SHA1: sha1Of(t, payload["/third"])},
	}
	if err := DownloadAll(jobs, 4, nil, nil); err != nil {
		t.Fatalf("DownloadAll: %v", err)
	}
	for name, expected := range payload {
		sum, err := SHA1File(filepath.Join(root, map[string]string{
			"/first": "first.bin", "/second": "second.bin", "/third": "sub/third.bin"}[name]))
		if err != nil {
			t.Fatalf("файл %s не найден: %v", name, err)
		}
		if !strings.EqualFold(sum, sha1Of(t, expected)) {
			t.Fatalf("файл %s скачался повреждённым", name)
		}
	}
	mutex.Lock()
	defer mutex.Unlock()
	if requests["/first"] != 0 {
		t.Fatalf("готовый файл скачали заново: %d запросов", requests["/first"])
	}
	if requests["/second"] == 0 || requests["/third"] == 0 {
		t.Fatalf("файлы не скачивались: %v", requests)
	}
}

// TestDownloadAllBadChecksum — повреждённый файл не остаётся на диске.
func TestDownloadAllBadChecksum(t *testing.T) {
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		_, _ = w.Write([]byte("содержимое"))
	}))
	defer server.Close()
	root := t.TempDir()
	target := filepath.Join(root, "broken.bin")
	jobs := []DownloadJob{{URL: server.URL + "/file", Target: target,
		SHA1: strings.Repeat("0", 40)}}
	if err := DownloadAll(jobs, 2, nil, nil); err == nil {
		t.Fatal("ожидалась ошибка контрольной суммы")
	}
	if _, err := os.Stat(target); err == nil {
		t.Fatal("повреждённый файл остался на диске")
	}
	entries, err := os.ReadDir(root)
	if err != nil {
		t.Fatal(err)
	}
	if len(entries) != 0 {
		t.Fatalf("в каталоге остался мусор: %v", entries)
	}
}

// TestDownloadAllCancel — отмена останавливает работу и возвращает ErrCancelled.
func TestDownloadAllCancel(t *testing.T) {
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		time.Sleep(200 * time.Millisecond)
		_, _ = w.Write([]byte("данные"))
	}))
	defer server.Close()
	root := t.TempDir()
	var jobs []DownloadJob
	for i := 0; i < 20; i++ {
		jobs = append(jobs, DownloadJob{URL: server.URL + "/file" + strconv.Itoa(i),
			Target: filepath.Join(root, "file"+strconv.Itoa(i)+".bin")})
	}
	cancel := &Cancel{}
	go func() {
		time.Sleep(60 * time.Millisecond)
		cancel.Cancel()
	}()
	err := DownloadAll(jobs, 4, nil, cancel)
	if err != ErrCancelled {
		t.Fatalf("ожидалась отмена, пришло: %v", err)
	}
}

// TestDedupeJobs — один и тот же файл под разными именами качается один раз.
func TestDedupeJobs(t *testing.T) {
	jobs := []DownloadJob{
		{Target: "a.bin", URL: "1"},
		{Target: "b.bin", URL: "2"},
		{Target: "a.bin", URL: "3"},
	}
	result := dedupeJobs(jobs)
	if len(result) != 2 {
		t.Fatalf("ожидалось два задания, получили %d", len(result))
	}
	if result[0].URL != "1" || result[1].URL != "2" {
		t.Fatalf("потерян порядок или первое вхождение: %v", result)
	}
}

// TestCountingProgress — счётчик файлов сообщает общее количество, а не байты
// одного из параллельных потоков.
func TestCountingProgress(t *testing.T) {
	var lastDone, lastTotal int64
	listener := &countingProgress{total: 300, listener: &testProgress{
		file: func(name string, done, total int64) { lastDone, lastTotal = done, total },
	}}
	listener.File("что-то", 12345, 99999)
	if lastDone != 12345 || lastTotal != 300 {
		t.Fatalf("счётчик испортил числа: done=%d total=%d", lastDone, lastTotal)
	}
}

type testProgress struct {
	stage func(string)
	file  func(string, int64, int64)
}

func (p *testProgress) Stage(text string) {
	if p.stage != nil {
		p.stage(text)
	}
}

func (p *testProgress) File(name string, done int64, total int64) {
	if p.file != nil {
		p.file(name, done, total)
	}
}

func makeNativesArchive(t *testing.T, path string) {
	t.Helper()
	file, err := os.Create(path)
	if err != nil {
		t.Fatal(err)
	}
	defer file.Close()
	writer := zip.NewWriter(file)
	for _, name := range []string{"glfw.dll", "META-INF/MANIFEST.MF", "sub/openal.dll"} {
		entry, err := writer.Create(name)
		if err != nil {
			t.Fatal(err)
		}
		if _, err := entry.Write([]byte("содержимое " + name)); err != nil {
			t.Fatal(err)
		}
	}
	if err := writer.Close(); err != nil {
		t.Fatal(err)
	}
}

// TestExtractNativesStamp — повторный запуск не распаковывает архив заново,
// а если архив другой (другая контрольная сумма) — распаковывает.
func TestExtractNativesStamp(t *testing.T) {
	root := t.TempDir()
	archive := filepath.Join(root, "natives.jar")
	makeNativesArchive(t, archive)
	target := filepath.Join(root, "natives")
	library := map[string]any{
		"extract": map[string]any{"exclude": []any{"META-INF/"}},
	}

	if err := extractNatives(archive, library, target, "sum-1"); err != nil {
		t.Fatal(err)
	}
	for _, name := range []string{"glfw.dll", "sub/openal.dll"} {
		if _, err := os.Stat(filepath.Join(target, filepath.FromSlash(name))); err != nil {
			t.Fatalf("файл %s не распакован: %v", name, err)
		}
	}
	if _, err := os.Stat(filepath.Join(target, "META-INF", "MANIFEST.MF")); err == nil {
		t.Fatal("META-INF не должен распаковываться")
	}

	// Удаляем распакованный файл и повторяем с той же суммой: распаковка
	// пропускается, файл не восстанавливается — значит, работы действительно нет.
	if err := os.Remove(filepath.Join(target, "glfw.dll")); err != nil {
		t.Fatal(err)
	}
	if err := extractNatives(archive, library, target, "sum-1"); err != nil {
		t.Fatal(err)
	}
	if _, err := os.Stat(filepath.Join(target, "glfw.dll")); err == nil {
		t.Fatal("архив распаковывался повторно, хотя он тот же самый")
	}

	// Другая контрольная сумма (обновилась версия библиотеки) — распаковываем.
	if err := extractNatives(archive, library, target, "sum-2"); err != nil {
		t.Fatal(err)
	}
	if _, err := os.Stat(filepath.Join(target, "glfw.dll")); err != nil {
		t.Fatal("новую версию архива не распаковали")
	}
}

// TestReadCachedJSON — сохранённый документ читается, устаревший и испорченный — нет.
func TestReadCachedJSON(t *testing.T) {
	root := t.TempDir()
	path := filepath.Join(root, "1.21.11.json")
	if err := os.WriteFile(path, []byte(`{"id":"1.21.11"}`), 0o644); err != nil {
		t.Fatal(err)
	}
	if _, err := readCachedJSON(path, time.Hour); err != nil {
		t.Fatalf("свежий документ не прочитан: %v", err)
	}
	if _, err := readCachedJSON(path, 0); err != nil {
		t.Fatalf("документ без срока давности не прочитан: %v", err)
	}
	past := time.Now().Add(-48 * time.Hour)
	if err := os.Chtimes(path, past, past); err != nil {
		t.Fatal(err)
	}
	if _, err := readCachedJSON(path, time.Hour); err == nil {
		t.Fatal("устаревший документ прочитан как свежий")
	}
	if err := os.WriteFile(path, []byte("не json"), 0o644); err != nil {
		t.Fatal(err)
	}
	if _, err := readCachedJSON(path, 0); err == nil {
		t.Fatal("испорченный документ прочитан без ошибки")
	}
}

// TestProgressName — в строке прогресса показывается человеческое имя, если оно есть.
func TestProgressName(t *testing.T) {
	cases := []struct {
		job      DownloadJob
		expected string
	}{
		{DownloadJob{Name: "Sodium", Target: "sodium.jar"}, "Sodium"},
		{DownloadJob{Target: filepath.Join("libs", "a.jar")}, "a.jar"},
		{DownloadJob{URL: "https://example.com/x"}, "https://example.com/x"},
	}
	for _, item := range cases {
		if got := progressName(item.job); got != item.expected {
			t.Fatalf("progressName: получили %q, ждали %q", got, item.expected)
		}
	}
}

// TestJavaCandidatesNoGarbage — в списке кандидатов нет пустых путей и дублей.
func TestJavaCandidatesNoGarbage(t *testing.T) {
	candidates := JavaCandidates()
	if len(candidates) == 0 {
		t.Skip("в этой системе нет ни одного обычного каталога с Java")
	}
	seen := map[string]bool{}
	for _, candidate := range candidates {
		if strings.TrimSpace(candidate) == "" {
			t.Fatal("пустой путь в списке кандидатов")
		}
		if seen[candidate] {
			t.Fatalf("кандидат продублирован: %s", candidate)
		}
		seen[candidate] = true
	}
	sort.Strings(candidates)
}

// TestModTitles — в манифесте у каждого мода есть человеческое название:
// на Modrinth версии часто называются «1.11.3-1.21.11 - Fabric», и показывать
// это в списке модов нельзя.
func TestModTitles(t *testing.T) {
	manifest, err := EmbeddedModManifest()
	if err != nil {
		t.Fatal(err)
	}
	for _, mod := range manifest.Required() {
		name := mod.DisplayName()
		if strings.TrimSpace(name) == "" {
			t.Fatalf("у мода %s нет названия", mod.ID)
		}
		if strings.HasPrefix(name, "v") && strings.ContainsAny(name[1:2], "0123456789") {
			t.Fatalf("название мода %s похоже на номер версии: %s", mod.ID, name)
		}
		if strings.TrimSpace(mod.Version) == "" {
			t.Fatalf("у мода %s нет версии", mod.ID)
		}
	}
	if manifest.FabricAPI != nil && manifest.FabricAPI.DisplayName() != "Fabric API" {
		t.Fatalf("название Fabric API: %s", manifest.FabricAPI.DisplayName())
	}
}
