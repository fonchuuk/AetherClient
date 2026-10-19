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
	ProjectType string `json:"project_type"`
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
func SearchModrinth(query string, gameVersion string, loader string,
	limit int, offset int, cancel *Cancel) (*ModrinthSearch, error) {
	return SearchModrinthProject("mod", query, gameVersion, loader, limit, offset, cancel)
}

// SearchModrinthProject ищет моды ("mod") или ресурспаки ("resourcepack") на Modrinth.
func SearchModrinthProject(projectType string, query string, gameVersion string, loader string,
	limit int, offset int, cancel *Cancel) (*ModrinthSearch, error) {
	if projectType != "resourcepack" {
		projectType = "mod"
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
	if offset < 0 {
		offset = 0
	}
	var facets [][]string
	if projectType == "resourcepack" {
		facets = [][]string{
			{"project_type:resourcepack"},
			{"versions:" + gameVersion},
		}
	} else {
		facets = [][]string{
			{"project_type:mod"},
			{"categories:" + loader},
			{"versions:" + gameVersion},
		}
	}
	encoded, err := json.Marshal(facets)
	if err != nil {
		return nil, err
	}
	target := modrinthAPI + "/search?query=" + url.QueryEscape(query) +
		"&limit=" + strconv.Itoa(limit) +
		"&offset=" + strconv.Itoa(offset) +
		"&index=downloads" +
		"&facets=" + url.QueryEscape(string(encoded))
	var payload modrinthSearchPayload
	if err := getJSON(target, cancel, &payload); err != nil {
		return nil, err
	}
	return &ModrinthSearch{Hits: payload.Hits, TotalHits: payload.TotalHits}, nil
}

// FetchModrinthVersions — все версии мода, подходящие под версию игры и загрузчик.
func FetchModrinthVersions(projectID string, gameVersion string, loader string,
	cancel *Cancel) ([]ModrinthVersion, error) {
	return FetchModrinthProjectVersions("mod", projectID, gameVersion, loader, cancel)
}

// FetchModrinthProjectVersions — версии мода или ресурспака с Modrinth.
func FetchModrinthProjectVersions(projectType string, projectID string, gameVersion string, loader string,
	cancel *Cancel) ([]ModrinthVersion, error) {
	if strings.TrimSpace(projectID) == "" {
		return nil, &Errorf{Message: "не выбран проект"}
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
	var target string
	if projectType == "resourcepack" {
		target = modrinthAPI + "/project/" + url.PathEscape(projectID) +
			"/version?game_versions=" + url.QueryEscape(string(filters))
	} else {
		loaders, err := json.Marshal([]string{loader})
		if err != nil {
			return nil, err
		}
		target = modrinthAPI + "/project/" + url.PathEscape(projectID) +
			"/version?game_versions=" + url.QueryEscape(string(filters)) +
			"&loaders=" + url.QueryEscape(string(loaders))
	}
	var versions []ModrinthVersion
	if err := getJSON(target, cancel, &versions); err != nil {
		return nil, err
	}
	// Если у ресурспака не проставлен точный тег патча (например 1.21.x), берём свежую версию проекта
	if len(versions) == 0 && projectType == "resourcepack" {
		fallbackTarget := modrinthAPI + "/project/" + url.PathEscape(projectID) + "/version"
		_ = getJSON(fallbackTarget, cancel, &versions)
	}
	return versions, nil
}

// LatestModrinthVersion — самая свежая подходящая версия мода.
func LatestModrinthVersion(projectID string, gameVersion string, loader string,
	cancel *Cancel) (*ModrinthVersion, error) {
	return LatestModrinthProjectVersion("mod", projectID, gameVersion, loader, cancel)
}

// LatestModrinthProjectVersion — самая свежая подходящая версия мода или ресурспака.
func LatestModrinthProjectVersion(projectType string, projectID string, gameVersion string, loader string,
	cancel *Cancel) (*ModrinthVersion, error) {
	versions, err := FetchModrinthProjectVersions(projectType, projectID, gameVersion, loader, cancel)
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
		return nil, &Errorf{Message: "нет подходящей версии под Minecraft " + gameVersion}
	}
	return best, nil
}

// ModrinthInstaller ставит выбранный мод в папку модов игры.
type ModrinthInstaller struct {
	Paths    Paths
	Cancel   *Cancel
	Progress ProgressListener
}

// Install скачивает файл версии мода в каталог модов и проверяет его по SHA-512.
func (m *ModrinthInstaller) Install(version *ModrinthVersion) (string, error) {
	return m.InstallProject("mod", version)
}

// InstallProject скачивает мод в папку mods или ресурспак в папку resourcepacks и проверяет по SHA-512.
func (m *ModrinthInstaller) InstallProject(projectType string, version *ModrinthVersion) (string, error) {
	if version == nil {
		return "", &Errorf{Message: "версия не выбрана"}
	}
	file, ok := version.PrimaryFile()
	if !ok {
		return "", &Errorf{Message: "у этой версии нет файла для скачивания"}
	}
	if file.URL == "" || file.Name == "" {
		return "", &Errorf{Message: "Modrinth не отдал ссылку на файл"}
	}
	isResourcePack := projectType == "resourcepack"
	name := sanitizeProjectFileName(projectType, file.Name)
	if name == "" {
		return "", &Errorf{Message: "не удалось понять имя файла: " + file.Name}
	}
	destDir := m.Paths.Mods()
	stagePrefix := "Мод: "
	if isResourcePack {
		destDir = m.Paths.ResourcePacks()
		stagePrefix = "Ресурспак: "
	}
	if err := os.MkdirAll(destDir, 0o755); err != nil {
		return "", err
	}
	target := filepath.Join(destDir, name)
	if m.Progress != nil {
		m.Progress.Stage(stagePrefix + name)
	}
	if err := downloadFile(DownloadJob{URL: file.URL, Target: target, Size: file.Size,
		SHA1: file.SHA1(), SHA512: file.SHA512(), Name: name},
		m.Progress, m.Cancel); err != nil {
		return "", err
	}
	return target, nil
}

// InstalledModFiles — jar-файлы, которые уже лежат в папке модов.
func InstalledModFiles(paths Paths) ([]string, error) {
	return listFilesWithExts(paths.Mods(), ".jar")
}

// InstalledResourcePackFiles — zip/jar-файлы ресурспаков в папке resourcepacks.
func InstalledResourcePackFiles(paths Paths) ([]string, error) {
	return listFilesWithExts(paths.ResourcePacks(), ".zip", ".jar")
}

func listFilesWithExts(dir string, exts ...string) ([]string, error) {
	entries, err := os.ReadDir(dir)
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
		ext := strings.ToLower(filepath.Ext(entry.Name()))
		for _, allowed := range exts {
			if ext == allowed {
				names = append(names, entry.Name())
				break
			}
		}
	}
	sort.Strings(names)
	return names, nil
}

func sanitizeModFileName(name string) string {
	return sanitizeProjectFileName("mod", name)
}

func sanitizeProjectFileName(projectType string, name string) string {
	name = filepath.Base(strings.ReplaceAll(name, "\\", "/"))
	if name == "." || name == "/" || name == "" {
		return ""
	}
	ext := strings.ToLower(filepath.Ext(name))
	if projectType == "resourcepack" {
		if ext != ".zip" && ext != ".jar" {
			return ""
		}
		return name
	}
	if ext != ".jar" {
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
