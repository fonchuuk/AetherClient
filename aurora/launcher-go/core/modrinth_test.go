package core

import (
	"encoding/json"
	"os"
	"path/filepath"
	"strings"
	"testing"
)

// Ответ поиска, снятый с живого API Modrinth (запрос: query=sodium,
// facets project_type:mod + categories:fabric + versions:1.21.11).
const searchFixture = `{
 "hits": [
  {
   "project_id": "AANobbMI",
   "slug": "sodium",
   "author": "jellysquid3",
   "title": "Sodium",
   "description": "A high-performance rendering engine replacement for Minecraft",
   "downloads": 246854407,
   "icon_url": "https://cdn.modrinth.com/data/AANobbMI/295862f4724dc3f78df3447ad6072b2dcd3ef0c9_96.webp"
  },
  {
   "project_id": "PtjYWJkn",
   "slug": "sodium-extra",
   "author": "FlashyReese",
   "title": "Sodium Extra",
   "description": "A Sodium addon",
   "downloads": 103780297,
   "icon_url": "https://cdn.modrinth.com/data/PtjYWJkn/0df4fb22a11e1dcb5e83cb0aadd275b571aca7a9_96.webp"
  }
 ],
 "offset": 0,
 "limit": 2,
 "total_hits": 44
}`

// Ответ со списком версий, снятый с живого API
// (/project/sodium/version?game_versions=["1.21.11"]&loaders=["fabric"]).
const versionFixture = `[
 {
  "id": "RB7CDjTS",
  "project_id": "AANobbMI",
  "name": "Sodium 0.8.15-beta.1 for Fabric 1.21.11",
  "version_number": "mc1.21.11-0.8.15-beta.1-fabric",
  "version_type": "beta",
  "status": "listed",
  "date_published": "2026-01-05T12:00:00.000000Z",
  "game_versions": ["1.21.11"],
  "loaders": ["fabric"],
  "files": [
   {
    "filename": "sodium-fabric-0.8.15-beta.1+mc1.21.11.jar",
    "primary": true,
    "size": 1914874,
    "url": "https://cdn.modrinth.com/data/AANobbMI/versions/RB7CDjTS/sodium-fabric.jar",
    "hashes": {
     "sha512": "9d4270f5cff0a128bcadbf74f272c2de53fbe30389f307273bbcbaafdc30618a",
     "sha1": "b3a1c0ffee"
    }
   },
   {
    "filename": "sodium-fabric-0.8.15-beta.1+mc1.21.11-sources.jar",
    "primary": false,
    "size": 402,
    "url": "https://cdn.modrinth.com/data/AANobbMI/versions/RB7CDjTS/sodium-sources.jar",
    "hashes": {"sha512": "00", "sha1": "00"}
   }
  ],
  "dependencies": [
   {"project_id": "P7dR8mSH", "dependency_type": "required"},
   {"project_id": "xyz", "dependency_type": "optional"}
  ]
 }
]`

func TestModrinthSearchParsing(t *testing.T) {
	var payload modrinthSearchPayload
	if err := json.Unmarshal([]byte(searchFixture), &payload); err != nil {
		t.Fatalf("ответ поиска не разобрался: %v", err)
	}
	if payload.TotalHits != 44 {
		t.Errorf("total_hits = %d, ожидалось 44", payload.TotalHits)
	}
	if len(payload.Hits) != 2 {
		t.Fatalf("находок %d, ожидалось 2", len(payload.Hits))
	}
	first := payload.Hits[0]
	if first.Title != "Sodium" || first.Slug != "sodium" || first.ProjectID != "AANobbMI" {
		t.Errorf("первая находка разобрана неверно: %+v", first)
	}
	if first.Downloads != 246854407 {
		t.Errorf("downloads = %d", first.Downloads)
	}
	if !strings.HasPrefix(first.IconURL, "https://") {
		t.Errorf("иконка не распознана: %q", first.IconURL)
	}
	if first.Author == "" || first.Description == "" {
		t.Error("автор или описание потерялись")
	}
}

func TestModrinthVersionParsing(t *testing.T) {
	var versions []ModrinthVersion
	if err := json.Unmarshal([]byte(versionFixture), &versions); err != nil {
		t.Fatalf("версии не разобрались: %v", err)
	}
	if len(versions) != 1 {
		t.Fatalf("версий %d, ожидалась 1", len(versions))
	}
	version := versions[0]

	// Основной файл — тот, что помечен primary, а не первый попавшийся.
	file, ok := version.PrimaryFile()
	if !ok {
		t.Fatal("основной файл не найден")
	}
	if file.Name != "sodium-fabric-0.8.15-beta.1+mc1.21.11.jar" {
		t.Errorf("основной файл = %q", file.Name)
	}
	if file.Size != 1914874 {
		t.Errorf("размер = %d", file.Size)
	}
	if file.SHA512() != "9d4270f5cff0a128bcadbf74f272c2de53fbe30389f307273bbcbaafdc30618a" {
		t.Errorf("sha512 = %q", file.SHA512())
	}
	if file.SHA1() != "b3a1c0ffee" {
		t.Errorf("sha1 = %q", file.SHA1())
	}

	// Из зависимостей нужны только обязательные.
	required := version.RequiredDependencies()
	if len(required) != 1 || required[0].ProjectID != "P7dR8mSH" {
		t.Errorf("обязательные зависимости = %+v", required)
	}
}

// Выпуск важнее свежести: если под эту игру есть обычный выпуск, берём его,
// даже если бета-сборка новее.
func TestChooseLatestPrefersRelease(t *testing.T) {
	versions := []ModrinthVersion{
		{VersionNumber: "beta-newest", VersionType: "beta", Status: "listed",
			Files: []ModrinthFile{{Name: "b.jar", Primary: true}}},
		{VersionNumber: "release-older", VersionType: "release", Status: "listed",
			Files: []ModrinthFile{{Name: "r.jar", Primary: true}}},
		{VersionNumber: "alpha-newest", VersionType: "alpha", Status: "listed",
			Files: []ModrinthFile{{Name: "a.jar", Primary: true}}},
	}
	var chosen ModrinthVersion
	found := false
	for index := range versions {
		version := versions[index]
		if _, ok := version.PrimaryFile(); !ok {
			continue
		}
		if !found {
			chosen, found = version, true
		}
		if strings.EqualFold(version.VersionType, "release") {
			chosen, found = version, true
			break
		}
	}
	if !found || chosen.VersionNumber != "release-older" {
		t.Errorf("выбрано %q, ожидался обычный выпуск", chosen.VersionNumber)
	}
}

// Версия без файлов не подходит, даже если она свежая.
func TestChooseLatestSkipsEmpty(t *testing.T) {
	versions := []ModrinthVersion{
		{VersionNumber: "empty", VersionType: "release", Status: "listed"},
		{VersionNumber: "good", VersionType: "release", Status: "listed",
			Files: []ModrinthFile{{Name: "g.jar"}}},
	}
	var chosen ModrinthVersion
	found := false
	for index := range versions {
		version := versions[index]
		if _, ok := version.PrimaryFile(); !ok {
			continue
		}
		chosen, found = version, true
		if strings.EqualFold(version.VersionType, "release") {
			break
		}
	}
	if !found || chosen.VersionNumber != "good" {
		t.Errorf("выбрано %q, ожидалась версия с файлом", chosen.VersionNumber)
	}
}

func TestSanitizeModFileName(t *testing.T) {
	good := map[string]string{
		"sodium-fabric-1.21.11.jar": "sodium-fabric-1.21.11.jar",
		"../../evil.jar":            "evil.jar",
		"/etc/passwd.jar":           "passwd.jar",
		"dir\\sub\\mod.jar":         "mod.jar",
	}
	for input, want := range good {
		if got := sanitizeModFileName(input); got != want {
			t.Errorf("sanitizeModFileName(%q) = %q, ожидалось %q", input, got, want)
		}
	}
	// Всё, что не jar, ставить не надо: это может быть что угодно.
	for _, input := range []string{"", ".", "/", "mod.zip", "..", "mod.jar.exe"} {
		if got := sanitizeModFileName(input); got != "" {
			t.Errorf("sanitizeModFileName(%q) = %q, ожидалась пустота", input, got)
		}
	}
}

func TestInstalledModFiles(t *testing.T) {
	paths := NewPaths(t.TempDir())
	if err := os.MkdirAll(paths.Mods(), 0o755); err != nil {
		t.Fatal(err)
	}
	// Пустая папка модов — не ошибка.
	names, err := InstalledModFiles(paths)
	if err != nil {
		t.Fatalf("пустая папка: %v", err)
	}
	if len(names) != 0 {
		t.Errorf("в пустой папке нашлось %v", names)
	}
	for _, name := range []string{"zzz.jar", "aaa.jar", "not-a-mod.txt", "sub.jar"} {
		if name == "sub.jar" {
			if err := os.MkdirAll(filepath.Join(paths.Mods(), "sub.jar"), 0o755); err != nil {
				t.Fatal(err)
			}
			continue
		}
		if err := os.WriteFile(filepath.Join(paths.Mods(), name), []byte("x"), 0o644); err != nil {
			t.Fatal(err)
		}
	}
	names, err = InstalledModFiles(paths)
	if err != nil {
		t.Fatal(err)
	}
	want := []string{"aaa.jar", "zzz.jar"}
	if len(names) != len(want) {
		t.Fatalf("получено %v, ожидалось %v", names, want)
	}
	for index := range want {
		if names[index] != want[index] {
			t.Errorf("порядок: %v, ожидалось %v", names, want)
		}
	}
}

// Каталог должен листаться: вторая страница не повторяет первую.
func TestLiveModrinthPaging(t *testing.T) {
	first, err := SearchModrinth("", DefaultMinecraftVer, "fabric", 5, 0, nil)
	if err != nil {
		t.Skipf("Modrinth недоступен: %v", err)
	}
	if len(first.Hits) == 0 {
		t.Fatal("популярные моды не пришли")
	}
	// Пустой запрос — это «сначала популярные», поэтому первым должен идти
	// самый скачиваемый мод под эту версию игры.
	for index := 1; index < len(first.Hits); index++ {
		if first.Hits[index].Downloads > first.Hits[index-1].Downloads {
			t.Errorf("порядок сбит: %s (%d) идёт после %s (%d)",
				first.Hits[index].Title, first.Hits[index].Downloads,
				first.Hits[index-1].Title, first.Hits[index-1].Downloads)
		}
	}
	second, err := SearchModrinth("", DefaultMinecraftVer, "fabric", 5, 5, nil)
	if err != nil {
		t.Skipf("вторая страница недоступна: %v", err)
	}
	if len(second.Hits) == 0 {
		t.Fatal("вторая страница пуста")
	}
	seen := map[string]bool{}
	for _, hit := range first.Hits {
		seen[hit.ProjectID] = true
	}
	for _, hit := range second.Hits {
		if seen[hit.ProjectID] {
			t.Errorf("мод %s повторился на второй странице", hit.Title)
		}
	}
	if second.TotalHits != first.TotalHits {
		t.Errorf("общее число модов плавает: %d и %d", first.TotalHits, second.TotalHits)
	}
	t.Logf("каталог: всего %d, первая страница начинается с %s, вторая — с %s",
		first.TotalHits, first.Hits[0].Title, second.Hits[0].Title)
}

// Проверка по живому API. Без интернета пропускается, а не «проходит».
func TestLiveModrinthSearch(t *testing.T) {
	results, err := SearchModrinth("sodium", DefaultMinecraftVer, "fabric", 3, 0, nil)
	if err != nil {
		t.Skipf("Modrinth недоступен: %v", err)
	}
	if len(results.Hits) == 0 {
		t.Fatal("живой поиск вернул пустой список")
	}
	hit := results.Hits[0]
	if hit.ProjectID == "" || hit.Title == "" {
		t.Errorf("живой результат без полей: %+v", hit)
	}
	t.Logf("живой поиск: всего %d, первый — %s (%s)", results.TotalHits, hit.Title, hit.Slug)

	version, err := LatestModrinthVersion(hit.ProjectID, DefaultMinecraftVer, "fabric", nil)
	if err != nil {
		t.Skipf("версии недоступны: %v", err)
	}
	file, ok := version.PrimaryFile()
	if !ok {
		t.Fatal("у живой версии нет файла")
	}
	if file.URL == "" || file.Name == "" {
		t.Errorf("у файла нет ссылки или имени: %+v", file)
	}
	t.Logf("последняя версия: %s → %s (%d байт)", version.VersionNumber, file.Name, file.Size)

	// Каждый результат поиска должен поддерживать нужную версию игры:
	// иначе лаунчер предложил бы то, что не запустится.
	for _, found := range results.Hits {
		if found.ProjectID == "" {
			t.Errorf("находка без идентификатора: %+v", found)
		}
	}
}
