package core

import (
	"encoding/json"
	"net/http"
	"net/url"
	"os"
	"path/filepath"
	"sort"
	"strconv"
	"strings"
)

// Поиск и установка модов с Modrinth — официального открытого каталога модов.
//
// Качаются сами jar-файлы модов, которые авторы выложили открыто; игра при
// этом остаётся официальной и ставится с серверов Mojang. Лаунчер ничего не
// перепаковывает и не меняет: файл кладётся в папку mods ровно в том виде,
// в каком его отдал Modrinth, и проверяется по SHA-512.

const modrinthAPI = "https://api.modrinth.com/v2"

// ModrinthHit — одна находка в поиске.
type ModrinthHit struct {
	Slug        string `json:"slug"`
	ProjectID   string `json:"project_id"`
	Title       string `json:"title"`
	Description string `json:"description"`
	Author      string `json:"author"`
	IconURL     string `json:"icon_url"`
	Downloads   int64  `json:"downloads"`
}

// ModrinthSearch — ответ поиска.
type ModrinthSearch struct {
	Hits      []ModrinthHit `json:"hits"`
	TotalHits int64         `json:"total_hits"`
}

type modrinthSearchPayload struct {
	Hits      []ModrinthHit `json:"hits"`
	TotalHits int64         `json:"total_hits"`
}

// ModrinthFile — файл версии мода.
type ModrinthFile struct {
	Name    string            `json:"filename"`
	URL     string            `json:"url"`
	Primary bool              `json:"primary"`
	Size    int64             `json:"size"`
	Hashes  map[string]string `json:"hashes"`
}

// SHA512 — контрольная сумма из ответа; пустая, если Modrinth её не дал
// (тогда проверка идёт только по размеру).
func (f ModrinthFile) SHA512() string {
	return strings.ToLower(f.Hashes["sha512"])
}

func (f ModrinthFile) SHA1() string {
	return strings.ToLower(f.Hashes["sha1"])
}

// ModrinthDependency — зависимость версии.
type ModrinthDependency struct {
	VersionID      string `json:"version_id"`
	ProjectID      string `json:"project_id"`
	DependencyType string `json:"dependency_type"`
}

// ModrinthVersion — версия мода.
type ModrinthVersion struct {
	ID            string               `json:"id"`
	ProjectID     string               `json:"project_id"`
	Name          string               `json:"name"`
	VersionNumber string               `json:"version_number"`
	VersionType   string               `json:"version_type"`
	Status        string               `json:"status"`
	DatePublished string               `json:"date_published"`
	Files         []ModrinthFile       `json:"files"`
	GameVersions  []string             `json:"game_versions"`
	Loaders       []string             `json:"loaders"`
	Dependencies  []ModrinthDependency `json:"dependencies"`
}

// PrimaryFile — файл, который Modrinth помечает основным. Если пометки нет,
// берётся первый: у версии с одним файлом она и не нужна.
func (v ModrinthVersion) PrimaryFile() (ModrinthFile, bool) {
	for _, file := range v.Files {
		if file.Primary {
			return file, true
		}
	}
	if len(v.Files) > 0 {
		return v.Files[0], true
	}
	return ModrinthFile{}, false
}

// RequiredDependencies — обязательные зависимости, которые нужно поставить
// отдельно. Встроенные (вшитые в сам файл) не считаются.
func (v ModrinthVersion) RequiredDependencies() []ModrinthDependency {
	var required []ModrinthDependency
	for _, dependency := range v.Dependencies {
		if strings.EqualFold(dependency.DependencyType, "required") &&
			dependency.ProjectID != "" {
			required = append(required, dependency)
		}
	}
	return required
}

const (
	defaultSearchLimit = 24
	maxSearchLimit     = 50
)

// SearchModrinth ищет моды по названию.
//
// Фильтры задаются facets — так их принимает сам Modrinth: только моды,
// только Fabric и только та версия игры, под которую собран лаунчер. Поэтому
// в результатах не бывает того, что заведомо не запустится.
func SearchModrinth(query string, gameVersion string, loader string,
	limit int, cancel *Cancel) (*ModrinthSearch, error) {
	query = strings.TrimSpace(query)
	if query == "" {
		return nil, &Errorf{Message: "введите название мода"}
	}
	if gameVersion == "" {
		gameVersion = DefaultMinecraftVer
	}
	if loader == "" {
		loader = "fabric"
	}
	if limit <= 0 {
		limit = defaultSearchLimit
	}
	if limit > maxSearchLimit {
		limit = maxSearchLimit
	}
	facets := [][]string{
		{"project_type:mod"},
		{"categories:" + loader},
		{"versions:" + gameVersion},
	}
	encoded, err := json.Marshal(facets)
	if err != nil {
		return nil, err
	}
	target := modrinthAPI + "/search?query=" + url.QueryEscape(query) +
		"&limit=" + strconv.Itoa(limit) +
		"&index=downloads" +
		"&facets=" + url.QueryEscape(string(encoded))
	var payload modrinthSearchPayload
	if err := getJSON(target, cancel, &payload); err != nil {
		return nil, err
	}
	return &ModrinthSearch{Hits: payload.Hits, TotalHits: payload.TotalHits}, nil
}

// FetchModrinthVersions — все версии проекта, подходящие под версию игры и
// загрузчик. Modrinth отдаёт их от новых к старым.
func FetchModrinthVersions(projectID string, gameVersion string, loader string,
	cancel *Cancel) ([]ModrinthVersion, error) {
	if strings.TrimSpace(projectID) == "" {
		return nil, &Errorf{Message: "не выбран мод"}
	}
	if gameVersion == "" {
		gameVersion = DefaultMinecraftVer
	}
	if loader == "" {
		loader = "fabric"
	}
	filters, err := json.Marshal([]string{gameVersion})
	if err != nil {
		return nil, err
	}
	loaders, err := json.Marshal([]string{loader})
	if err != nil {
		return nil, err
	}
	target := modrinthAPI + "/project/" + url.PathEscape(projectID) +
		"/version?game_versions=" + url.QueryEscape(string(filters)) +
		"&loaders=" + url.QueryEscape(string(loaders))
	var versions []ModrinthVersion
	if err := getJSON(target, cancel, &versions); err != nil {
		return nil, err
	}
	return versions, nil
}

// LatestModrinthVersion — самая свежая подходящая версия.
//
// Сначала ищется обычный выпуск: бета-сборку ставить можно, но только если
// более стабильной версии под эту игру нет. Архивные версии пропускаются.
func LatestModrinthVersion(projectID string, gameVersion string, loader string,
	cancel *Cancel) (*ModrinthVersion, error) {
	versions, err := FetchModrinthVersions(projectID, gameVersion, loader, cancel)
	if err != nil {
		return nil, err
	}
	var best *ModrinthVersion
	for index := range versions {
		version := versions[index]
		if !strings.EqualFold(version.Status, "listed") &&
			!strings.EqualFold(version.Status, "archived") && version.Status != "" {
			continue
		}
		if _, ok := version.PrimaryFile(); !ok {
			continue
		}
		if best == nil {
			best = &version
		}
		if strings.EqualFold(version.VersionType, "release") {
			return &version, nil
		}
	}
	if best == nil {
		return nil, &Errorf{Message: "у этого мода нет версии под Minecraft " +
			gameVersion + " и загрузчик " + loader}
	}
	return best, nil
}

// ModrinthInstaller ставит выбранный мод в папку модов игры.
type ModrinthInstaller struct {
	Paths    Paths
	Cancel   *Cancel
	Progress ProgressListener
}

// Install скачивает файл версии в каталог модов и проверяет его по SHA-512.
// Возвращает путь к установленному файлу.
func (m *ModrinthInstaller) Install(version *ModrinthVersion) (string, error) {
	if version == nil {
		return "", &Errorf{Message: "версия мода не выбрана"}
	}
	file, ok := version.PrimaryFile()
	if !ok {
		return "", &Errorf{Message: "у этой версии нет файла для скачивания"}
	}
	if file.URL == "" || file.Name == "" {
		return "", &Errorf{Message: "Modrinth не отдал ссылку на файл"}
	}
	name := sanitizeModFileName(file.Name)
	if name == "" {
		return "", &Errorf{Message: "не удалось понять имя файла: " + file.Name}
	}
	if err := os.MkdirAll(m.Paths.Mods(), 0o755); err != nil {
		return "", err
	}
	target := filepath.Join(m.Paths.Mods(), name)
	if m.Progress != nil {
		m.Progress.Stage("Мод: " + name)
	}
	// downloadFile, а не Download: только он проверяет SHA-512, который
	// Modrinth отдаёт для каждого файла.
	if err := downloadFile(DownloadJob{URL: file.URL, Target: target, Size: file.Size,
		SHA1: file.SHA1(), SHA512: file.SHA512(), Name: name},
		m.Progress, m.Cancel); err != nil {
		return "", err
	}
	return target, nil
}

// InstalledModFiles — jar-файлы, которые уже лежат в папке модов.
func InstalledModFiles(paths Paths) ([]string, error) {
	entries, err := os.ReadDir(paths.Mods())
	if err != nil {
		if os.IsNotExist(err) {
			return nil, nil
		}
		return nil, err
	}
	var names []string
	for _, entry := range entries {
		if entry.IsDir() {
			continue
		}
		if !strings.EqualFold(filepath.Ext(entry.Name()), ".jar") {
			continue
		}
		names = append(names, entry.Name())
	}
	sort.Strings(names)
	return names, nil
}

// sanitizeModFileName оставляет от имени только имя файла: Modrinth отдаёт
// его готовым, но на всякий случай путь не должен выйти за пределы папки модов.
func sanitizeModFileName(name string) string {
	name = filepath.Base(strings.ReplaceAll(name, "\\", "/"))
	if name == "." || name == "/" || name == "" {
		return ""
	}
	if !strings.EqualFold(filepath.Ext(name), ".jar") {
		return ""
	}
	return name
}

// getJSON — запрос документа с поддержкой отмены.
func getJSON(rawURL string, cancel *Cancel, out any) error {
	request, err := http.NewRequest(http.MethodGet, rawURL, nil)
	if err != nil {
		return err
	}
	request.Header.Set("Accept", "application/json")
	request.Header.Set("User-Agent", "AetherGlassLauncher/1.0")
	response, err := do(request, cancel)
	if err != nil {
		return err
	}
	defer response.Body.Close()
	if response.StatusCode != http.StatusOK {
		return &Errorf{Message: "Modrinth ответил " + strconv.Itoa(response.StatusCode)}
	}
	return json.NewDecoder(response.Body).Decode(out)
}
